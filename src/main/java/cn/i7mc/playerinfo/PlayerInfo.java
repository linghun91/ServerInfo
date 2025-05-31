package cn.i7mc.playerinfo;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

import cn.i7mc.playerinfo.auth.AuthController;
import cn.i7mc.playerinfo.controller.PlayerController;
import cn.i7mc.playerinfo.messaging.MessageSender;
import cn.i7mc.playerinfo.model.PlayerData;
import cn.i7mc.playerinfo.web.WebServer;
import cn.i7mc.playerinfo.util.PlaceholderManager;

/**
 * PlayerInfo Bukkit/Spigot 插件主类
 */
public class PlayerInfo {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final File dataFolder;
    
    private PlayerController playerController;
    private WebServer webServer;
    private MessageSender messageSender;
    private AuthController authController;
    private PlaceholderManager placeholderManager;
    
    // 配置项
    private int webServerPort;
    private boolean allowExternalAccess;
    private boolean bungeeCordMode;
    private boolean standaloneWebServer;
    private boolean debug;
    private boolean authEnabled;
    
    // 定时任务
    private BukkitTask dataRefreshTask;
    
    /**
     * 构造函数
     * 
     * @param plugin JavaPlugin 实例
     */
    public PlayerInfo(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataFolder = plugin.getDataFolder();
    }
    
    /**
     * 初始化插件
     */
    public void initialize() {
        // 直接调用onPluginEnable方法，避免代码重复
        onPluginEnable();
    }
    
