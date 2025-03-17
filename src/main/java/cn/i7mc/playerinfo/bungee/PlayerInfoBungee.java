package cn.i7mc.playerinfo.bungee;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.TaskScheduler;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.api.ChatColor;

import cn.i7mc.playerinfo.bungee.web.BungeeWebServer;
import cn.i7mc.playerinfo.bungee.controller.BungeePlayerController;
import cn.i7mc.playerinfo.bungee.command.PlayerInfoCommand;
import cn.i7mc.playerinfo.bungee.messaging.MessageListener;
import cn.i7mc.playerinfo.auth.AuthController;
import cn.i7mc.playerinfo.auth.PasswordHash;

/**
 * PlayerInfo BungeeCord插件主类
 */
public class PlayerInfoBungee {
    private final Plugin plugin;
    private final Logger logger;
    private final File dataFolder;
    
    private Configuration config;
    private BungeePlayerController playerController;
    private BungeeWebServer webServer;
    private MessageListener messageListener;
    private AuthController authController;
    
    // 配置项
    private int webServerPort;
    private boolean allowExternalAccess;
    private boolean debug;
    private String messageChannel;
    private boolean authEnabled;
    
    // 清理任务ID
    private int cleanupTaskId = -1;
    private int refreshTaskId = -1;
    
    /**
     * 构造函数
     * 
     * @param plugin Plugin实例
     */
    public PlayerInfoBungee(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataFolder = plugin.getDataFolder();
    }
    
    /**
     * 插件启用时调用
     */
    public void onPluginEnable() {
        // 创建插件数据文件夹
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.severe("无法创建插件数据文件夹!");
            return;
        }
        
        // 加载配置
        loadConfig();
        
        // 输出关键配置信息
        logger.info(ChatColor.DARK_AQUA + "======== PlayerInfo BungeeCord 配置信息 ========");
        logger.info(ChatColor.DARK_AQUA + "Web服务器端口: " + webServerPort);
        logger.info(ChatColor.DARK_AQUA + "允许外部访问: " + allowExternalAccess);
        logger.info(ChatColor.DARK_AQUA + "认证系统: " + (authEnabled ? "已启用" : "未启用"));
        logger.info(ChatColor.DARK_AQUA + "消息通道: " + messageChannel);
        logger.info(ChatColor.DARK_AQUA + "调试模式: " + debug);
        logger.info(ChatColor.DARK_AQUA + "==============================================");
        
        // 初始化玩家控制器
        playerController = new BungeePlayerController(this);
        
