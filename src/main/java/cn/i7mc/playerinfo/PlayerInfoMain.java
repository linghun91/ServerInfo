package cn.i7mc.playerinfo;

import java.util.logging.Logger;

/**
 * 统一入口类，支持在BungeeCord和Bukkit/Spigot环境中自动检测并加载适当的实现
 * 这个类将被同时指定为plugin.yml和bungee.yml中的主类
 */
public class PlayerInfoMain {
    private static final Logger LOGGER = Logger.getLogger("PlayerInfo");
    
    static {
        try {
            // 尝试检测BungeeCord环境
            Class.forName("net.md_5.bungee.api.plugin.Plugin");
            LOGGER.info("§3检测到BungeeCord环境，正在初始化BungeeCord版本...");
            // 动态加载BungeeCord适配器类
            Class.forName("cn.i7mc.playerinfo.bungee.PlayerInfoBungeeAdapter");
            LOGGER.info("§3已加载BungeeCord适配器");
        } catch (ClassNotFoundException e) {
            try {
                // BungeeCord环境检测失败，尝试检测Bukkit环境
                Class.forName("org.bukkit.plugin.java.JavaPlugin");
                LOGGER.info("§3检测到Bukkit/Spigot环境，正在初始化Bukkit版本...");
                // 动态加载Bukkit适配器类
                Class.forName("cn.i7mc.playerinfo.bukkit.PlayerInfoBukkitAdapter");
                LOGGER.info("§3已加载Bukkit适配器");
            } catch (ClassNotFoundException ex) {
                // 两种环境都检测失败
                LOGGER.severe("无法检测到支持的服务器环境(BungeeCord或Bukkit/Spigot)！");
                LOGGER.severe("插件无法启动！");
            }
        }
    }
} 