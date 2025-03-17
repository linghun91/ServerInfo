package cn.i7mc.playerinfo.bungee.messaging;

import cn.i7mc.playerinfo.bungee.PlayerInfoBungee;
import cn.i7mc.playerinfo.bungee.controller.BungeePlayerController;
import cn.i7mc.playerinfo.util.CompressionUtil;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

/**
 * 处理来自Spigot服务器的消息
 */
public class MessageListener implements Listener {
    private final Plugin plugin;
    private final String channel;
    private final Logger logger;
    private final PlayerInfoBungee playerInfoBungee;
    private final BungeePlayerController playerController;
    private final Gson gson = new Gson();
    
    /**
     * 构造一个新的消息监听器
     * 
     * @param plugin BungeeCord插件实例
     * @param channel 消息通道名称
     * @param playerInfoBungee PlayerInfoBungee实例
     * @param playerController 玩家控制器实例
     */
    public MessageListener(Plugin plugin, String channel, PlayerInfoBungee playerInfoBungee, BungeePlayerController playerController) {
        this.plugin = plugin;
        this.channel = channel;
        this.logger = plugin.getLogger();
        this.playerInfoBungee = playerInfoBungee;
        this.playerController = playerController;
        
        // 注册消息通道
        ProxyServer.getInstance().registerChannel(channel);
        
        // 注册事件监听器
        ProxyServer.getInstance().getPluginManager().registerListener(plugin, this);
        
        logger.info("§3已注册消息通道: " + channel);
    }
    
    /**
     * 处理插件消息事件
     */
    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals(channel)) {
            return;
        }
        
        // 获取发送服务器的名称
        String serverName = "unknown";
        if (event.getSender() instanceof Server) {
            Server server = (Server) event.getSender();
            serverName = server.getInfo().getName();
        }
        
        // 读取消息
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            String messageType = in.readUTF();
            
            if (messageType.equals("PlayerData")) {
                // 读取玩家UUID
                String playerUUIDStr = in.readUTF();
                
                // 检查是否是压缩数据
                boolean isCompressed = in.readBoolean();
                
                String playerData;
                
                if (isCompressed) {
                    // 读取压缩数据
                    int compressedSize = in.readInt();
                    byte[] compressedData = new byte[compressedSize];
                    in.readFully(compressedData);
                    
                    // 解压数据
                    byte[] decompressedData = CompressionUtil.decompress(
                        compressedData, 
                        logger, 
                        playerInfoBungee.isDebug()
                    );
                    
                    // 转换为字符串
                    playerData = new String(decompressedData, StandardCharsets.UTF_8);
                } else {
                    // 读取未压缩的数据 (向后兼容)
                    playerData = in.readUTF();
                }
                
                // 处理收到的数据
                processPlayerData(serverName, playerData);
                
                // 尝试从JSON中提取玩家UUID
                try {
                    JsonObject dataObj = gson.fromJson(playerData, JsonObject.class);
                    if (dataObj.has("uuid")) {
                        String uuid = dataObj.get("uuid").getAsString();
                        UUID playerUUID = UUID.fromString(uuid);
                        
                        // 执行玩家服务器数据修正 - 确保玩家只存在于当前服务器中
                        playerController.correctPlayerServerData(playerUUID, serverName);
                    }
                } catch (Exception e) {
                    // 静默处理异常
                }
            } else if (messageType.equals("PlayerRemove")) {
                // 读取玩家UUID
                String playerUUIDStr = in.readUTF();
                
                // 尝试从其他服务器查找此玩家
                for (String otherServer : ProxyServer.getInstance().getServers().keySet()) {
                    if (!otherServer.equals(serverName)) {
                        try {
                            UUID playerUUID = UUID.fromString(playerUUIDStr);
                            Map<UUID, String> serverData = playerController.getServerPlayerData(otherServer);
                            if (serverData != null && serverData.containsKey(playerUUID)) {
                                // 静默处理冲突
                            }
                        } catch (Exception e) {
                            // 静默处理异常
                        }
                    }
                }
                
                // 从控制器中移除玩家
                try {
                    UUID playerUUID = UUID.fromString(playerUUIDStr);
                    playerController.removePlayerData(serverName, playerUUID);
                } catch (IllegalArgumentException e) {
                    // 静默处理异常
                }
            } else if (messageType.equals("ServerInfo")) {
                // 处理服务器信息...
                String serverVersion = in.readUTF();
                int onlinePlayers = in.readInt();
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "处理插件消息时出错: " + e.getMessage(), e);
        }
    }
    
    /**
     * 处理接收到的玩家数据
     */
    private void processPlayerData(String serverName, String playerData) {
        // 调试：检查占位符数据是否存在
        if (playerInfoBungee.isDebug()) {
            try {
                JsonObject dataObj = gson.fromJson(playerData, JsonObject.class);
                if (dataObj.has("placeholders")) {
                    logger.info("接收到来自 " + serverName + " 的占位符数据");
                } else {
                    logger.warning("来自 " + serverName + " 的数据中没有占位符字段");
                    // 打印接收到的数据结构，帮助调试
                    StringBuilder keys = new StringBuilder();
                    for (Map.Entry<String, JsonElement> entry : dataObj.entrySet()) {
                        keys.append(entry.getKey()).append(", ");
                    }
                    logger.info("数据字段: " + keys.toString());
                }
            } catch (Exception e) {
                logger.warning("解析来自 " + serverName + " 的玩家数据时出错: " + e.getMessage());
            }
        }
        
        // 更新数据到玩家控制器
        playerController.updatePlayersData(serverName, playerData);
    }
    
    /**
     * 请求所有Spigot服务器刷新玩家数据
     */
    public void requestDataRefresh() {
        Map<String, ServerInfo> servers = ProxyServer.getInstance().getServers();
        
        for (Map.Entry<String, ServerInfo> entry : servers.entrySet()) {
            ServerInfo server = entry.getValue();
            if (server.getPlayers().size() > 0) {
                try {
                    // 使用DataOutputStream正确格式化消息
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream out = new DataOutputStream(baos);
                    
                    // 写入消息类型
                    out.writeUTF("Refresh");
                    
                    // 发送格式化后的数据
                    byte[] data = baos.toByteArray();
                    server.sendData(channel, data);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "向服务器 " + entry.getKey() + " 发送数据刷新请求失败", e);
                }
            }
        }
    }
    
    /**
     * 取消注册消息通道
     */
    public void unregister() {
        ProxyServer.getInstance().unregisterChannel(channel);
        ProxyServer.getInstance().getPluginManager().unregisterListener(this);
        logger.info("已取消注册消息通道: " + channel);
    }
} 