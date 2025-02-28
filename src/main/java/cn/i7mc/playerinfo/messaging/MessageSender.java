package cn.i7mc.playerinfo.messaging;

import cn.i7mc.playerinfo.PlayerInfo;
import cn.i7mc.playerinfo.model.PlayerData;
import cn.i7mc.playerinfo.util.CompressionUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 消息发送器，用于向BungeeCord发送玩家数据
 */
public class MessageSender {
    private final PlayerInfo playerInfo;
    private final Plugin plugin; // 实际的Plugin实例
    private final String channelName;
    private final Gson gson;
    private boolean registered = false;
    
    /**
     * 构造函数
     * 
     * @param playerInfo PlayerInfo插件实例
     */
    public MessageSender(PlayerInfo playerInfo) {
        this.playerInfo = playerInfo;
        this.plugin = playerInfo.getPlugin(); // 获取JavaPlugin实例
        this.channelName = playerInfo.getConfig().getString("messaging.channel", "playerinfo:channel");
        
        // 使用更安全的GSON配置，防止循环引用
        this.gson = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setLenient()
            .enableComplexMapKeySerialization()   // 启用复杂Map键序列化
            .create();
    }
    
    /**
     * 注册消息通道
     */
    public void register() {
        if (!registered) {
            playerInfo.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channelName);
            registered = true;
            playerInfo.getLogger().info("§3已注册BungeeCord消息通道: " + channelName);
        }
    }
    
    /**
     * 取消注册消息通道
     */
    public void unregister() {
        if (registered) {
            playerInfo.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, channelName);
            registered = false;
        }
    }
    
    /**
     * 发送玩家数据到BungeeCord
     * 
     * @param player 玩家
     * @param playerData 玩家数据
     */
    public void sendPlayerData(Player player, PlayerData playerData) {
        if (!playerInfo.getConfig().getBoolean("bungeecord.enabled", true)) {
            return;
        }
        
        if (!registered) {
            register();
        }
        
        try {
            // 使用ItemStackSerializer先转换为Map，再序列化
            Map<String, Object> serializedData = cn.i7mc.playerinfo.util.ItemStackSerializer.serialize(playerData);
            String jsonData = gson.toJson(serializedData);
            
            // 将JSON字符串转换为字节数组
            byte[] rawData = jsonData.getBytes(StandardCharsets.UTF_8);
            
            // 检查原始数据大小，记录日志
            boolean debug = playerInfo.getConfig().getBoolean("debug", false);
            if (debug) {
                playerInfo.getLogger().info("玩家数据原始大小: " + rawData.length + " 字节");
            }
            
            // 如果原始数据接近或超过限制，进行压缩
            if (rawData.length > 30000) { // 预留一些空间给头信息
                // 压缩数据
                byte[] compressedData = CompressionUtil.compress(
                    rawData, 
                    playerInfo.getLogger(), 
                    debug
                );
                
                // 检查压缩后的大小是否仍然超过限制
                if (compressedData.length > 32000) { // 预留头信息空间
                    playerInfo.getLogger().severe(String.format(
                        "压缩后的玩家数据仍然超过限制 (%d > 32000 字节)，无法发送!",
                        compressedData.length
                    ));
                    return;
                }
                
                // 发送压缩数据
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(stream);
                
                // 写入子通道名称
                out.writeUTF("PlayerData");
                
                // 写入玩家UUID
                out.writeUTF(player.getUniqueId().toString());
                
                // 标记这是压缩数据
                out.writeBoolean(true); // 压缩标志
                
                // 写入压缩数据
                out.writeInt(compressedData.length);
                out.write(compressedData);
                
                // 发送数据
                player.sendPluginMessage(plugin, channelName, stream.toByteArray());
                
                if (debug) {
                    playerInfo.getLogger().info("已发送压缩的玩家数据: " + player.getName());
                }
            } else {
                // 原始数据不大，直接发送未压缩数据（保持向后兼容）
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(stream);
                
                // 写入子通道名称
                out.writeUTF("PlayerData");
                
                // 写入玩家UUID
                out.writeUTF(player.getUniqueId().toString());
                
                // 标记这是未压缩数据
                out.writeBoolean(false); // 压缩标志
                
                // 写入JSON数据
                out.writeUTF(jsonData);
                
                // 发送数据
                player.sendPluginMessage(plugin, channelName, stream.toByteArray());
                
                if (debug) {
                    playerInfo.getLogger().info("已发送未压缩的玩家数据: " + player.getName());
                }
            }
        } catch (IOException e) {
            playerInfo.getLogger().warning("发送玩家数据时出错: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            // 捕获任何序列化相关的错误
            playerInfo.getLogger().severe("序列化玩家数据时出错: " + e.getMessage());
            if (playerInfo.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 发送玩家移除消息到BungeeCord
     * 
     * @param player 玩家
     */
    public void sendPlayerRemove(Player player) {
        if (!playerInfo.getConfig().getBoolean("bungeecord.enabled", true)) {
            return;
        }
        
        if (!registered) {
            register();
        }
        
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(stream);
            
            // 写入子通道名称
            out.writeUTF("PlayerRemove");
            
            // 写入玩家UUID
            out.writeUTF(player.getUniqueId().toString());
            
            // 发送数据
            player.sendPluginMessage(plugin, channelName, stream.toByteArray());
            
            if (playerInfo.getConfig().getBoolean("debug", false)) {
                playerInfo.getLogger().info("已发送玩家 " + player.getName() + " 的移除消息到BungeeCord");
            }
        } catch (IOException e) {
            playerInfo.getLogger().warning("发送玩家移除消息时出错: " + e.getMessage());
        }
    }
    
    /**
     * 发送服务器信息到BungeeCord
     */
    public void sendServerInfo() {
        if (!playerInfo.getConfig().getBoolean("bungeecord.enabled", true)) {
            return;
        }
        
        if (!registered) {
            register();
        }
        
        if (playerInfo.getServer().getOnlinePlayers().isEmpty()) {
            return; // 没有在线玩家，无法发送消息
        }
        
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(stream);
            
            // 写入子通道名称
            out.writeUTF("ServerInfo");
            
            // 写入服务器版本和在线玩家数
            out.writeUTF(playerInfo.getServer().getVersion());
            out.writeInt(playerInfo.getServer().getOnlinePlayers().size());
            
            // 发送数据（通过任意一个在线玩家）
            Player player = playerInfo.getServer().getOnlinePlayers().iterator().next();
            player.sendPluginMessage(plugin, channelName, stream.toByteArray());
            
            if (playerInfo.getConfig().getBoolean("debug", false)) {
                playerInfo.getLogger().info("已发送服务器信息到BungeeCord");
            }
        } catch (IOException e) {
            playerInfo.getLogger().warning("发送服务器信息时出错: " + e.getMessage());
        }
    }
    
    /**
     * 获取消息通道名称
     * 
     * @return 通道名称
     */
    public String getChannelName() {
        return channelName;
    }
} 