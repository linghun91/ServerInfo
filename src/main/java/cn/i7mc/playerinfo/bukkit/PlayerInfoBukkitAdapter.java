package cn.i7mc.playerinfo.bukkit;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.logging.Logger;

import cn.i7mc.playerinfo.PlayerInfo;

/**
 * Bukkit/Spigot 适配器类
 * 负责为 Bukkit/Spigot 环境加载和管理 PlayerInfo 插件
 */
public class PlayerInfoBukkitAdapter {
    
    private static PlayerInfoBukkitAdapter instance;
    
    private final JavaPlugin plugin;
    private final File dataFolder;
    private final Logger logger;
    
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
        this.logger = this.plugin.getLogger();
        
        // 保存实例
        instance = this;
    }
    
    /**
     * 获取适配器实例
     * 
     * @return 适配器实例
     */
    public static PlayerInfoBukkitAdapter getInstance() {
        return instance;
    }
    
    /**
     * 启用插件
     */
    public void enable() {
        logger.info("§3正在通过适配器启动 Bukkit/Spigot 版本的 PlayerInfo 插件...");
        
        try {
            // 实例化主类
            playerInfo = new PlayerInfo(plugin);
            
            // 调用启用方法
            playerInfo.onPluginEnable();
            
            logger.info("§3PlayerInfo 插件已成功加载");
        } catch (Exception e) {
            logger.severe("启动 PlayerInfo 插件时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 禁用插件
     */
    public void disable() {
        if (playerInfo != null) {
            try {
                // 调用禁用方法
                playerInfo.onPluginDisable();
                
                logger.info("§3PlayerInfo 插件已成功卸载");
            } catch (Exception e) {
                logger.severe("卸载 PlayerInfo 插件时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 获取JavaPlugin实例
     * 
     * @return JavaPlugin实例
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }
    
    /**
     * 获取数据文件夹
     * 
     * @return 数据文件夹
     */
    public File getDataFolder() {
        return dataFolder;
    }
    
    /**
     * 获取日志记录器
     * 
     * @return 日志记录器
     */
    public Logger getLogger() {
        return logger;
    }
} 