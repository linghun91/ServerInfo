package cn.i7mc.playerinfo.bungee.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import cn.i7mc.playerinfo.bungee.controller.BungeePlayerController;
import cn.i7mc.playerinfo.auth.AuthController;
import cn.i7mc.playerinfo.web.WebAuthFilter;

/**
 * BungeeCord的Web服务器实现
 */
public class BungeeWebServer {
    private final int port;
    private final boolean allowExternalAccess;
    private final File dataFolder;
    private final Logger logger;
    private HttpServer server;
    private BungeePlayerController playerController;
    private AuthController authController;
    
    // 内容类型映射
    private static final Map<String, String> MIME_TYPES = new HashMap<>();
    static {
        MIME_TYPES.put("html", "text/html");
        MIME_TYPES.put("css", "text/css");
        MIME_TYPES.put("js", "application/javascript");
        MIME_TYPES.put("json", "application/json");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("cnf", "text/plain");
    }
    
    /**
     * 构造一个新的BungeeWebServer实例
     * 
     * @param port 端口号
     * @param allowExternalAccess 是否允许外部访问
     * @param dataFolder 数据文件夹
     * @param logger 日志记录器
     */
    public BungeeWebServer(int port, boolean allowExternalAccess, File dataFolder, Logger logger) {
        this.port = port;
        this.allowExternalAccess = allowExternalAccess;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }
    
    /**
     * 设置玩家控制器
     * 
     * @param playerController 玩家控制器
     */
    public void setPlayerController(BungeePlayerController playerController) {
        this.playerController = playerController;
    }
    
    /**
     * 设置认证控制器
     * 
     * @param authController 认证控制器
     */
    public void setAuthController(AuthController authController) {
        this.authController = authController;
        logger.info("已设置认证控制器");
    }
    
    /**
     * 启动Web服务器
     */
    public void start() {
        try {
            InetSocketAddress address = allowExternalAccess 
                ? new InetSocketAddress(port) 
                : new InetSocketAddress("localhost", port);
            
            server = HttpServer.create(address, 0);
            
            // 添加认证处理
            if (authController != null) {
                // 添加认证API端点
                server.createContext("/api/auth/login", new AuthApiHandler());
                server.createContext("/api/auth/logout", new AuthApiHandler());
                server.createContext("/api/auth/check", new AuthApiHandler());
                
                // 为静态资源添加认证过滤器
                WebAuthFilter authFilter = new WebAuthFilter(authController);
                server.createContext("/", new StaticFileHandler(authFilter));
                
                logger.info("已启用Web认证系统");
            } else {
                // 不使用认证过滤器
                server.createContext("/", new StaticFileHandler(null));
                logger.info("未启用Web认证系统");
            }
            
            // 添加API端点
            server.createContext("/api/players", new ApiHandler());
            server.createContext("/api/servers", new ApiHandler());
            server.createContext("/api/player/", new ApiHandler());
            
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            
            if (address.getHostName().equals("0.0.0.0")) {
                logger.info("§3BungeeCord Web服务器已启动在所有接口的端口: " + port);
            } else {
                logger.info("§3BungeeCord Web服务器已启动在 " + address.getHostName() + ":" + port);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "启动Web服务器失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 停止Web服务器
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("BungeeCord Web服务器已停止");
        }
    }
    
    /**
     * 处理静态文件请求
     */
    private class StaticFileHandler implements HttpHandler {
        private final WebAuthFilter authFilter;
        
        public StaticFileHandler(WebAuthFilter authFilter) {
            this.authFilter = authFilter;
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            // 默认首页
            if (path.equals("/")) {
                path = "/index.html";
            }
            
            // 检查认证状态，除了特定页面外都需要身份验证
            boolean needsAuth = true;
            
            // 登录页面和认证API不需要身份验证
            if (path.equals("/login.html") || 
                path.startsWith("/api/auth/") || 
                path.startsWith("/css/") || 
                path.startsWith("/js/") || 
                path.startsWith("/img/")) {
                needsAuth = false;
            }
            
            // 如果需要认证且认证过滤器存在，则验证会话
            if (needsAuth && authFilter != null) {
                boolean authenticated = WebAuthFilter.isAuthenticated(exchange, authController);
                if (!authenticated) {
                    // 未验证，重定向到登录页面
                    exchange.getResponseHeaders().set("Location", "/login.html");
                    exchange.sendResponseHeaders(302, -1);
                    return;
                }
            }
            
            // 去除开头的斜杠
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            
            // 构建文件路径 - 修正路径问题
            // 原先错误的构建方式：File file = new File(new File(dataFolder, "web"), path);
            // 因为dataFolder已经是web目录，这会导致系统查找 dataFolder/web/web/path
            File file = new File(dataFolder, path);
            
            if (file.exists() && !file.isDirectory()) {
                // 从文件系统加载文件
                serveFileFromFilesystem(exchange, file);
            } else {
                // 尝试从JAR中加载文件
                InputStream resourceStream = getClass().getClassLoader().getResourceAsStream("web/" + path);
                if (resourceStream != null) {
                    // 从JAR包加载文件
                    serveResourceFromJar(exchange, resourceStream, path);
                } else {
                    // 文件不存在
                    String response = "404 - Not Found";
                    exchange.sendResponseHeaders(404, response.length());
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(response.getBytes());
                    }
                }
            }
        }
        
        /**
         * 从文件系统提供文件
         */
        private void serveFileFromFilesystem(HttpExchange exchange, File file) throws IOException {
            // 设置内容类型
            String contentType = getContentType(file.getName());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            
            // 发送文件内容
            exchange.sendResponseHeaders(200, file.length());
            try (OutputStream out = exchange.getResponseBody();
                 InputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        }
        
        /**
         * 从JAR包提供资源
         */
        private void serveResourceFromJar(HttpExchange exchange, InputStream resourceStream, String path) throws IOException {
            // 设置内容类型
            String contentType = getContentType(path);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            
            // 读取资源到内存
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = resourceStream.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            byte[] data = baos.toByteArray();
            
            // 发送资源内容
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(data);
            }
        }
        
        /**
         * 根据文件名确定内容类型
         */
        private String getContentType(String fileName) {
            int dot = fileName.lastIndexOf('.');
            if (dot > 0) {
                String extension = fileName.substring(dot + 1).toLowerCase();
                String mimeType = MIME_TYPES.get(extension);
                if (mimeType != null) {
                    return mimeType;
                }
            }
            return "application/octet-stream";
        }
    }
    
