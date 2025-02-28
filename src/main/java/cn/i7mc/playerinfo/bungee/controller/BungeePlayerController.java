package cn.i7mc.playerinfo.bungee.controller;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import cn.i7mc.playerinfo.bungee.PlayerInfoBungee;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;

/**
 * BungeeCord玩家数据控制器
 * 负责管理从各个Spigot服务器收集的玩家数据
 */
public class BungeePlayerController {
    private final PlayerInfoBungee plugin;
    private final Logger logger;
    private final Gson gson;
    
    // 按服务器存储的玩家数据Map
    private final Map<String, Map<UUID, String>> playerDataMap;
    // 数据最后更新时间Map
    private final Map<String, Map<UUID, Long>> lastUpdateTimeMap;
    
    /**
     * 构造函数
     * @param plugin 插件实例
     */
    public BungeePlayerController(PlayerInfoBungee plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPlugin().getLogger();
        this.gson = new Gson();
        this.playerDataMap = new ConcurrentHashMap<>();
        this.lastUpdateTimeMap = new ConcurrentHashMap<>();
    }
    
    /**
     * 获取插件实例
     * 
     * @return PlayerInfoBungee实例
     */
    public PlayerInfoBungee getPlugin() {
        return plugin;
    }
    
    /**
     * 更新玩家数据
     * 
     * @param serverName 服务器名称
     * @param playerUUID 玩家UUID
     * @param jsonData JSON格式的玩家数据
     */
    public void updatePlayerData(String serverName, UUID playerUUID, String jsonData) {
        // 确保服务器的数据映射存在
        playerDataMap.computeIfAbsent(serverName, k -> new ConcurrentHashMap<>());
        lastUpdateTimeMap.computeIfAbsent(serverName, k -> new ConcurrentHashMap<>());
        
        // 更新数据和时间戳
        playerDataMap.get(serverName).put(playerUUID, jsonData);
        lastUpdateTimeMap.get(serverName).put(playerUUID, System.currentTimeMillis());
    }
    
    /**
     * 移除玩家数据
     * 
     * @param serverName 服务器名称
     * @param playerUUID 玩家UUID
     */
    public void removePlayerData(String serverName, UUID playerUUID) {
        // 检查服务器映射是否存在
        if (playerDataMap.containsKey(serverName)) {
            // 获取要删除的玩家数据
            Map<UUID, String> serverPlayers = playerDataMap.get(serverName);
            String playerData = serverPlayers.get(playerUUID);
            
            if (playerData != null) {
                // 移除玩家数据
                serverPlayers.remove(playerUUID);
            }
        }
        
        if (lastUpdateTimeMap.containsKey(serverName)) {
            lastUpdateTimeMap.get(serverName).remove(playerUUID);
        }
    }
    
    /**
     * 清理过时的数据
     * 
     * @param maxAge 最大数据年龄（毫秒）
     */
    public void cleanupStaleData(long maxAge) {
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;
        
        for (String serverName : lastUpdateTimeMap.keySet()) {
            Map<UUID, Long> serverTimes = lastUpdateTimeMap.get(serverName);
            List<UUID> toRemove = new ArrayList<>();
            
            for (Map.Entry<UUID, Long> entry : serverTimes.entrySet()) {
                if (currentTime - entry.getValue() > maxAge) {
                    toRemove.add(entry.getKey());
                }
            }
            
            // 移除过期数据
            for (UUID uuid : toRemove) {
                removePlayerData(serverName, uuid);
                removedCount++;
            }
        }
    }
    
    /**
     * 处理服务器列表请求
     * 
     * @return 包含服务器列表的JSON
     */
    public String handleServerList() {
        JsonObject response = new JsonObject();
        // 从BungeeCord API获取所有服务器
        Map<String, ServerInfo> bungeeServers = ProxyServer.getInstance().getServers();
        List<String> servers = new ArrayList<>(bungeeServers.keySet());
        
        response.add("servers", gson.toJsonTree(servers));
        return gson.toJson(response);
    }
    
    /**
     * 处理服务器列表请求，包含每个服务器的玩家数量
     * 
     * @return 包含服务器列表和玩家数量的JSON
     */
    public String handleServerListWithPlayerCount() {
        JsonObject response = new JsonObject();
        JsonArray serversArray = new JsonArray();
        
        // 从BungeeCord API获取所有服务器
        Map<String, ServerInfo> bungeeServers = ProxyServer.getInstance().getServers();
        
        // 为每个服务器创建一个包含名称和玩家数量的对象
        for (Map.Entry<String, ServerInfo> entry : bungeeServers.entrySet()) {
            String serverName = entry.getKey();
            ServerInfo serverInfo = entry.getValue();
            
            JsonObject serverData = new JsonObject();
            serverData.addProperty("name", serverName);
            
            // 获取玩家数量，优先使用我们缓存的数据
            int playerCount = 0;
            if (playerDataMap.containsKey(serverName)) {
                playerCount = playerDataMap.get(serverName).size();
            } else {
                // 如果我们没有数据，尝试从ServerInfo获取
                playerCount = serverInfo.getPlayers().size();
            }
            
            serverData.addProperty("playerCount", playerCount);
            serversArray.add(serverData);
        }
        
        response.add("servers", serversArray);
        return gson.toJson(response);
    }
    
