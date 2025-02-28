package cn.i7mc.playerinfo.controller;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import cn.i7mc.playerinfo.PlayerInfo;
import cn.i7mc.playerinfo.model.PlayerData;
import cn.i7mc.playerinfo.util.ItemStackSerializer;
import cn.i7mc.playerinfo.messaging.MessageSender;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.logging.Logger;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

public class PlayerController implements HttpHandler, Listener {
    private final Gson gson;
    private final PlayerInfo playerInfo;
    private final Logger logger;

    // 玩家数据缓存
    private Map<UUID, Map<String, Object>> playerDataMap;

    public PlayerController(PlayerInfo playerInfo) {
        this.playerInfo = playerInfo;
        this.logger = playerInfo.getPlugin().getLogger();
        this.gson = new Gson();
        this.playerDataMap = new HashMap<>();
        
        // 初始化时刷新数据
        refreshData();

        // 注册事件监听器
        playerInfo.getServer().getPluginManager().registerEvents(this, playerInfo.getPlugin());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 获取离开的玩家
        Player player = event.getPlayer();
        
        // 如果启用了BungeeCord模式，发送玩家移除消息
        if (playerInfo.getConfig().getBoolean("bungeecord.enabled", true)) {
            // 通过PlayerInfo获取MessageSender
            MessageSender messageSender = playerInfo.getMessageSender();
            
            if (messageSender != null) {
                // 发送移除消息到BungeeCord
                messageSender.sendPlayerRemove(player);
                
                if (playerInfo.isDebug()) {
                    logger.info("已发送玩家离开消息: " + player.getName());
                }
            }
        }
        
        // 从本地数据缓存中清除玩家数据
        playerDataMap.remove(player.getUniqueId());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String response;

        if ("/api/players".equals(path)) {
            response = handlePlayerList();
        } else if (path.startsWith("/api/player/")) {
            String playerName = path.substring("/api/player/".length());
            response = handlePlayerDetails(playerName);
        } else {
            sendResponse(exchange, 404, "Not found");
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        sendResponse(exchange, 200, response);
    }

    private String handlePlayerList() {
        List<String> playerNames = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerNames.add(player.getName());
        }
        String response = gson.toJson(playerNames);
        return response;
    }