    /**
     * 处理API请求
     */
    private class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 设置CORS头信息
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type,Authorization");
            
            // 处理预检请求
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            // 只处理GET请求
            if (!"GET".equals(exchange.getRequestMethod())) {
                String errorResponse = "{\"error\":\"Method not allowed\"}";
                exchange.sendResponseHeaders(405, errorResponse.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes());
                }
                return;
            }
            
            // 设置内容类型和字符集
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            // 添加额外的头信息防止缓存
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.getResponseHeaders().set("Pragma", "no-cache");
            exchange.getResponseHeaders().set("Expires", "0");
            
            // 创建一个简单的JSON响应
            String response;
            
            // 如果有玩家控制器，使用它获取数据
            if (playerController != null) {
                // 解析路径参数
                String path = exchange.getRequestURI().getPath();
                String query = exchange.getRequestURI().getQuery();
                
                // 当请求玩家列表或服务器列表时，主动请求各子服刷新数据
                if (path.equals("/api/players") || path.equals("/api/servers")) {
                    // 尝试通过反射获取PlayerInfoBungee实例和MessageListener实例
                    try {
                        // 获取MessageListener的requestDataRefresh方法
                        java.lang.reflect.Method getPluginMethod = playerController.getClass().getMethod("getPlugin");
                        Object playerInfoBungee = getPluginMethod.invoke(playerController);
                        
                        if (playerInfoBungee != null) {
                            java.lang.reflect.Method getMessageListenerMethod = playerInfoBungee.getClass().getMethod("getMessageListener");
                            Object messageListener = getMessageListenerMethod.invoke(playerInfoBungee);
                            
                            if (messageListener != null) {
                                java.lang.reflect.Method requestDataRefreshMethod = messageListener.getClass().getMethod("requestDataRefresh");
                                requestDataRefreshMethod.invoke(messageListener);
                                
                                // 执行数据清理，移除过期数据
                                long maxAge = 60 * 1000; // 60秒
                                playerController.cleanupStaleData(maxAge);
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("尝试请求数据刷新时出错: " + e.getMessage());
                    }
                }
                
                if (path.equals("/api/players")) {
                    // 获取服务器列表或指定服务器的玩家列表
                    String serverName = getQueryParameter(query, "server");
                    
                    if (serverName == null) {
                        // 返回服务器列表
                        response = playerController.handleServerList();
                    } else {
                        // 返回指定服务器的玩家列表
                        response = playerController.handlePlayerList(serverName);
                    }
                } else if (path.equals("/api/servers")) {
                    // 返回包含玩家数量的服务器列表
                    response = playerController.handleServerListWithPlayerCount();
                } else if (path.startsWith("/api/player/")) {
                    // 提取玩家名和服务器名
                    String playerName = path.substring("/api/player/".length());
                    // URL解码玩家名
                    playerName = java.net.URLDecoder.decode(playerName, "UTF-8");
                    String serverName = getQueryParameter(query, "server");
                    
                    if (serverName != null && !playerName.isEmpty()) {
                        // 返回玩家详情
                        response = playerController.handlePlayerDetails(serverName, playerName);
                    } else {
                        response = "{\"error\":\"Missing server or player parameter\"}";
                    }
                } else {
                    // 默认返回简单响应
                    response = "{\"status\": \"ok\"}";
                }
            } else {
                // 玩家控制器未初始化
                response = "{\"error\": \"Player controller not initialized\"}";
            }
            
            // 将响应字符串转换为UTF-8字节数组
            byte[] responseBytes = response.getBytes("UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
        
        /**
         * 从查询字符串中获取参数值
         */
        private String getQueryParameter(String query, String name) {
            if (query == null) {
                return null;
            }
            
            try {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && pair[0].equals(name)) {
                        // 解码URL参数
                        String value = java.net.URLDecoder.decode(pair[1], "UTF-8");
                        return value;
                    }
                }
            } catch (Exception e) {
                logger.warning("解析查询参数时出错: " + e.getMessage());
            }
            
            return null;
        }
    }
    
    /**
     * 处理认证API请求
     */
    private class AuthApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 设置CORS头信息
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type,Authorization");
            
            // 处理预检请求
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            // 设置内容类型和字符集
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.getResponseHeaders().set("Pragma", "no-cache");
            exchange.getResponseHeaders().set("Expires", "0");
            
            String path = exchange.getRequestURI().getPath();
            String response = "{\"error\":\"Unknown endpoint\"}";
            
            if (authController == null) {
                response = "{\"error\":\"Authentication controller not initialized\"}";
                sendResponse(exchange, 500, response);
                return;
            }
            
            try {
                // 处理登录
                if ("POST".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().equals("/api/auth/login")) {
                    logger.fine("收到登录请求，转发到AuthController...");
                    authController.handleLogin(exchange);
                    return;
                }
                
                // 处理注销
                if ("POST".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().equals("/api/auth/logout")) {
                    logger.fine("收到注销请求，转发到AuthController...");
                    authController.handleLogout(exchange);
                    return;
                }
                
                // 检查会话
                if ("GET".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().equals("/api/auth/check")) {
                    logger.fine("收到会话检查请求，转发到AuthController...");
                    authController.handleCheckSession(exchange);
                    return;
                }
                
                // 兼容旧的API端点
                if ("POST".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().equals("/api/login")) {
                    logger.fine("收到旧格式登录请求，转发到新路径...");
                    authController.handleLogin(exchange);
                    return;
                }
                
                // 处理旧的注销和会话检查端点
                if ("POST".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().equals("/api/logout")) {
                    authController.handleLogout(exchange);
                    return;
                }
                
                if ("GET".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().equals("/api/session")) {
                    authController.handleCheckSession(exchange);
                    return;
                }
                
                // 未知端点
                logger.warning("收到未知认证端点请求: " + path);
                response = "{\"error\":\"Unknown endpoint: " + path + "\"}";
                sendResponse(exchange, 404, response);
            } catch (Exception e) {
                // 处理请求过程中的异常
                logger.log(Level.SEVERE, "处理认证请求时出错", e);
                response = "{\"error\":\"Internal server error: " + e.getMessage() + "\"}";
                sendResponse(exchange, 500, response);
            }
        }
        
        /**
         * 发送响应
         */
        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            byte[] responseBytes = response.getBytes("UTF-8");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
        
        /**
         * 读取JSON请求体
         */
        private Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException {
            InputStream is = exchange.getRequestBody();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) != -1) {
                baos.write(buffer, 0, length);
            }
            String requestBody = baos.toString("UTF-8");
            
            try {
                return new com.google.gson.Gson().fromJson(requestBody, Map.class);
            } catch (Exception e) {
                logger.warning("解析JSON请求体时出错: " + e.getMessage());
                return null;
            }
        }
        
        /**
         * 发送JSON响应
         */
        private void sendJsonResponse(HttpExchange exchange, Map<String, Object> response) throws IOException {
            String jsonResponse = new com.google.gson.Gson().toJson(response);
            sendResponse(exchange, 200, jsonResponse);
        }
    }
} 