        // 初始化认证控制器（如果启用）
        if (authEnabled) {
            try {
                logger.info(ChatColor.DARK_AQUA + "正在初始化认证系统...");
                logger.info(ChatColor.DARK_AQUA + "插件数据目录: " + dataFolder.getAbsolutePath());
                
                File passwdFile = new File(dataFolder, "passwd.yml");
                logger.info(ChatColor.DARK_AQUA + "预期的passwd.yml路径: " + passwdFile.getAbsolutePath());
                
                // 如果passwd.yml已存在，输出信息
                if (passwdFile.exists()) {
                    logger.info(ChatColor.DARK_AQUA + "passwd.yml文件已存在，将使用现有文件");
                } else {
                    logger.info(ChatColor.DARK_AQUA + "passwd.yml文件不存在，将在初始化时创建");
                }
                
                authController = new AuthController(dataFolder);
                logger.info(ChatColor.DARK_AQUA + "认证系统已成功初始化");
                
                // 再次检查passwd.yml是否已创建
                if (passwdFile.exists()) {
                    logger.info(ChatColor.DARK_AQUA + "passwd.yml文件已成功创建/存在: " + passwdFile.getAbsolutePath());
                    logger.info(ChatColor.DARK_AQUA + "文件大小: " + passwdFile.length() + " 字节");
                } else {
                    logger.severe("【严重错误】初始化后passwd.yml文件仍未创建: " + passwdFile.getAbsolutePath());
                    logger.severe("将尝试手动创建passwd.yml文件...");
                    
                    // 手动创建passwd.yml文件
                    try {
                        String defaultConfig = 
                            "# ServerInfo登录验证系统配置文件\n" +
                            "# 该文件在插件首次启动时自动生成\n\n" +
                            "authentication:\n" +
                            "  enabled: true\n" +
                            "  session-timeout: 1440\n" +
                            "  max-login-attempts: 5\n" +
                            "  lockout-duration: 30\n" +
                            "users:\n" +
                            "- username: admin\n" +
                            "  password: admin123\n" +
                            "  permission: admin\n" +
                            "- username: viewer\n" +
                            "  password: view123\n" +
                            "  permission: view\n";
                            
                        java.io.FileWriter writer = new java.io.FileWriter(passwdFile);
                        writer.write(defaultConfig);
                        writer.close();
                        
                        // 再次检查文件是否创建成功
                        if (passwdFile.exists()) {
                            logger.info(ChatColor.DARK_AQUA + "手动创建passwd.yml成功: " + passwdFile.getAbsolutePath());
                        } else {
                            logger.severe("手动创建passwd.yml失败，文件仍不存在!");
                        }
                    } catch (Exception e2) {
                        logger.severe("手动创建passwd.yml失败: " + e2.getMessage());
                        e2.printStackTrace();
                    }
                }
            } catch (Exception e) {
                logger.severe("初始化认证系统失败: " + e.getMessage());
                e.printStackTrace();
                
                // 尝试直接创建passwd.yml
                try {
                    logger.info(ChatColor.DARK_AQUA + "尝试手动创建passwd.yml文件...");
                    File passwdFile = new File(dataFolder, "passwd.yml");
                    
                    String defaultConfig = 
                        "# ServerInfo登录验证系统配置文件\n" +
                        "# 该文件在插件首次启动时自动生成\n\n" +
                        "authentication:\n" +
                        "  enabled: true\n" +
                        "  session-timeout: 1440\n" +
                        "  max-login-attempts: 5\n" +
                        "  lockout-duration: 30\n" +
                        "users:\n" +
                        "- username: admin\n" +
                        "  password: admin123\n" +
                        "  permission: admin\n" +
                        "- username: viewer\n" +
                        "  password: view123\n" +
                        "  permission: view\n";
                        
                    java.io.FileWriter writer = new java.io.FileWriter(passwdFile);
                    writer.write(defaultConfig);
                    writer.close();
                    
                    logger.info(ChatColor.DARK_AQUA + "手动创建passwd.yml成功: " + passwdFile.getAbsolutePath());
                } catch (Exception e2) {
                    logger.severe("手动创建passwd.yml失败: " + e2.getMessage());
                    e2.printStackTrace();
                }
            }
        } else {
            logger.warning("【警告】认证系统未启用! 请在config.yml中设置authentication.enabled=true以启用认证");
        }
        
        // 初始化消息监听器
        messageListener = new MessageListener(plugin, messageChannel, this, playerController);
        
        // 注册命令
        plugin.getProxy().getPluginManager().registerCommand(
            plugin, new PlayerInfoCommand(this)
        );
        
        // 提取Web资源
        extractWebResources();
        
        // 启动Web服务器
        startWebServer();
        
        // 启动数据清理任务
        startCleanupTask();
        
        // 启动数据刷新请求任务
        startRefreshTask();
        