    /**
     * 插件启用时调用
     */
    public void onPluginEnable() {
        // 提取默认配置文件
        File configFile = new File(dataFolder, "config.yml");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        if (!configFile.exists()) {
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("已创建默认配置文件 config.yml");
                } else {
                    logger.warning("无法在插件JAR中找到默认配置文件 config.yml");
                }
            } catch (Exception e) {
                logger.severe("提取默认配置文件时出错: " + e.getMessage());
            }
        }
        
        // 提取认证系统配置文件
        File passwdFile = new File(dataFolder, "passwd.yml");
        if (!passwdFile.exists()) {
            try (InputStream in = plugin.getResource("passwd.yml.template")) {
                if (in != null) {
                    Files.copy(in, passwdFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("已创建认证系统配置文件 passwd.yml");
                } else {
                    logger.warning("无法在插件JAR中找到认证系统配置模板文件 passwd.yml.template");
                }
            } catch (Exception e) {
                logger.severe("提取认证系统配置文件时出错: " + e.getMessage());
            }
        }
        
        // 加载配置
        loadConfig();
        
        // 初始化占位符管理器
        try {
            logger.info("[占位符调试] 开始初始化占位符管理器...");
            placeholderManager = new PlaceholderManager(this);
            
            // 验证初始化结果
            if (placeholderManager != null) {
                boolean papiAvailable = placeholderManager.isPlaceholderAPIAvailable();
                logger.info("[占位符调试] 占位符管理器初始化完成，PlaceholderAPI可用状态: " + papiAvailable);
                
                // 如果无法使用PlaceholderAPI，输出更明确的警告
                if (!papiAvailable) {
                    logger.warning("[占位符调试] 警告: PlaceholderAPI不可用，自定义占位符功能将无法正常工作！");
                    logger.warning("[占位符调试] 请确保服务器已安装并启用PlaceholderAPI插件!");
                } else {
                    logger.info("[占位符调试] 加载完成，准备接收玩家占位符数据");
                }
            } else {
                logger.severe("[占位符调试] 严重错误: 占位符管理器初始化后仍为null");
            }
        } catch (Exception e) {
            logger.severe("[占位符调试] 初始化占位符管理器时出现异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        // 初始化 PlayerController
        playerController = new PlayerController(this);
        
        // 检查 BungeeCord 模式
        if (bungeeCordMode) {
            logger.info("BungeeCord 模式已启用");
            
            // 注册 BungeeCord 消息通道
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
            
            // 初始化消息发送器
            messageSender = new MessageSender(this);
        } else {
            logger.info("运行在独立 Spigot 服务器模式");
        }
        
        // 初始化认证控制器
        if (authEnabled) {
            authController = new AuthController(plugin.getDataFolder());
            logger.info("已启用Web认证系统");
        }
        
        // 如果启用了独立 Web 服务器，提取并启动
        if (standaloneWebServer) {
            extractWebResources();
            
            // 启动 Web 服务器 - 使用正确的构造器
            try {
                webServer = new WebServer(webServerPort, playerController, this);
                webServer.start();
                
                logger.info("Web 服务器已启动，端口: " + webServerPort + 
                            (allowExternalAccess ? " (允许外部访问)" : " (仅限本地访问)"));
            } catch (IOException e) {
                logger.severe("启动Web服务器时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // 启动数据刷新任务
        startDataRefreshTask();
        
        logger.info("PlayerInfo 插件已成功启用");
    }
    
    /**
     * 插件禁用时调用
     */
    public void onPluginDisable() {
        // 停止数据刷新任务
        if (dataRefreshTask != null) {
            dataRefreshTask.cancel();
        }
        
        // 停止Web服务器
        if (webServer != null) {
            webServer.stop();
            logger.info("Web服务器已停止");
        }
        
        // 卸载消息发送器
        if (messageSender != null) {
            messageSender.unregister();
        }
        
        logger.info("PlayerInfo 插件已成功卸载!");
    }
    
    /**
     * 加载配置文件
     */
    private void loadConfig() {
        webServerPort = plugin.getConfig().getInt("web-server.port", 25581);
        allowExternalAccess = plugin.getConfig().getBoolean("web-server.allow-external-access", false);
        bungeeCordMode = plugin.getConfig().getBoolean("bungeecord.enabled", false);
        standaloneWebServer = plugin.getConfig().getBoolean("standalone.web-server", false);
        debug = plugin.getConfig().getBoolean("debug", false);
        
        // 加载认证设置
        authEnabled = plugin.getConfig().getBoolean("authentication.enabled", true);
        
        if (debug) {
            logger.info("配置加载完成:");
            logger.info("- Web服务器端口: " + webServerPort);
            logger.info("- 允许外部访问: " + allowExternalAccess);
            logger.info("- BungeeCord模式: " + bungeeCordMode);
            logger.info("- 独立Web服务器: " + standaloneWebServer);
            logger.info("- 调试模式: " + debug);
            logger.info("- 认证系统: " + (authEnabled ? "已启用" : "未启用"));
        }
    }
    
    /**
     * 提取Web资源文件
     */
    private void extractWebResources() {
        File webRoot = new File(dataFolder, "web");
        if (!webRoot.exists() && !webRoot.mkdirs()) {
            logger.severe("无法创建Web资源文件夹!");
            return;
        }
        
        // 提取index.html
        extractResource("web/index.html", new File(webRoot, "index.html"));
        
        // 提取物品图标和翻译配置文件
        extractResource("web/itemIcons.cnf", new File(webRoot, "itemIcons.cnf"));
        extractResource("web/itemTranslations.cnf", new File(webRoot, "itemTranslations.cnf"));
        
        // 提取CSS和JS文件
        File cssDir = new File(webRoot, "css");
        File jsDir = new File(webRoot, "js");
        
        if (!cssDir.exists() && !cssDir.mkdirs()) {
            logger.severe("无法创建CSS文件夹!");
        }
        if (!jsDir.exists() && !jsDir.mkdirs()) {
            logger.severe("无法创建JS文件夹!");
        }
        
        extractResource("web/css/style.css", new File(cssDir, "style.css"));
        extractResource("web/js/app.js", new File(jsDir, "app.js"));
        extractResource("web/js/skinViewer.js", new File(jsDir, "skinViewer.js"));
        
        // 提取默认皮肤图片
        extractResource("web/DefaultSkin.png", new File(webRoot, "DefaultSkin.png"));
        
        // 提取namemap.cnf文件
        extractResource("web/namemap.cnf", new File(webRoot, "namemap.cnf"));
        
        logger.info("Web资源文件已提取完成");
    }
    
    /**
     * 提取资源文件
     * 
     * @param resourcePath 资源路径
     * @param destFile 目标文件
     */
    private void extractResource(String resourcePath, File destFile) {
        if (!destFile.exists()) {
            try (InputStream in = plugin.getResource(resourcePath)) {
                if (in != null) {
                    Files.copy(in, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    if (debug) {
                        logger.info("已提取资源: " + resourcePath);
                    }
                } else {
                    logger.warning("无法在插件JAR中找到资源: " + resourcePath);
                }
            } catch (Exception e) {
                logger.severe("提取资源文件时出错: " + e.getMessage());
            }
        }
    }
    
    /**
     * 启动数据刷新任务
     */
    private void startDataRefreshTask() {
        int refreshInterval = plugin.getConfig().getInt("refresh-interval", 5) * 20; // 转换为tick
        
        dataRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            playerController.refreshData();
            
            // 如果启用了BungeeCord模式，则发送数据到BungeeCord
            if (bungeeCordMode && messageSender != null) {
                // 遍历在线玩家，发送每个玩家的数据
                for (Player player : Bukkit.getOnlinePlayers()) {
                    messageSender.sendPlayerData(player, playerController.getPlayerData(player));
                }
            }
            
            if (debug) {
                logger.info("已刷新玩家数据");
            }
        }, 20L, refreshInterval);
    }
    
    /**
     * 获取外部访问设置
     * 
     * @return 是否允许外部访问
     */
    public boolean isExternalAccessAllowed() {
        return allowExternalAccess;
    }
    
    /**
     * 获取认证控制器
     * 
     * @return 认证控制器实例
     */
    public AuthController getAuthController() {
        return authController;
    }
    
    /**
     * 检查是否开启调试模式
     * 
     * @return 是否开启调试模式
     */
    public boolean isDebug() {
        return debug;
    }
    
    /**
     * 获取MessageSender实例
     * 
     * @return MessageSender实例，如果未启用BungeeCord模式则返回null
     */
    public MessageSender getMessageSender() {
        return messageSender;
    }
    
    /**
     * 获取玩家控制器
     * 
     * @return 玩家控制器
     */
    public PlayerController getPlayerController() {
        return playerController;
    }
    
    /**
     * 获取Web服务器
     * 
     * @return Web服务器
     */
    public WebServer getWebServer() {
        return webServer;
    }
    
    /**
     * 获取插件实例
     * 
     * @return 插件实例
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }
    
    /**
     * 重新加载插件
     */
    public void reload() {
        logger.info("正在重新加载 PlayerInfo 插件...");
        
        // 停止数据刷新任务
        if (dataRefreshTask != null) {
            dataRefreshTask.cancel();
            dataRefreshTask = null;
        }
        
        // 关闭Web服务器
        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
        
        // 重新加载配置
        plugin.reloadConfig();
        loadConfig();
        
        // 重新初始化各组件
        
        // 重新初始化PlayerController
        playerController = new PlayerController(this);
        
        // 检查BungeeCord模式
        if (bungeeCordMode) {
            logger.info("BungeeCord 模式已启用");
            
            // 注册BungeeCord消息通道
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
            
            // 重新初始化消息发送器
            messageSender = new MessageSender(this);
        } else {
            logger.info("运行在独立 Spigot 服务器模式");
        }
        
        // 重新初始化占位符管理器
        if (placeholderManager != null) {
            placeholderManager.reloadConfig();
            logger.info("已重新加载占位符配置");
        } else {
            placeholderManager = new PlaceholderManager(this);
            logger.info("已初始化占位符管理器");
        }
        
        // 重新初始化认证控制器
        if (authEnabled) {
            if (authController != null) {
                authController.reload();
            } else {
                authController = new AuthController(plugin.getDataFolder());
            }
            logger.info("已重新加载Web认证系统");
        }
        
        // 如果启用了独立Web服务器，重新启动
        if (standaloneWebServer) {
            extractWebResources();
            
            // 启动/重启 Web 服务器 - 使用正确的构造器
            if (webServer != null) {
                webServer.stop();
            }
            
            try {
                webServer = new WebServer(webServerPort, playerController, this);
                webServer.start();
                
                logger.info("Web 服务器已重启，端口: " + webServerPort + 
                           (allowExternalAccess ? " (允许外部访问)" : " (仅限本地访问)"));
            } catch (IOException e) {
                logger.severe("重启Web服务器时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // 重启数据刷新任务
        startDataRefreshTask();
        
        logger.info("PlayerInfo 插件已成功重新加载");
    }
    
    /**
     * 重新加载占位符配置
     */
    public void reloadPlaceholders() {
        if (placeholderManager != null) {
            placeholderManager.reloadConfig();
            logger.info("已重新加载占位符配置");
        } else {
            placeholderManager = new PlaceholderManager(this);
            logger.info("已初始化占位符管理器");
        }
    }
    
    /**
     * 获取PlaceholderManager实例
     */
    public PlaceholderManager getPlaceholderManager() {
        return placeholderManager;
    }
    
    // 添加代理方法，将调用传递给内部的plugin实例
    
    /**
     * 获取服务器实例
     * 
     * @return 服务器实例
     */
    public Server getServer() {
        return plugin.getServer();
    }
    
    /**
     * 获取配置文件
     * 
     * @return 配置文件
     */
    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }
    
    /**
     * 获取日志记录器
     * 
     * @return 日志记录器
     */
    public Logger getLogger() {
        return logger;
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
     * 获取插件描述
     * 
     * @return 插件描述
     */
    public org.bukkit.plugin.PluginDescriptionFile getDescription() {
        return plugin.getDescription();
    }
    
    /**
     * 从插件资源中获取资源
     * 
     * @param fileName 文件名
     * @return 资源流
     */
    public InputStream getResource(String fileName) {
        return plugin.getResource(fileName);
    }
} 