    /**
     * 处理玩家列表请求
     * 
     * @param serverName 服务器名称
     * @return 包含玩家列表的JSON
     */
    public String handlePlayerList(String serverName) {
        JsonObject response = new JsonObject();
        
        if (playerDataMap.containsKey(serverName)) {
            // 提取玩家名称列表
            List<String> playerNames = new ArrayList<>();
            Map<UUID, String> serverData = playerDataMap.get(serverName);
            
            for (String jsonData : serverData.values()) {
                try {
                    JsonObject playerObj = gson.fromJson(jsonData, JsonObject.class);
                    if (playerObj.has("name")) {
                        String playerName = playerObj.get("name").getAsString();
                        playerNames.add(playerName);
                    } else {
                        logger.warning("玩家数据中缺少name字段: " + jsonData);
                    }
                } catch (Exception e) {
                    logger.warning("解析玩家数据时出错: " + e.getMessage());
                }
            }
            
            response.add("players", gson.toJsonTree(playerNames));
        } else {
            response.add("players", gson.toJsonTree(new ArrayList<String>()));
        }
        
        String jsonResponse = gson.toJson(response);
        return jsonResponse;
    }
    
    /**
     * 处理玩家详情请求
     * 
     * @param serverName 服务器名称
     * @param playerName 玩家名称
     * @return 包含玩家详情的JSON
     */
    public String handlePlayerDetails(String serverName, String playerName) {
        if (!playerDataMap.containsKey(serverName)) {
            return "{\"error\":\"Server not found\"}";
        }
        
        // 在服务器中查找玩家
        for (String jsonData : playerDataMap.get(serverName).values()) {
            try {
                JsonObject playerObj = gson.fromJson(jsonData, JsonObject.class);
                if (playerObj.has("name") && 
                    playerObj.get("name").getAsString().equalsIgnoreCase(playerName)) {
                    return jsonData;
                }
            } catch (Exception e) {
                logger.warning("解析玩家数据时出错: " + e.getMessage());
            }
        }
        
        return "{\"error\":\"Player not found\"}";
    }
    
    /**
     * 批量更新玩家数据
     * 
     * @param serverName 服务器名称
     * @param jsonData 包含多个玩家数据的JSON字符串
     */
    public void updatePlayersData(String serverName, String jsonData) {
        try {
            // 检查是否是新格式的数据（从ItemStackSerializer序列化的Map）
            JsonObject dataObj = gson.fromJson(jsonData, JsonObject.class);
            
            if (dataObj.has("uuid")) {
                // 单个玩家数据的新格式
                try {
                    String uuid = dataObj.get("uuid").getAsString();
                    UUID playerUUID = UUID.fromString(uuid);
                    updatePlayerData(serverName, playerUUID, jsonData);
                } catch (IllegalArgumentException e) {
                    logger.warning("无效的UUID格式: " + dataObj.get("uuid").getAsString());
                }
            } else if (dataObj.has("players") && dataObj.get("players").isJsonArray()) {
                // 旧格式 - 玩家数组
                dataObj.get("players").getAsJsonArray().forEach(playerElement -> {
                    JsonObject playerObj = playerElement.getAsJsonObject();
                    if (playerObj.has("name") && playerObj.has("uuid")) {
                        String uuid = playerObj.get("uuid").getAsString();
                        try {
                            UUID playerUUID = UUID.fromString(uuid);
                            updatePlayerData(serverName, playerUUID, playerObj.toString());
                        } catch (IllegalArgumentException e) {
                            logger.warning("无效的UUID格式: " + uuid);
                        }
                    }
                });
            } else {
                logger.warning("无法识别的JSON数据格式: " + jsonData);
            }
        } catch (Exception e) {
            logger.warning("批量更新玩家数据时出错: " + e.getMessage());
            if (plugin.isDebug()) {
                logger.warning("数据内容: " + jsonData);
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 获取指定服务器的所有玩家数据
     * 
     * @param serverName 服务器名称
     * @return 该服务器的玩家数据映射 UUID -> 玩家数据，如果服务器不存在则返回null
     */
    public Map<UUID, String> getServerPlayerData(String serverName) {
        return playerDataMap.get(serverName);
    }
    
    /**
     * 关键的BungeeCord数据调整
     * 当检测到同一个玩家在多个服务器上时，进行修复
     * 
     * @param playerUUID 玩家UUID
     * @param currentServer 玩家当前所在服务器
     */
    public void correctPlayerServerData(UUID playerUUID, String currentServer) {
        // 检查玩家是否在其他服务器的数据中存在
        for (String serverName : playerDataMap.keySet()) {
            if (!serverName.equals(currentServer)) {
                Map<UUID, String> serverPlayers = playerDataMap.get(serverName);
                if (serverPlayers != null && serverPlayers.containsKey(playerUUID)) {
                    // 从其他服务器移除此玩家
                    serverPlayers.remove(playerUUID);
                    if (lastUpdateTimeMap.containsKey(serverName)) {
                        lastUpdateTimeMap.get(serverName).remove(playerUUID);
                    }
                }
            }
        }
    }
} 