    private String handlePlayerDetails(String playerName) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null || !player.isOnline()) {
            playerInfo.getLogger().warning(ChatColor.AQUA + "未找到玩家或玩家不在线: " + playerName);
            return "{}";
        }

        // Get main hand and off hand items
        ItemStack mainHand = player.getInventory().getItemInHand(); // Use getItemInHand() for main hand in 1.12.2
        
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack offHand = contents.length > 40 ? contents[40] : null; // Safely get off hand item
        
        if (mainHand != null && !mainHand.getType().name().equals("AIR")) {
            mainHand = mainHand.clone();
        } else {
            mainHand = null;
        }
        
        if (offHand != null && !offHand.getType().name().equals("AIR")) {
            offHand = offHand.clone();
        } else {
            offHand = null;
        }

        // 获取玩家UUID
        UUID playerUUID = player.getUniqueId();
        
        // 获取玩家等级
        int level = player.getLevel();
        
        // 获取玩家生命值和最大生命值
        double health = player.getHealth();
        double maxHealth = player.getMaxHealth();
        
        // 获取玩家位置信息
        Location location = player.getLocation();
        
        // 构建Mojang API的皮肤URL
        String skinURL = "https://sessionserver.mojang.com/session/minecraft/profile/" + 
                        playerUUID.toString().replace("-", "") + "?unsigned=false";

        PlayerData playerData = new PlayerData(
            player.getName(),
            playerUUID,
            skinURL,
            player.getInventory().getArmorContents().clone(),
            player.getInventory().getContents().clone(),
            mainHand,
            offHand,
            level,
            health,
            maxHealth,
            location
        );

        String response = gson.toJson(ItemStackSerializer.serialize(playerData));
        return response;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    /**
     * 刷新玩家数据
     * 收集所有在线玩家的信息
     */
    public void refreshData() {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        
        // 清除旧数据
        playerDataMap.clear();
        
        // 收集每个玩家的数据
        for (Player player : onlinePlayers) {
            collectPlayerData(player);
        }
        
        if (playerInfo.isDebug()) {
            logger.info("已刷新 " + playerDataMap.size() + " 名玩家的数据");
        }
    }
    
    /**
     * 收集单个玩家的数据
     * 
     * @param player Bukkit玩家对象
     */
    private void collectPlayerData(Player player) {
        Map<String, Object> playerData = new LinkedHashMap<>();
        
        // 基本信息
        playerData.put("name", player.getName());
        playerData.put("uuid", player.getUniqueId().toString());
        playerData.put("displayName", player.getDisplayName());
        
        // 游戏状态
        playerData.put("health", player.getHealth());
        playerData.put("maxHealth", player.getMaxHealth());
        playerData.put("foodLevel", player.getFoodLevel());
        playerData.put("gameMode", player.getGameMode().toString());
        playerData.put("level", player.getLevel());
        playerData.put("exp", player.getExp());
        
        // 位置信息
        playerData.put("world", player.getWorld().getName());
        playerData.put("x", player.getLocation().getX());
        playerData.put("y", player.getLocation().getY());
        playerData.put("z", player.getLocation().getZ());
        
        // 网络信息
        try {
            playerData.put("ip", player.getAddress().getAddress().getHostAddress());
            // getPing()方法在某些Bukkit API版本中可能不存在，使用try-catch处理
            try {
                // 尝试通过反射获取ping方法
                java.lang.reflect.Method pingMethod = player.getClass().getMethod("getPing");
                if (pingMethod != null) {
                    Object pingValue = pingMethod.invoke(player);
                    if (pingValue instanceof Integer) {
                        playerData.put("ping", pingValue);
                    }
                }
            } catch (Exception e) {
                // 如果方法不存在，使用默认值或忽略该字段
                playerData.put("ping", -1); // 使用-1表示无法获取ping值
                if (playerInfo.isDebug()) {
                    logger.info("当前Bukkit API版本不支持getPing()方法，使用默认值");
                }
            }
        } catch (Exception e) {
            if (playerInfo.isDebug()) {
                logger.warning("获取玩家 " + player.getName() + " 的网络信息时出错: " + e.getMessage());
            }
        }
        
        // 存储玩家数据
        playerDataMap.put(player.getUniqueId(), playerData);
    }
    
    /**
     * 将玩家数据转换为JSON字符串
     * 
     * @return 包含所有玩家数据的JSON字符串
     */
    public String getPlayersDataAsJson() {
        JsonObject rootObject = new JsonObject();
        JsonArray playersArray = new JsonArray();
        
        // 添加每个玩家的数据
        for (Map<String, Object> playerData : playerDataMap.values()) {
            playersArray.add(gson.toJsonTree(playerData));
        }
        
        // 添加玩家数组到根对象
        rootObject.add("players", playersArray);
        
        // 添加服务器信息
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", Bukkit.getServerName());
        serverInfo.addProperty("version", Bukkit.getVersion());
        serverInfo.addProperty("bukkitVersion", Bukkit.getBukkitVersion());
        serverInfo.addProperty("onlinePlayers", Bukkit.getOnlinePlayers().size());
        serverInfo.addProperty("maxPlayers", Bukkit.getMaxPlayers());
        
        rootObject.add("server", serverInfo);
        
        return gson.toJson(rootObject);
    }
    
    /**
     * 获取所有玩家数据
     * 
     * @return 玩家数据映射 UUID -> 数据
     */
    public Map<UUID, Map<String, Object>> getPlayersData() {
        return playerDataMap;
    }
    
    /**
     * 获取单个玩家的数据
     * 
     * @param uuid 玩家UUID
     * @return 玩家数据，如果玩家不存在则返回null
     */
    public Map<String, Object> getPlayerData(UUID uuid) {
        return playerDataMap.get(uuid);
    }
    
    /**
     * 获取单个玩家的数据
     * 
     * @param playerName 玩家名称
     * @return 玩家数据，如果玩家不存在则返回null
     */
    public Map<String, Object> getPlayerData(String playerName) {
        for (Map.Entry<UUID, Map<String, Object>> entry : playerDataMap.entrySet()) {
            if (entry.getValue().get("name").equals(playerName)) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    /**
     * 获取单个玩家的数据，并转换为PlayerData对象
     * 
     * @param player 玩家对象
     * @return PlayerData对象
     */
    public PlayerData getPlayerData(Player player) {
        if (player == null) {
            return null;
        }
        
        // 确保玩家数据已经被收集
        UUID playerUUID = player.getUniqueId();
        if (!playerDataMap.containsKey(playerUUID)) {
            collectPlayerData(player);
        }
        
        // 获取玩家主手和副手物品
        ItemStack mainHand = player.getInventory().getItemInHand();
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack offHand = contents.length > 40 ? contents[40] : null;
        
        if (mainHand != null && !mainHand.getType().name().equals("AIR")) {
            mainHand = mainHand.clone();
        } else {
            mainHand = null;
        }
        
        if (offHand != null && !offHand.getType().name().equals("AIR")) {
            offHand = offHand.clone();
        } else {
            offHand = null;
        }
        
        // 构建Mojang API的皮肤URL
        String skinURL = "https://sessionserver.mojang.com/session/minecraft/profile/" + 
                        playerUUID.toString().replace("-", "") + "?unsigned=false";
        
        return new PlayerData(
            player.getName(),
            playerUUID,
            skinURL,
            player.getInventory().getArmorContents().clone(),
            player.getInventory().getContents().clone(),
            mainHand,
            offHand,
            player.getLevel(),
            player.getHealth(),
            player.getMaxHealth(),
            player.getLocation()
        );
    }
} 