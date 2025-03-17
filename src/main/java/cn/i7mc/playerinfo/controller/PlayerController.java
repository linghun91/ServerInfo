package cn.i7mc.playerinfo.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import cn.i7mc.playerinfo.PlayerInfo;
import cn.i7mc.playerinfo.model.PlayerData;
import cn.i7mc.playerinfo.util.ItemStackSerializer;
import cn.i7mc.playerinfo.messaging.MessageSender;
import cn.i7mc.playerinfo.util.PlaceholderManager;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PlayerController implements HttpHandler, Listener {
    private final Gson gson;
    private final PlayerInfo playerInfo;
    private final Logger logger;
    private final JavaPlugin plugin;
    private PlaceholderManager placeholderManager;

    // 玩家数据缓存
    private Map<UUID, Map<String, Object>> playerDataMap;

    public PlayerController(PlayerInfo playerInfo) {
        this.playerInfo = playerInfo;
        this.logger = playerInfo.getLogger();
        this.gson = new GsonBuilder()
            .disableHtmlEscaping()  // 禁用HTML转义，保留原始字符
            .serializeNulls()
            .setLenient()
            .create();
        this.playerDataMap = new ConcurrentHashMap<>();
        this.plugin = playerInfo.getPlugin();
        
        this.placeholderManager = playerInfo.getPlaceholderManager();
        
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
        
        // 移除经济余额相关变量
        
        // 构建Mojang API的皮肤URL
        String skinURL = "https://sessionserver.mojang.com/session/minecraft/profile/" + 
                        playerUUID.toString().replace("-", "") + "?unsigned=false";

        // 获取物品栏内容（只获取0-35号槽位）
        ItemStack[] inventoryContents = new ItemStack[36];
        ItemStack[] fullContents = player.getInventory().getContents();
        
        // 只复制前36个槽位的物品（0-35），跳过装备栏（36-39）和副手（40）
        for (int i = 0; i < 36 && i < fullContents.length; i++) {
            if (fullContents[i] != null && !fullContents[i].getType().name().equals("AIR")) {
                inventoryContents[i] = fullContents[i].clone();
            }
        }

        // 创建Map格式的物品栏数据
        Map<Integer, Map<String, Object>> inventoryMap = new HashMap<>();
        for (int i = 0; i < 36 && i < fullContents.length; i++) {
            if (fullContents[i] != null && !fullContents[i].getType().name().equals("AIR")) {
                inventoryMap.put(i, ItemStackSerializer.serializeItemStack(fullContents[i]));
            }
        }

        // 创建Map格式的装备数据
        Map<String, Map<String, Object>> equipmentMap = new HashMap<>();
        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack chestplate = player.getInventory().getChestplate();
        ItemStack leggings = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();
        
        if (helmet != null && !helmet.getType().name().equals("AIR")) {
            equipmentMap.put("helmet", ItemStackSerializer.serializeItemStack(helmet));
        }
        if (chestplate != null && !chestplate.getType().name().equals("AIR")) {
            equipmentMap.put("chestplate", ItemStackSerializer.serializeItemStack(chestplate));
        }
        if (leggings != null && !leggings.getType().name().equals("AIR")) {
            equipmentMap.put("leggings", ItemStackSerializer.serializeItemStack(leggings));
        }
        if (boots != null && !boots.getType().name().equals("AIR")) {
            equipmentMap.put("boots", ItemStackSerializer.serializeItemStack(boots));
        }
        if (mainHand != null && !mainHand.getType().name().equals("AIR")) {
            equipmentMap.put("mainHand", ItemStackSerializer.serializeItemStack(mainHand));
        }
        if (offHand != null && !offHand.getType().name().equals("AIR")) {
            equipmentMap.put("offHand", ItemStackSerializer.serializeItemStack(offHand));
        }

        // 使用无参构造函数创建PlayerData对象
        PlayerData playerData = new PlayerData();
        playerData.setName(player.getName());
        playerData.setUuid(playerUUID.toString());
        playerData.setSkinURL(skinURL);
        playerData.setArmor(player.getInventory().getArmorContents().clone());
        playerData.setInventory(inventoryContents);
        playerData.setMainHand(mainHand);
        playerData.setOffHand(offHand);
        playerData.setLevel(level);
        playerData.setHealth(health);
        playerData.setMaxHealth(maxHealth);
        playerData.setLocation(location);
        
        // 设置Map格式的物品栏和装备数据
        playerData.setInventory(inventoryMap);
        playerData.setEquipment(equipmentMap);

        // 添加占位符数据
        if (placeholderManager != null) {
            try {
                
                // 使用新方法获取占位符数据，包含名称映射
                JsonObject placeholders = placeholderManager.getPlaceholdersJson(player);
                if (placeholders != null) {
                    
                    // 检查占位符数据是否有内容
                    if (placeholders.has("placeholders")) {
                        JsonArray placeholdersArray = placeholders.getAsJsonArray("placeholders");
                        
                        // 打印每个占位符详情
                        for (int i = 0; i < placeholdersArray.size(); i++) {
                            try {
                                JsonObject placeholder = placeholdersArray.get(i).getAsJsonObject();
                            } catch (Exception e) {
                            }
                        }
                    } else {
                    }
                    
                    // 检查名称映射
                    if (placeholders.has("nameMapping")) {
                    } else {
                    }
                    
                    // 直接使用JsonObject，不需要转换为Map
                    playerData.setPlaceholders(placeholders);
                } else {
                }
            } catch (Exception e) {
                e.printStackTrace(); // 添加堆栈跟踪以便更好地诊断问题
            }
        } else {
        }

        // 添加DragonCore容器数据
        try {
            // 检查DragonCore插件是否可用
            Plugin dragonCore = Bukkit.getPluginManager().getPlugin("DragonCore");
            if (dragonCore != null && dragonCore.isEnabled()) {
                try {
                    // 直接使用DragonCore的API获取物品
                    Map<String, Map<String, Object>> dragonCoreItems = new LinkedHashMap<>();
                    
                    // 安全地获取玩家的DragonCore容器物品，使用反射防止类加载错误
                    Map<String, ItemStack> playerItems = null;
                    try {
                        // 尝试获取DragonCore API类
                        Class<?> slotAPIClass = Class.forName("eos.moe.dragoncore.api.SlotAPI");
                        
                        // 获取getCacheAllSlotItem方法
                        java.lang.reflect.Method getCacheAllSlotItem = slotAPIClass.getMethod("getCacheAllSlotItem", Player.class);
                        
                        // 调用静态方法
                        @SuppressWarnings("unchecked")
                        Map<String, ItemStack> result = (Map<String, ItemStack>) getCacheAllSlotItem.invoke(null, player);
                        playerItems = result;
                        
                        // 打印获取到的物品数据
                        if (result != null) {
                            for (Map.Entry<String, ItemStack> entry : result.entrySet()) {
                                if (entry.getValue() != null) {
                                } else {
                                }
                            }
                        } else {
                        }
                    } catch (ClassNotFoundException e) {
                    } catch (NoSuchMethodException e) {
                    } catch (Exception e) {
                        if (playerInfo.isDebug()) {
                            e.printStackTrace();
                        }
                    }
                    
                    if (playerItems != null && !playerItems.isEmpty()) {
                        // 序列化物品
                        for (Map.Entry<String, ItemStack> entry : playerItems.entrySet()) {
                            if (entry.getValue() != null && !entry.getValue().getType().name().equals("AIR")) {
                                dragonCoreItems.put(entry.getKey(), ItemStackSerializer.serializeItemStack(entry.getValue()));
                            } else if (entry.getValue() != null) {
                            }
                        }
                        
                        // 添加到玩家数据
                        if (!dragonCoreItems.isEmpty()) {
                            playerData.setDragonCore(dragonCoreItems);
                        } else {
                        }
                    } else {
                    }
                } catch (Exception e) {
                    if (playerInfo.isDebug()) {
                        e.printStackTrace();
                    }
                }
            } else {
            }
        } catch (Exception e) {
            if (playerInfo.isDebug()) {
                e.printStackTrace();
            }
        }

        String response = gson.toJson(ItemStackSerializer.serialize(playerData));
        
        // 在返回前添加最终的JSON数据调试日志
        try {
            // 解析回JSON对象以检查占位符是否存在
            JsonObject jsonObj = gson.fromJson(response, JsonObject.class);
            if (jsonObj.has("placeholders")) {
            } else {
            }
        } catch (Exception e) {
        }
        
        return response;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        // 确保使用UTF-8编码处理响应
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
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
        }
    }
    
    /**
     * 收集单个玩家的数据
     * 
     * @param player Bukkit玩家对象
     */
    private void collectPlayerData(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        
        try {
            UUID playerUUID = player.getUniqueId();
            Map<String, Object> playerData = new LinkedHashMap<>();
            
            // 收集基本玩家信息
            playerData.put("uuid", playerUUID.toString());
            playerData.put("username", player.getName());
            playerData.put("displayName", ChatColor.stripColor(player.getDisplayName()));
            playerData.put("isOp", player.isOp());
            
            // 收集玩家状态信息
            playerData.put("health", player.getHealth());
            playerData.put("maxHealth", player.getMaxHealth());
            playerData.put("foodLevel", player.getFoodLevel());
            playerData.put("exhaustion", player.getExhaustion());
            playerData.put("saturation", player.getSaturation());
            playerData.put("gameMode", player.getGameMode().toString());
            playerData.put("level", player.getLevel());
            playerData.put("exp", player.getExp());
            
            // 收集位置信息
            Location loc = player.getLocation();
            playerData.put("world", loc.getWorld().getName());
            playerData.put("x", loc.getX());
            playerData.put("y", loc.getY());
            playerData.put("z", loc.getZ());
            playerData.put("yaw", loc.getYaw());
            playerData.put("pitch", loc.getPitch());
            
            // 收集物品信息 - 使用Map格式
            Map<Integer, Map<String, Object>> items = new LinkedHashMap<>();
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null && !contents[i].getType().name().equals("AIR")) {
                    items.put(i, ItemStackSerializer.serializeItemStack(contents[i]));
                }
            }
            playerData.put("inventory", items);
            
            // 收集装备信息 - 使用Map格式
            Map<String, Map<String, Object>> equipment = new LinkedHashMap<>();
            PlayerInventory inventory = player.getInventory();
            ItemStack helmet = inventory.getHelmet();
            ItemStack chestplate = inventory.getChestplate();
            ItemStack leggings = inventory.getLeggings();
            ItemStack boots = inventory.getBoots();
            ItemStack mainHand = inventory.getItemInMainHand();
            ItemStack offHand = inventory.getItemInOffHand();
            
            if (helmet != null && !helmet.getType().name().equals("AIR")) {
                equipment.put("helmet", ItemStackSerializer.serializeItemStack(helmet));
            }
            if (chestplate != null && !chestplate.getType().name().equals("AIR")) {
                equipment.put("chestplate", ItemStackSerializer.serializeItemStack(chestplate));
            }
            if (leggings != null && !leggings.getType().name().equals("AIR")) {
                equipment.put("leggings", ItemStackSerializer.serializeItemStack(leggings));
            }
            if (boots != null && !boots.getType().name().equals("AIR")) {
                equipment.put("boots", ItemStackSerializer.serializeItemStack(boots));
            }
            if (mainHand != null && !mainHand.getType().name().equals("AIR")) {
                equipment.put("mainHand", ItemStackSerializer.serializeItemStack(mainHand));
            }
            if (offHand != null && !offHand.getType().name().equals("AIR")) {
                equipment.put("offHand", ItemStackSerializer.serializeItemStack(offHand));
            }
            
            playerData.put("equipment", equipment);
            

            
            // 添加占位符数据
            if (placeholderManager != null) {
                try {
                    // 使用新方法获取占位符数据，包含名称映射
                    JsonObject placeholders = placeholderManager.getPlaceholdersJson(player);
                    if (placeholders != null) {
                        // 直接使用JsonObject，不需要转换为Map
                        playerData.put("placeholders", placeholders);
                        
                        if (playerInfo.isDebug()) {
                            logger.info("已为玩家 " + player.getName() + " 添加占位符数据");
                        }
                    }
                } catch (Exception e) {
                }
            }
            
            // 添加DragonCore容器数据
            try {
                // 检查DragonCore插件是否可用
                Plugin dragonCore = Bukkit.getPluginManager().getPlugin("DragonCore");
                if (dragonCore != null && dragonCore.isEnabled()) {
                    try {
                        // 直接使用DragonCore的API获取物品
                        Map<String, Map<String, Object>> dragonCoreItems = new LinkedHashMap<>();
                        
                        // 安全地获取玩家的DragonCore容器物品，使用反射防止类加载错误
                        Map<String, ItemStack> playerItems = null;
                        try {
                            // 尝试获取DragonCore API类
                            Class<?> slotAPIClass = Class.forName("eos.moe.dragoncore.api.SlotAPI");
                            
                            // 获取getCacheAllSlotItem方法
                            java.lang.reflect.Method getCacheAllSlotItem = slotAPIClass.getMethod("getCacheAllSlotItem", Player.class);
                            
                            // 调用静态方法
                            @SuppressWarnings("unchecked")
                            Map<String, ItemStack> result = (Map<String, ItemStack>) getCacheAllSlotItem.invoke(null, player);
                            playerItems = result;
                            
                            // 打印获取到的物品数据
                            if (result != null) {
                                for (Map.Entry<String, ItemStack> entry : result.entrySet()) {
                                    if (entry.getValue() != null) {
                                    } else {
                                    }
                                }
                            } else {
                            }
                        } catch (ClassNotFoundException e) {
                        } catch (NoSuchMethodException e) {
                        } catch (Exception e) {
                            if (playerInfo.isDebug()) {
                                e.printStackTrace();
                            }
                        }
                        
                        if (playerItems != null && !playerItems.isEmpty()) {
                            // 序列化物品
                            for (Map.Entry<String, ItemStack> entry : playerItems.entrySet()) {
                                if (entry.getValue() != null && !entry.getValue().getType().name().equals("AIR")) {
                                    dragonCoreItems.put(entry.getKey(), ItemStackSerializer.serializeItemStack(entry.getValue()));
                                } else if (entry.getValue() != null) {
                                }
                            }
                            
                            // 添加到玩家数据
                            if (!dragonCoreItems.isEmpty()) {
                                playerData.put("dragonCore", dragonCoreItems);
                            } else {
                            }
                        } else {
                        }
                    } catch (Exception e) {
                        if (playerInfo.isDebug()) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                if (playerInfo.isDebug()) {
                    e.printStackTrace();
                }
            }
            
            // 更新玩家数据缓存
            playerDataMap.put(playerUUID, playerData);
            
            if (playerInfo.isDebug()) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        
        // 使用StringWriter确保UTF-8编码正确
        try {
            StringWriter writer = new StringWriter();
            gson.toJson(rootObject, writer);
            String jsonString = writer.toString();
            
            // 额外的编码保障：转换为字节数组再转回字符串，确保UTF-8编码
            byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);
            return new String(jsonBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return gson.toJson(rootObject);
        }
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
        
        try {
            // 获取玩家基本信息
            String playerName = player.getName();
            UUID uuid = player.getUniqueId();
            
            // 获取玩家皮肤URL
            String skinURL = getSkinURL(playerName);
            
            // 获取玩家装备和物品栏
            ItemStack[] armor = player.getInventory().getArmorContents();
            ItemStack[] inventory = player.getInventory().getContents();
            
            // 获取玩家主副手物品
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            
            // 获取玩家等级和生命值
            int level = player.getLevel();
            double health = player.getHealth();
            double maxHealth = player.getMaxHealth();
            
            // 获取玩家位置
            Location location = player.getLocation();
            

            // 创建玩家数据对象
            PlayerData playerData = new PlayerData();
            playerData.setName(playerName);
            playerData.setUuid(uuid.toString());
            playerData.setSkinURL(skinURL);
            playerData.setArmor(armor);
            playerData.setInventory(inventory);
            playerData.setMainHand(mainHand);
            playerData.setOffHand(offHand);
            playerData.setLevel(level);
            playerData.setHealth(health);
            playerData.setMaxHealth(maxHealth);
            playerData.setLocation(location);
            


            // 添加占位符数据
            if (placeholderManager != null) {
                try {
                    
                    // 使用新方法获取占位符数据，包含名称映射
                    JsonObject placeholders = placeholderManager.getPlaceholdersJson(player);
                    if (placeholders != null) {
                        
                        // 检查占位符数据是否有内容
                        if (placeholders.has("placeholders")) {
                            JsonArray placeholdersArray = placeholders.getAsJsonArray("placeholders");
                            
                            // 打印每个占位符详情
                            for (int i = 0; i < placeholdersArray.size(); i++) {
                                try {
                                    JsonObject placeholder = placeholdersArray.get(i).getAsJsonObject();
                                } catch (Exception e) {
                                }
                            }
                        } else {
                        }
                        
                        // 检查名称映射
                        if (placeholders.has("nameMapping")) {
                        } else {
                        }
                        
                        // 直接使用JsonObject，不需要转换为Map
                        playerData.setPlaceholders(placeholders);
                        
                    } else {
                    }
                } catch (Exception e) {
                    e.printStackTrace(); // 添加堆栈跟踪以便更好地诊断问题
                }
            } else {
            }
            
            // 添加DragonCore容器数据
            try {
                // 检查DragonCore插件是否可用
                Plugin dragonCore = Bukkit.getPluginManager().getPlugin("DragonCore");
                if (dragonCore != null && dragonCore.isEnabled()) {
                    try {
                        // 直接使用DragonCore的API获取物品
                        Map<String, Map<String, Object>> dragonCoreItems = new LinkedHashMap<>();
                        
                        // 安全地获取玩家的DragonCore容器物品，使用反射防止类加载错误
                        Map<String, ItemStack> playerItems = null;
                        try {
                            // 尝试获取DragonCore API类
                            Class<?> slotAPIClass = Class.forName("eos.moe.dragoncore.api.SlotAPI");
                            
                            // 获取getCacheAllSlotItem方法
                            java.lang.reflect.Method getCacheAllSlotItem = slotAPIClass.getMethod("getCacheAllSlotItem", Player.class);
                            
                            // 调用静态方法
                            @SuppressWarnings("unchecked")
                            Map<String, ItemStack> result = (Map<String, ItemStack>) getCacheAllSlotItem.invoke(null, player);
                            playerItems = result;
                            
                            // 打印获取到的物品数据
                            if (result != null) {
                                for (Map.Entry<String, ItemStack> entry : result.entrySet()) {
                                    if (entry.getValue() != null) {
                                    } else {
                                    }
                                }
                            } else {
                            }
                        } catch (ClassNotFoundException e) {
                        } catch (NoSuchMethodException e) {
                        } catch (Exception e) {
                            if (playerInfo.isDebug()) {
                                e.printStackTrace();
                            }
                        }
                        
                        if (playerItems != null && !playerItems.isEmpty()) {
                            // 序列化物品
                            for (Map.Entry<String, ItemStack> entry : playerItems.entrySet()) {
                                if (entry.getValue() != null && !entry.getValue().getType().name().equals("AIR")) {
                                    dragonCoreItems.put(entry.getKey(), ItemStackSerializer.serializeItemStack(entry.getValue()));
                                    } else if (entry.getValue() != null) {
                                }
                            }
                            
                            // 添加到玩家数据
                            if (!dragonCoreItems.isEmpty()) {
                                playerData.setDragonCore(dragonCoreItems);
                            } else {
                            }
                        } else {
                        }
                    } catch (Exception e) {
                        if (playerInfo.isDebug()) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                if (playerInfo.isDebug()) {
                    e.printStackTrace();
                }
            }
            
            return playerData;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取玩家皮肤URL
     * @param playerName 玩家名称
     * @return 皮肤URL
     */
    private String getSkinURL(String playerName) {
        try {
            // 尝试获取玩家UUID
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                UUID uuid = player.getUniqueId();
                // 构建Mojang API的皮肤URL
                return "https://sessionserver.mojang.com/session/minecraft/profile/" + 
                       uuid.toString().replace("-", "") + "?unsigned=false";
            }
        } catch (Exception e) {
        }
        
        // 返回默认皮肤URL
        return "https://textures.minecraft.net/texture/1a4ab6b2d9a0b4e2bb9d8fc70b49e84c6e388a8999d6f3d6958c1f92d8b8e765";
    }

    /**
     * 收集玩家数据
     */
    public void collectPlayerData() {
        if (playerInfo != null && playerInfo.isDebug()) {
            logger.info("正在收集玩家数据...");
        }
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            String name = player.getName();
            
            PlayerData playerData = new PlayerData();
            playerData.setUuid(uuid.toString());
            playerData.setName(name);
            playerData.setHealth(player.getHealth());
            playerData.setMaxHealth(player.getMaxHealth());
            playerData.setFoodLevel(player.getFoodLevel());
            playerData.setLevel(player.getLevel());
            playerData.setExp((float) (Math.round(player.getExp() * 100.0) / 100.0));
            playerData.setGamemode(player.getGameMode().name());
            
            // 位置信息
            playerData.setWorld(player.getWorld().getName());
            Location loc = player.getLocation();
            playerData.setX(loc.getX());
            playerData.setY(loc.getY());
            playerData.setZ(loc.getZ());
            playerData.setYaw(loc.getYaw());
            playerData.setPitch(loc.getPitch());
            
            // 收集库存信息
            org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
            Map<Integer, Map<String, Object>> items = new HashMap<>();
            
            for (int i = 0; i < 36; i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && !item.getType().name().equals("AIR")) {
                    Map<String, Object> itemData = ItemStackSerializer.serializeItemStack(item);
                    items.put(i, itemData);
                }
            }
            
            // 收集装备信息
            Map<String, Map<String, Object>> equipment = new HashMap<>();
            ItemStack helmet = inventory.getHelmet();
            ItemStack chestplate = inventory.getChestplate();
            ItemStack leggings = inventory.getLeggings();
            ItemStack boots = inventory.getBoots();
            ItemStack offhand = inventory.getItemInOffHand();
            
            if (helmet != null && !helmet.getType().name().equals("AIR")) {
                equipment.put("helmet", ItemStackSerializer.serializeItemStack(helmet));
            }
            if (chestplate != null && !chestplate.getType().name().equals("AIR")) {
                equipment.put("chestplate", ItemStackSerializer.serializeItemStack(chestplate));
            }
            if (leggings != null && !leggings.getType().name().equals("AIR")) {
                equipment.put("leggings", ItemStackSerializer.serializeItemStack(leggings));
            }
            if (boots != null && !boots.getType().name().equals("AIR")) {
                equipment.put("boots", ItemStackSerializer.serializeItemStack(boots));
            }
            if (offhand != null && !offhand.getType().name().equals("AIR")) {
                equipment.put("offhand", ItemStackSerializer.serializeItemStack(offhand));
            }
            
            playerData.setInventory(items);
            playerData.setEquipment(equipment);
            
            // 添加占位符数据
            if (placeholderManager != null) {
                try {
                    // 使用新方法获取占位符数据，包含名称映射
                    JsonObject placeholders = placeholderManager.getPlaceholdersJson(player);
                    if (placeholders != null) {
                        // 直接使用JsonObject，不需要转换为Map
                        playerData.setPlaceholders(placeholders);
                        
                        if (playerInfo.isDebug()) {
                        }
                    }
                } catch (Exception e) {
                }
            }
            
            // 添加DragonCore容器数据
            try {
                // 检查DragonCore插件是否可用
                Plugin dragonCore = Bukkit.getPluginManager().getPlugin("DragonCore");
                if (dragonCore != null && dragonCore.isEnabled()) {
                    try {
                        // 直接使用DragonCore的API获取物品
                        Map<String, Map<String, Object>> dragonCoreItems = new LinkedHashMap<>();
                        
                        // 安全地获取玩家的DragonCore容器物品，使用反射防止类加载错误
                        Map<String, ItemStack> playerItems = null;
                        try {
                            // 尝试获取DragonCore API类
                            Class<?> slotAPIClass = Class.forName("eos.moe.dragoncore.api.SlotAPI");
                            
                            // 获取getCacheAllSlotItem方法
                            java.lang.reflect.Method getCacheAllSlotItem = slotAPIClass.getMethod("getCacheAllSlotItem", Player.class);
                            
                            // 调用静态方法
                            @SuppressWarnings("unchecked")
                            Map<String, ItemStack> result = (Map<String, ItemStack>) getCacheAllSlotItem.invoke(null, player);
                            playerItems = result;
                            
                            // 打印获取到的物品数据
                            if (result != null) {
                                for (Map.Entry<String, ItemStack> entry : result.entrySet()) {
                                    if (entry.getValue() != null) {
                                    } else {
                                    }
                                }
                            } else {
                            }
                        } catch (ClassNotFoundException e) {
                        } catch (NoSuchMethodException e) {
                        } catch (Exception e) {
                            if (playerInfo.isDebug()) {
                                e.printStackTrace();
                            }
                        }
                        
                        if (playerItems != null && !playerItems.isEmpty()) {
                            // 序列化物品
                            for (Map.Entry<String, ItemStack> entry : playerItems.entrySet()) {
                                if (entry.getValue() != null && !entry.getValue().getType().name().equals("AIR")) {
                                    dragonCoreItems.put(entry.getKey(), ItemStackSerializer.serializeItemStack(entry.getValue()));
                                } else if (entry.getValue() != null) {
                                }
                            }
                            
                            // 添加到玩家数据
                            if (!dragonCoreItems.isEmpty()) {
                                playerData.setDragonCore(dragonCoreItems);
                            } else {
                            }
                        } else {
                        }
                    } catch (Exception e) {
                        if (playerInfo.isDebug()) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                if (playerInfo.isDebug()) {
                    e.printStackTrace();
                }
            }
            
            // 将PlayerData对象序列化为Map后再存入playerDataMap
            Map<String, Object> serializedData = ItemStackSerializer.serialize(playerData);
            playerDataMap.put(player.getUniqueId(), serializedData);
            
            if (playerInfo.isDebug()) {
            }
        }
    }

    /**
     * 获取玩家数据 - JSON格式输出
     */
    private String getPlayerDataJson(Player player) {
        JsonObject playerJson = new JsonObject();
        
        // 基本信息
        playerJson.addProperty("name", player.getName());
        playerJson.addProperty("displayName", ChatColor.stripColor(player.getDisplayName()));
        playerJson.addProperty("uuid", player.getUniqueId().toString());
        
        // 在线状态
        playerJson.addProperty("online", player.isOnline());
        
        // 位置信息
        Location loc = player.getLocation();
        JsonObject locationJson = new JsonObject();
        locationJson.addProperty("world", loc.getWorld().getName());
        locationJson.addProperty("x", Math.round(loc.getX() * 100.0) / 100.0);
        locationJson.addProperty("y", Math.round(loc.getY() * 100.0) / 100.0);
        locationJson.addProperty("z", Math.round(loc.getZ() * 100.0) / 100.0);
        locationJson.addProperty("yaw", Math.round(loc.getYaw() * 100.0) / 100.0);
        locationJson.addProperty("pitch", Math.round(loc.getPitch() * 100.0) / 100.0);
        playerJson.add("location", locationJson);
        
        // 游戏模式
        playerJson.addProperty("gameMode", player.getGameMode().toString());
        
        // 健康状态
        playerJson.addProperty("health", player.getHealth());
        playerJson.addProperty("maxHealth", player.getMaxHealth());
        playerJson.addProperty("foodLevel", player.getFoodLevel());
        playerJson.addProperty("exhaustion", player.getExhaustion());
        playerJson.addProperty("saturation", player.getSaturation());
        
        // 经验值
        playerJson.addProperty("exp", player.getExp());
        playerJson.addProperty("level", player.getLevel());
        playerJson.addProperty("totalExperience", player.getTotalExperience());

        
        // 自定义占位符信息
        if (placeholderManager != null) {
            JsonArray placeholdersArray = new JsonArray();
            JsonObject placeholdersJson = placeholderManager.updatePlayerPlaceholders(player);
            if (placeholdersJson.has("placeholders")) {
                placeholdersArray = placeholdersJson.getAsJsonArray("placeholders");
            }
            playerJson.add("placeholders", placeholdersArray);
        }
        
        // 物品栏信息
        PlayerInventory inventory = player.getInventory();
        ItemStackSerializer serializer = new ItemStackSerializer();
        
        // 主手物品
        ItemStack mainHandItem = inventory.getItemInMainHand();
        if (mainHandItem != null && mainHandItem.getType().name() != "AIR") {
            JsonObject mainHandJson = convertMapToJsonObject(serializer.serializeItemStack(mainHandItem));
            playerJson.add("mainHandItem", mainHandJson);
        }
        
        // 副手物品
        ItemStack offHandItem = inventory.getItemInOffHand();
        if (offHandItem != null && offHandItem.getType().name() != "AIR") {
            JsonObject offHandJson = convertMapToJsonObject(serializer.serializeItemStack(offHandItem));
            playerJson.add("offHandItem", offHandJson);
        }
        
        // 装备信息
        JsonArray armorArray = new JsonArray();
        ItemStack[] armorContents = inventory.getArmorContents();
        
        for (int i = 0; i < armorContents.length; i++) {
            ItemStack armor = armorContents[i];
            if (armor != null && armor.getType().name() != "AIR") {
                JsonObject armorJson = convertMapToJsonObject(serializer.serializeItemStack(armor));
                armorArray.add(armorJson);
            } else {
                armorArray.add(new JsonObject()); // 占位
            }
        }
        
        playerJson.add("armor", armorArray);
        
        // 背包物品
        JsonArray inventoryArray = new JsonArray();
        
        for (int i = 0; i < 36; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null) {
                JsonObject itemJson = convertMapToJsonObject(serializer.serializeItemStack(item));
                inventoryArray.add(itemJson);
            } else {
                inventoryArray.add(new JsonObject()); // 空物品占位符
            }
        }
        
        playerJson.add("inventory", inventoryArray);
        
        return gson.toJson(playerJson);
    }

    /**
     * 创建并返回包含玩家数据的PlayerData对象
     */
    private PlayerData createPlayerData(Player player) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        
        PlayerData playerData = new PlayerData();
        
        // 基本信息
        playerData.setName(player.getName());
        playerData.setUuid(player.getUniqueId().toString());
        
        // 位置信息
        Location loc = player.getLocation();
        playerData.setLocation(loc);
        playerData.setX(loc.getX());
        playerData.setY(loc.getY());
        playerData.setZ(loc.getZ());
        playerData.setYaw(loc.getYaw());
        playerData.setPitch(loc.getPitch());
        
        // 健康状态
        playerData.setHealth(player.getHealth());
        playerData.setMaxHealth(player.getMaxHealth());
        playerData.setFoodLevel(player.getFoodLevel());
        
        // 游戏模式
        playerData.setGamemode(player.getGameMode().name());
        
        // 经验相关
        playerData.setLevel(player.getLevel());
        playerData.setExp(player.getExp());
        

        // 添加自定义占位符信息
        if (placeholderManager != null) {
            JsonObject placeholders = placeholderManager.updatePlayerPlaceholders(player);
            playerData.setPlaceholders(placeholders);
        }
        
        return playerData;
    }

    // 添加辅助方法用于将Map转换为JsonObject
    private JsonObject convertMapToJsonObject(Map<String, Object> map) {
        JsonObject jsonObject = new JsonObject();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof String) {
                jsonObject.addProperty(key, (String) value);
            } else if (value instanceof Number) {
                jsonObject.addProperty(key, (Number) value);
            } else if (value instanceof Boolean) {
                jsonObject.addProperty(key, (Boolean) value);
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                jsonObject.add(key, convertMapToJsonObject(nestedMap));
            } else if (value == null) {
                jsonObject.add(key, null);
            } else {
                jsonObject.addProperty(key, value.toString());
            }
        }
        return jsonObject;
    }
} 