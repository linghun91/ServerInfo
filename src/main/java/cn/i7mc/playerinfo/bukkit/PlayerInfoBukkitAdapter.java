package cn.i7mc.playerinfo.bukkit;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.logging.Logger;

import cn.i7mc.playerinfo.PlayerInfo;

/**
 * Bukkit/Spigot 平台适配器
 * 负责为 Bukkit/Spigot 环境加载和管理 PlayerInfo 插件
 */
public class PlayerInfoBukkitAdapter {
    
    private static PlayerInfoBukkitAdapter instance;
    
    private final JavaPlugin plugin;
    private final File dataFolder;
    
    // 插件主类实例
    private PlayerInfo playerInfo;
    
    /**
     * 构造函数
     * 
     * @param plugin JavaPlugin 实例
     * @param dataFolder 数据文件夹
     */
    public PlayerInfoBukkitAdapter(Object plugin, File dataFolder) {
        this.plugin = (JavaPlugin) plugin;
        this.dataFolder = dataFolder;
        
        // 保存实例
        instance = this;
    }
    
    /**
     * 默认构造函数，仅供测试使用
     */
    public PlayerInfoBukkitAdapter() {
        this.plugin = null;
        this.dataFolder = new File("plugins/PlayerInfo");
    }
    
    /**
     * 启用插件
     */
    public void enable() {
        instance = this;
        
        plugin.getLogger().info("正在通过适配器启动 Bukkit/Spigot 版本的 PlayerInfo 插件...");
        
        try {
            // 初始化PlayerInfo实例
            playerInfo = new PlayerInfo(plugin);
            
            // 调用PlayerInfo的onPluginEnable方法
            playerInfo.onPluginEnable();
            
            plugin.getLogger().info("PlayerInfo 插件已成功加载");
        } catch (Exception e) {
            plugin.getLogger().severe("启动 PlayerInfo 插件时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 禁用插件
     */
    public void disable() {
        try {
            // 调用PlayerInfo的onPluginDisable方法
            if (playerInfo != null) {
                playerInfo.onPluginDisable();
            }
            
            plugin.getLogger().info("PlayerInfo 插件已成功卸载");
        } catch (Exception e) {
            plugin.getLogger().severe("卸载 PlayerInfo 插件时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 获取当前实例
     * 
     * @return 插件实例
     */
    public static PlayerInfoBukkitAdapter getInstance() {
        return instance;
    }
    
    /**
     * 获取PlayerInfo实例
     * 
     * @return PlayerInfo实例
     */
    public PlayerInfo getPlayerInfo() {
        return playerInfo;
    }
    
    /**
     * 获取数据文件夹
     * 
     * @return 数据文件夹
     */
    public File getPluginDataFolder() {
        return dataFolder;
    }
    
    /**
     * 获取日志记录器
     * 
     * @return 日志记录器
     */
    public Logger getPluginLogger() {
        return plugin.getLogger();
    }
    
    /**
     * 获取插件实例
     * 
     * @return JavaPlugin实例
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }
} 