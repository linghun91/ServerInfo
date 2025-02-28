package cn.i7mc.playerinfo;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Server;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import cn.i7mc.playerinfo.controller.PlayerController;
import cn.i7mc.playerinfo.messaging.MessageSender;
import cn.i7mc.playerinfo.web.WebServer;
import cn.i7mc.playerinfo.command.PlayerInfoCommand;
import cn.i7mc.playerinfo.model.PlayerData;
import cn.i7mc.playerinfo.auth.AuthController;

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
            try (InputStream in = plugin.getResource("passwd.yml")) {
                if (in != null) {
                    Files.copy(in, passwdFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("已创建认证系统配置文件 passwd.yml");
                } else {
                    logger.warning("无法在插件JAR中找到认证系统配置文件模板");
                }
            } catch (Exception e) {
                logger.severe("提取认证系统配置文件时出错: " + e.getMessage());
            }
        }
        
        // 加载配置
        loadConfig();
        
        // 创建数据文件夹
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        // 初始化认证控制器（如果启用）
        if (authEnabled) {
            try {
                authController = new AuthController(dataFolder);
                logger.info("认证系统已初始化");
            } catch (Exception e) {
                logger.severe("初始化认证系统失败: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            logger.info("认证系统未启用");
        }
        
        // 初始化玩家控制器
        playerController = new PlayerController(this);
        
        // 如果启用了BungeeCord模式，初始化消息发送器
        if (bungeeCordMode) {
            logger.info("正在BungeeCord模式下运行，初始化消息发送器...");
            messageSender = new MessageSender(this);
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
            messageSender.register(); // 注册消息通道
            
            // 注册插件消息监听器，用于处理BungeeCord发来的刷新请求
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, 
                    messageSender.getChannelName(), (channel, player, message) -> {
                // 检查消息类型是否为刷新请求
                try {
                    if (message == null || message.length == 0) {
                        logger.warning("收到空消息数据");
                        return;
                    }
                    
                    DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
                    
                    // 检查可读字节数
                    if (in.available() <= 0) {
                        logger.warning("消息数据格式错误: 没有可读字节");
                        return;
                    }
                    
                    String messageType = in.readUTF();
                    
                    if ("Refresh".equals(messageType)) {
                        // 收到刷新请求，立即刷新玩家数据并发送到BungeeCord
                        if (debug) {
                            logger.info("收到来自BungeeCord的数据刷新请求");
                        }
                        
                        // 刷新玩家数据
                        if (playerController != null) {
                            playerController.refreshData();
                        } else {
                            logger.warning("无法刷新玩家数据：playerController为null");
                        }
                        
                        // 遍历在线玩家，发送每个玩家的数据
                        if (messageSender != null) {
                            int playerCount = 0;
                            for (Player p : plugin.getServer().getOnlinePlayers()) {
                                if (p != null && p.isOnline()) {
                                    PlayerData playerData = playerController.getPlayerData(p);
                                    if (playerData != null) {
                                        messageSender.sendPlayerData(p, playerData);
                                        playerCount++;
                                    }
                                }
                            }
                            
                            if (debug) {
                                logger.info("已响应BungeeCord刷新请求，刷新并发送了 " + 
                                        playerCount + " 名玩家的数据");
                            }
                        } else {
                            logger.warning("无法发送玩家数据：messageSender为null");
                        }
                    } else {
                        logger.info("收到未知消息类型: " + messageType);
                    }
                } catch (Exception e) {
                    logger.warning("处理来自BungeeCord的消息时出错: " + 
                            (e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
                    if (debug) {
                        e.printStackTrace();
                    }
                }
            });
            
            if (standaloneWebServer) {
                logger.info("已启用独立Web服务器模式，本地Web服务器将同时运行");
            } else {
                logger.info("已禁用独立Web服务器模式，本地Web服务器将不会启动");
            }
        }
        
        // 决定是否启动Web服务器
        // 在BungeeCord模式下，只有当standaloneWebServer为true时才启动
        // 在非BungeeCord模式下，始终启动Web服务器
        if (!bungeeCordMode || standaloneWebServer) {
            try {
                webServer = new WebServer(webServerPort, playerController, this);
                webServer.start();
                logger.info("Web服务器已启动，端口: " + webServerPort + 
                           (allowExternalAccess ? " (允许外部访问)" : " (仅允许本地访问)"));
            } catch (Exception e) {
                logger.severe("启动Web服务器失败: " + e.getMessage());
            }
        } else {
            logger.info("根据配置，子服端不启动Web服务器");
        }
        
        // 启动数据刷新任务
        startDataRefreshTask();
        
        logger.info("PlayerInfo 插件已成功启动!");
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
     * 获取调试模式状态
     * 
     * @return 是否启用调试模式
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
     * 获取插件实例
     * 
     * @return 插件实例
     */
    public JavaPlugin getPlugin() {
        return plugin;
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
     * 重新加载插件
     */
    public void reload() {
        // 重新加载配置
        plugin.reloadConfig();
        
        // 检查配置文件是否存在，如果不存在则提取
        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("已创建默认配置文件 config.yml");
                }
            } catch (Exception e) {
                logger.severe("提取默认配置文件时出错: " + e.getMessage());
            }
        }
        
        loadConfig();
        
        // 停止并重启Web服务器
        if (webServer != null) {
            webServer.stop();
        }
        
        if (!bungeeCordMode || standaloneWebServer) {
            try {
                webServer = new WebServer(webServerPort, playerController, this);
                webServer.start();
                logger.info("Web服务器已重新启动，端口: " + webServerPort + 
                        (allowExternalAccess ? " (允许外部访问)" : " (仅允许本地访问)"));
            } catch (Exception e) {
                logger.severe("重新启动Web服务器失败: " + e.getMessage());
            }
        }
        
        // 停止并重启消息发送器
        if (messageSender != null) {
            messageSender.unregister();
            messageSender = new MessageSender(this);
            messageSender.register();
        }
        
        logger.info("PlayerInfo 配置已重新加载");
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
     * 是否允许外部访问Web服务器
     * 
     * @return 是否允许外部访问
     */
    public boolean isExternalAccessAllowed() {
        return allowExternalAccess;
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
    
    /**
     * 获取认证控制器
     * @return 认证控制器实例
     */
    public AuthController getAuthController() {
        return authController;
    }
} 