        logger.info(ChatColor.DARK_AQUA + "PlayerInfo BungeeCord 插件已成功启动!");
    }
    
    /**
     * 插件禁用时调用
     */
    public void onPluginDisable() {
        // 取消清理任务
        if (cleanupTaskId != -1) {
            plugin.getProxy().getScheduler().cancel(cleanupTaskId);
        }
        
        // 取消刷新任务
        if (refreshTaskId != -1) {
            plugin.getProxy().getScheduler().cancel(refreshTaskId);
        }
        
        // 停止Web服务器
        if (webServer != null) {
            webServer.stop();
            logger.info(ChatColor.DARK_AQUA + "Web服务器已停止");
        }
        
        // 取消消息监听器
        if (messageListener != null) {
            messageListener.unregister();
        }
        
        logger.info(ChatColor.DARK_AQUA + "PlayerInfo BungeeCord 插件已成功卸载!");
    }
    
    /**
     * 加载配置文件
     */
    private void loadConfig() {
        try {
            // 保存默认配置
            if (!new File(dataFolder, "config.yml").exists()) {
                try (InputStream in = plugin.getResourceAsStream("bungee_config.yml")) {
                    if (in != null) {
                        Files.copy(in, new File(dataFolder, "config.yml").toPath());
                    } else {
                        logger.warning("找不到默认配置文件 bungee_config.yml，将创建一个空配置");
                        createDefaultConfig();
                    }
                }
            }
            
            // 加载配置
            config = ConfigurationProvider.getProvider(YamlConfiguration.class)
                .load(new File(dataFolder, "config.yml"));
            
            // 读取配置项
            webServerPort = config.getInt("web-server.port", 25581);
            allowExternalAccess = config.getBoolean("web-server.allow-external-access", true);
            debug = config.getBoolean("debug", false);
            messageChannel = config.getString("messaging.channel", "playerinfo:channel");
            authEnabled = config.getBoolean("authentication.enabled", true);
            
            logger.info(ChatColor.DARK_AQUA + "配置已加载: Web服务器端口 = " + webServerPort + 
                       ", 允许外部访问 = " + allowExternalAccess + 
                       ", 认证系统 = " + (authEnabled ? "已启用" : "未启用"));
            
        } catch (IOException e) {
            logger.severe("加载配置文件时出错: " + e.getMessage());
            
            // 使用默认值
            webServerPort = 25581;
            allowExternalAccess = true;
            debug = false;
            messageChannel = "playerinfo:channel";
            authEnabled = true;
        }
    }
    
    /**
     * 创建默认配置文件
     */
    private void createDefaultConfig() {
        try {
            Configuration defaultConfig = new Configuration();
            defaultConfig.set("web-server.port", 25581);
            defaultConfig.set("web-server.allow-external-access", true);
            defaultConfig.set("debug", false);
            defaultConfig.set("messaging.channel", "playerinfo:channel");
            defaultConfig.set("data.cleanup-interval", 5); // 分钟
            defaultConfig.set("data.max-age", 60); // 分钟
            defaultConfig.set("messaging.refresh-interval", 30); // 秒
            defaultConfig.set("authentication.enabled", true); // 启用认证
            
            ConfigurationProvider.getProvider(YamlConfiguration.class)
                .save(defaultConfig, new File(dataFolder, "config.yml"));
                
            logger.info(ChatColor.DARK_AQUA + "已创建默认配置文件");
        } catch (IOException e) {
            logger.severe("创建默认配置文件时出错: " + e.getMessage());
        }
    }
    
    /**
     * 保存配置文件
     */
    public void saveConfig() {
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class)
                .save(config, new File(dataFolder, "config.yml"));
        } catch (IOException e) {
            logger.severe("保存配置文件时出错: " + e.getMessage());
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
        extractResource("web/login.html", new File(webRoot, "login.html"));
        
        // 提取物品图标和翻译配置文件
        extractResource("web/itemIcons.cnf", new File(webRoot, "itemIcons.cnf"));
        extractResource("web/itemTranslations.cnf", new File(webRoot, "itemTranslations.cnf"));
        
        // 提取CSS文件
        File cssDir = new File(webRoot, "css");
        if (!cssDir.exists() && !cssDir.mkdirs()) {
            logger.severe("无法创建CSS文件夹!");
        }
        extractResource("web/css/style.css", new File(cssDir, "style.css"));
        extractResource("web/css/login.css", new File(cssDir, "login.css"));
        
        // 提取JS文件
        File jsDir = new File(webRoot, "js");
        if (!jsDir.exists() && !jsDir.mkdirs()) {
            logger.severe("无法创建JS文件夹!");
        }
        extractResource("web/js/app.js", new File(jsDir, "app.js"));
        extractResource("web/js/skinViewer.js", new File(jsDir, "skinViewer.js"));
        extractResource("web/js/auth.js", new File(jsDir, "auth.js"));
        
        // 提取默认皮肤图片
        extractResource("web/DefaultSkin.png", new File(webRoot, "DefaultSkin.png"));
        
        // 提取namemap.cnf文件
        extractResource("web/namemap.cnf", new File(webRoot, "namemap.cnf"));
        
        // 提取图片资源
        extractImgResources(webRoot);
        
        // 确保Placeholder.yml配置文件存在
        File placeholderFile = new File(dataFolder, "Placeholder.yml");
        if (!placeholderFile.exists()) {
            // 尝试从资源中提取Placeholder.yml模板
            extractResource("Placeholder.yml", placeholderFile);
            
            // 如果模板不存在，尝试使用模板文件
            if (!placeholderFile.exists()) {
                extractResource("Placeholder.yml.template", placeholderFile);
                logger.info(ChatColor.GREEN + "已创建Placeholder.yml配置文件（从模板）");
            } else {
                logger.info(ChatColor.GREEN + "已创建Placeholder.yml配置文件");
            }
        }
        
        logger.info(ChatColor.DARK_AQUA + "Web资源文件已提取完成");
    }
    
    /**
     * 提取图片资源
     */
    private void extractImgResources(File webRoot) {
        // 确保img目录存在
        File imgDir = new File(webRoot, "img");
        if (!imgDir.exists()) {
            imgDir.mkdirs();
        }
        
        // 确保blocks和items子目录存在
        File blocksDir = new File(imgDir, "blocks");
        if (!blocksDir.exists()) {
            blocksDir.mkdirs();
        }
        
        File itemsDir = new File(imgDir, "items");
        if (!itemsDir.exists()) {
            itemsDir.mkdirs();
        }
        
        // 提取blocks图片
        try {
            extractResourceDirectory("web/img/blocks", blocksDir);
        } catch (Exception e) {
            logger.severe("提取blocks图片资源失败: " + e.getMessage());
        }
        
        // 提取items图片
        try {
            extractResourceDirectory("web/img/items", itemsDir);
        } catch (Exception e) {
            logger.severe("提取items图片资源失败: " + e.getMessage());
        }
    }
    
    /**
     * 提取资源目录下的所有文件
     * 
     * @param resourceDirPath 资源目录路径
     * @param destDir 目标目录
     */
    private void extractResourceDirectory(String resourceDirPath, File destDir) {
        try {
            // 尝试通过遍历JAR文件来查找资源
            java.util.jar.JarFile jarFile = new java.util.jar.JarFile(plugin.getFile());
            java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
            
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // 检查文件是否在指定目录下 - 确保精确匹配
                if (entryName.startsWith(resourceDirPath + "/") && !entry.isDirectory()) {
                    // 提取文件名部分
                    String fileName = entryName.substring(entryName.lastIndexOf("/") + 1);
                    
                    // 创建目标文件
                    File destFile = new File(destDir, fileName);
                    
                    // 提取文件 - 采用直接从JarFile读取而不是通过ResourceAsStream
                    if (!destFile.exists()) {
                        try (InputStream in = jarFile.getInputStream(entry);
                             java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
                            
                            if (in != null) {
                                // 使用缓冲区复制
                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                while ((bytesRead = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, bytesRead);
                                }
                            }
                        } catch (Exception e) {
                            if (debug) {
                                logger.warning(ChatColor.DARK_AQUA + "提取资源时出错: " + entryName + " - " + e.getMessage());
                            }
                        }
                    }
                }
            }
            
            // 关闭JAR文件
            jarFile.close();
            
        } catch (Exception e) {
            logger.severe("提取资源目录时出错: " + resourceDirPath + " - " + e.getMessage());
        }
    }
    
    /**
     * 提取资源文件
     * 
     * @param resourcePath 资源路径
     * @param destFile 目标文件
     */
    private void extractResource(String resourcePath, File destFile) {
        // 如果目标文件不存在，则提取
        if (!destFile.exists()) {
            try (InputStream in = plugin.getResourceAsStream(resourcePath)) {
                if (in != null) {
                    Files.copy(in, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info(ChatColor.DARK_AQUA + "已提取资源: " + resourcePath + " 到 " + destFile.getAbsolutePath());
                } else {
                    logger.warning(ChatColor.DARK_AQUA + "无法在插件JAR中找到资源: " + resourcePath);
                }
            } catch (Exception e) {
                logger.severe("提取资源文件时出错: " + resourcePath + " - " + e.getMessage());
                e.printStackTrace();
            }
        } else if (debug) {
            logger.info(ChatColor.DARK_AQUA + "资源文件已存在，跳过提取: " + destFile.getAbsolutePath());
        }
    }
    
    /**
     * 启动Web服务器
     */
    private void startWebServer() {
        try {
            File webRoot = new File(dataFolder, "web");
            
            // 创建Web服务器实例
            if (authEnabled && authController != null) {
                // 如果启用认证，创建带认证控制器的Web服务器
                try {
                    // 尝试使用反射创建带认证控制器的Web服务器
                    Class<?> webServerClass = Class.forName("cn.i7mc.playerinfo.bungee.web.BungeeWebServer");
                    java.lang.reflect.Constructor<?> constructor = webServerClass.getConstructor(
                        int.class, boolean.class, File.class, Logger.class);
                    webServer = (BungeeWebServer) constructor.newInstance(
                        webServerPort, allowExternalAccess, webRoot, logger);
                    
                    // 设置玩家控制器
                    webServer.setPlayerController(playerController);
                    
                    // 使用反射设置认证控制器
                    java.lang.reflect.Method setAuthMethod = webServerClass.getMethod(
                        "setAuthController", Class.forName("cn.i7mc.playerinfo.auth.AuthController"));
                    setAuthMethod.invoke(webServer, authController);
                    
                    logger.info(ChatColor.DARK_AQUA + "Web服务器已启用认证系统");
                } catch (Exception e) {
                    logger.severe("创建带认证的Web服务器时出错: " + e.getMessage());
                    e.printStackTrace();
                    
                    // 回退到不带认证的Web服务器
                    webServer = new BungeeWebServer(webServerPort, allowExternalAccess, webRoot, logger);
                    webServer.setPlayerController(playerController);
                    logger.warning(ChatColor.DARK_AQUA + "已回退到不带认证的Web服务器");
                }
            } else {
                // 创建不带认证的Web服务器
                webServer = new BungeeWebServer(webServerPort, allowExternalAccess, webRoot, logger);
                webServer.setPlayerController(playerController);
            }
            
            // 启动Web服务器
            webServer.start();
            logger.info(ChatColor.DARK_AQUA + "Web服务器已启动，端口: " + webServerPort + 
                       (allowExternalAccess ? " (允许外部访问)" : " (仅允许本地访问)"));
        } catch (Exception e) {
            logger.severe("启动Web服务器时出错: " + e.getMessage());
        }
    }
    
    /**
     * 启动数据清理任务
     */
    private void startCleanupTask() {
        long cleanupInterval = config.getLong("data.cleanup-interval", 5) * 60L; // 转换为秒
        long maxDataAge = config.getLong("data.max-age", 60) * 60L * 1000L; // 转换为毫秒
        
        TaskScheduler scheduler = plugin.getProxy().getScheduler();
        cleanupTaskId = scheduler.schedule(plugin, () -> {
            playerController.cleanupStaleData(maxDataAge);
            if (debug) {
                logger.info(ChatColor.DARK_AQUA + "已执行数据清理任务");
            }
        }, cleanupInterval, cleanupInterval, TimeUnit.SECONDS).getId();
        
        logger.info(ChatColor.DARK_AQUA + "数据清理任务已启动，间隔: " + cleanupInterval + "秒");
    }
    
    /**
     * 启动数据刷新请求任务
     */
    private void startRefreshTask() {
        long refreshInterval = config.getLong("messaging.refresh-interval", 30);
        
        TaskScheduler scheduler = plugin.getProxy().getScheduler();
        refreshTaskId = scheduler.schedule(plugin, () -> {
            messageListener.requestDataRefresh();
            if (debug) {
                logger.info(ChatColor.DARK_AQUA + "已请求所有服务器刷新数据");
            }
        }, refreshInterval, refreshInterval, TimeUnit.SECONDS).getId();
        
        logger.info(ChatColor.DARK_AQUA + "数据刷新请求任务已启动，间隔: " + refreshInterval + "秒");
    }
    
    /**
     * 获取配置
     * 
     * @return 配置对象
     */
    public Configuration getConfig() {
        return config;
    }
    
    /**
     * 获取插件实例
     * 
     * @return Plugin实例
     */
    public Plugin getPlugin() {
        return plugin;
    }
    
    /**
     * 获取玩家控制器
     * 
     * @return 玩家控制器
     */
    public BungeePlayerController getPlayerController() {
        return playerController;
    }
    
    /**
     * 获取Web服务器实例
     * 
     * @return Web服务器实例
     */
    public BungeeWebServer getWebServer() {
        return webServer;
    }
    
    /**
     * 获取调试模式状态
     * 
     * @return 是否启用调试模式
     */
    public boolean isDebug() {
        return debug;
    }
    
    /**
     * 获取消息监听器实例
     * 
     * @return 消息监听器实例
     */
    public MessageListener getMessageListener() {
        return messageListener;
    }
    
    /**
     * 重新加载插件配置
     */
    public void reload() {
        // 重新加载配置
        loadConfig();
        
        // 停止任务
        if (cleanupTaskId != -1) {
            plugin.getProxy().getScheduler().cancel(cleanupTaskId);
        }
        if (refreshTaskId != -1) {
            plugin.getProxy().getScheduler().cancel(refreshTaskId);
        }
        
        // 重启Web服务器
        if (webServer != null) {
            webServer.stop();
            startWebServer();
        } else {
            startWebServer();
        }
        
        // 重新注册消息监听器
        if (messageListener != null) {
            messageListener.unregister();
            messageListener = new MessageListener(plugin, messageChannel, this, playerController);
        }
        
        // 重启任务
        startCleanupTask();
        startRefreshTask();
        
        logger.info(ChatColor.DARK_AQUA + "PlayerInfo BungeeCord 插件已重新加载!");
    }
    
    /**
     * 获取代理服务器实例
     * 
     * @return 代理服务器实例
     */
    public net.md_5.bungee.api.ProxyServer getProxy() {
        return plugin.getProxy();
    }
    
    /**
     * 获取插件描述
     * 
     * @return 插件描述
     */
    public net.md_5.bungee.api.plugin.PluginDescription getDescription() {
        return plugin.getDescription();
    }
    
    /**
     * 获取消息通道名称
     * 
     * @return 消息通道名称
     */
    public String getMessageChannel() {
        return messageChannel;
    }
    
    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        loadConfig();
    }
} 