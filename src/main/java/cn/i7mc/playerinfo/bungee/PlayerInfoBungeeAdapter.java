package cn.i7mc.playerinfo.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import java.io.File;
import java.util.logging.Logger;
import net.md_5.bungee.api.ChatColor;

/**
 * BungeeCord 适配器类
 * 负责为 BungeeCord 环境加载和管理 PlayerInfo 插件
 */
public class PlayerInfoBungeeAdapter {
    
    private static PlayerInfoBungeeAdapter instance;
    
    private final Plugin plugin;
    private final File dataFolder;
    private final Logger logger;
    
    // 插件主类实例
    private PlayerInfoBungee playerInfoBungee;
    
    /**
     * 构造函数
     * 
     * @param plugin Plugin 实例
     * @param dataFolder 数据文件夹
     */
    public PlayerInfoBungeeAdapter(Object plugin, File dataFolder) {
        this.plugin = (Plugin) plugin;
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
    public static PlayerInfoBungeeAdapter getInstance() {
        return instance;
    }
    
    /**
     * 启用插件
     */
    public void enable() {
        logger.info(ChatColor.DARK_AQUA + "正在通过适配器启动 BungeeCord 版本的 PlayerInfo 插件...");
        
        try {
            // 实例化主类
            playerInfoBungee = new PlayerInfoBungee(plugin);
            
            // 调用启用方法
            playerInfoBungee.onPluginEnable();
            
            logger.info(ChatColor.DARK_AQUA + "PlayerInfo BungeeCord 插件已成功加载");
        } catch (Exception e) {
            logger.severe("启动 PlayerInfo BungeeCord 插件时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 禁用插件
     */
    public void disable() {
        if (playerInfoBungee != null) {
            try {
                // 调用禁用方法
                playerInfoBungee.onPluginDisable();
                
                logger.info(ChatColor.DARK_AQUA + "PlayerInfo BungeeCord 插件已成功卸载");
            } catch (Exception e) {
                logger.severe("卸载 PlayerInfo BungeeCord 插件时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 获取Plugin实例
     * 
     * @return Plugin实例
     */
    public Plugin getPlugin() {
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