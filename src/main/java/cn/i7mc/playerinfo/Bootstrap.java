package cn.i7mc.playerinfo;

import java.util.logging.Logger;
import java.lang.reflect.Constructor;
import java.io.File;
import java.util.logging.Level;
import net.md_5.bungee.api.ChatColor;

/**
 * PlayerInfo插件的引导类
 * 用于检测环境并加载适当的插件主类
 */
public class Bootstrap {
    
    private static final String BUKKIT_CLASS = "org.bukkit.plugin.java.JavaPlugin";
    private static final String BUNGEE_CLASS = "net.md_5.bungee.api.plugin.Plugin";
    
    private static final String BUKKIT_ADAPTER = "cn.i7mc.playerinfo.bukkit.PlayerInfoBukkitAdapter";
    private static final String BUNGEE_ADAPTER = "cn.i7mc.playerinfo.bungee.PlayerInfoBungeeAdapter";
    
    private static final String BUKKIT_PLUGIN = "cn.i7mc.playerinfo.plugin.BukkitPlugin";
    private static final String BUNGEE_PLUGIN = "cn.i7mc.playerinfo.plugin.BungeePlugin";
    
    private static Logger logger;
    private static ClassLoader classLoader;
    private static Object pluginInstance;
    
    /**
     * 初始化插件
     * 检测环境并加载适当的适配器
     * 
     * @param plugin 插件实例（BungeeCord或Bukkit）
     * @param dataFolder 数据文件夹
     * @param logger 日志记录器
     * @return 是否初始化成功
     */
    public static boolean init(Object plugin, File dataFolder, Logger logger) {
        Bootstrap.logger = logger;
        Bootstrap.classLoader = plugin.getClass().getClassLoader();
        Bootstrap.pluginInstance = plugin;
        
        logger.info("§3PlayerInfo 引导程序启动...");
        logger.info("§3检测运行环境...");
        
        boolean isBukkit = isClassAvailable(BUKKIT_CLASS);
        boolean isBungee = isClassAvailable(BUNGEE_CLASS);
        
        if (isBukkit && !isBungee) {
            logger.info("§3检测到 Bukkit/Spigot 环境");
            return loadAdapter(BUKKIT_ADAPTER, plugin, dataFolder);
        } else if (isBungee && !isBukkit) {
            logger.info("§3检测到 BungeeCord 环境");
            return loadAdapter(BUNGEE_ADAPTER, plugin, dataFolder);
        } else if (isBukkit && isBungee) {
            logger.warning("检测到 Bukkit 和 BungeeCord 环境都存在，这是不寻常的");
            logger.warning("尝试通过插件实例类型确定环境");
            
            // 检查插件实例类型
            if (plugin.getClass().getName().contains("bungee")) {
                logger.info("§3基于插件类型判断为 BungeeCord 环境");
                return loadAdapter(BUNGEE_ADAPTER, plugin, dataFolder);
            } else {
                logger.info("§3基于插件类型判断为 Bukkit/Spigot 环境");
                return loadAdapter(BUKKIT_ADAPTER, plugin, dataFolder);
            }
        } else {
            logger.severe("无法检测到 Bukkit 或 BungeeCord 环境");
            logger.severe("PlayerInfo 插件无法运行，请检查服务器环境");
            return false;
        }
    }
    
    /**
     * 检查类是否可用
     * 
     * @param className 类名
     * @return 是否可用
     */
    private static boolean isClassAvailable(String className) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * 加载适配器类
     * 
     * @param adapterClassName 适配器类名
     * @param plugin 插件实例
     * @param dataFolder 数据文件夹
     * @return 是否加载成功
     */
    private static boolean loadAdapter(String adapterClassName, Object plugin, File dataFolder) {
        try {
            Class<?> adapterClass = Class.forName(adapterClassName, true, classLoader);
            Constructor<?> constructor = adapterClass.getConstructor(Object.class, File.class);
            Object adapter = constructor.newInstance(plugin, dataFolder);
            
            logger.info("§3成功加载适配器: " + adapterClassName);
            
            // 调用适配器的enable方法
            try {
                adapterClass.getMethod("enable").invoke(adapter);
                logger.info("§3插件启动成功");
                return true;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "调用适配器enable方法失败", e);
                return false;
            }
        } catch (ClassNotFoundException e) {
            logger.severe("找不到适配器类: " + adapterClassName);
            logger.severe("请确保插件JAR文件完整");
        } catch (NoSuchMethodException e) {
            logger.severe("适配器缺少必要的构造函数或方法: " + e.getMessage());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "加载适配器时出错", e);
        }
        
        return false;
    }
    
    /**
     * 关闭插件
     */
    public static void shutdown() {
        if (pluginInstance != null) {
            logger.info("PlayerInfo 插件正在关闭...");
            
            try {
                // 尝试获取适配器实例并调用disable方法
                Class<?> adapterClass;
                if (isClassAvailable(BUKKIT_CLASS)) {
                    adapterClass = Class.forName(BUKKIT_ADAPTER, true, classLoader);
                } else {
                    adapterClass = Class.forName(BUNGEE_ADAPTER, true, classLoader);
                }
                
                // 查找适配器实例的静态方法getInstance
                java.lang.reflect.Method getInstanceMethod = adapterClass.getMethod("getInstance");
                Object adapterInstance = getInstanceMethod.invoke(null);
                
                if (adapterInstance != null) {
                    // 调用disable方法
                    adapterClass.getMethod("disable").invoke(adapterInstance);
                    logger.info("PlayerInfo 插件已成功关闭");
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "关闭插件时出错", e);
            }
        }
    }
} 