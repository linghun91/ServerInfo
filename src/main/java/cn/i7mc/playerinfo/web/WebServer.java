package cn.i7mc.playerinfo.web;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpContext;
import cn.i7mc.playerinfo.controller.PlayerController;
import cn.i7mc.playerinfo.PlayerInfo;
import java.nio.charset.StandardCharsets;
import org.bukkit.ChatColor;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import java.util.Base64;
import cn.i7mc.playerinfo.auth.AuthController;
import cn.i7mc.playerinfo.web.WebAuthFilter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

public class WebServer {
    private final HttpServer server;
    private final int port;
    private PlayerController playerController;
    private final PlayerInfo plugin;
    private final boolean allowExternalAccess;

    public WebServer(int port, PlayerController playerController, PlayerInfo plugin) throws IOException {
        this.port = port;
        this.playerController = playerController;
        this.plugin = plugin;
        this.allowExternalAccess = plugin.isExternalAccessAllowed();
        
        // 创建服务器并绑定到网络接口
        server = HttpServer.create();
        
        // 根据配置决定绑定的地址
        if (allowExternalAccess) {
            // 绑定到所有网络接口，允许外部访问
            server.bind(new InetSocketAddress(port), 0);
            plugin.getLogger().info(ChatColor.AQUA + "Web服务器将允许任何IP地址访问");
        } else {
            // 仅绑定到本地回环接口，仅允许本机访问
            server.bind(new InetSocketAddress("127.0.0.1", port), 0);
            plugin.getLogger().info(ChatColor.AQUA + "Web服务器将仅允许本地访问(127.0.0.1)");
        }
        
        // 注册路由处理器
        server.createContext("/api/players", playerController);
        server.createContext("/api/player", playerController);
        server.createContext("/api/servers", new ServersHandler(playerController, plugin));
        server.createContext("/api/cache-skin", new SkinCacheHandler(plugin));
        
        // 添加认证相关的API端点
        if (plugin.getAuthController() != null) {
            AuthController authController = plugin.getAuthController();
            server.createContext("/api/auth/login", exchange -> {
                try {
                    authController.handleLogin(exchange);
                } catch (Exception e) {
                    plugin.getLogger().warning("处理登录请求时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            server.createContext("/api/auth/check", exchange -> {
                try {
                    authController.handleCheckSession(exchange);
                } catch (Exception e) {
                    plugin.getLogger().warning("处理会话检查请求时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            server.createContext("/api/auth/logout", exchange -> {
                try {
                    authController.handleLogout(exchange);
                } catch (Exception e) {
                    plugin.getLogger().warning("处理注销请求时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
            // 添加认证过滤器
            HttpContext context = server.createContext("/", new StaticFileHandler(plugin));
            context.getFilters().add(new WebAuthFilter(authController));
        } else {
            server.createContext("/", new StaticFileHandler(plugin));
        }
        
        // 使用默认的执行器
        server.setExecutor(null);
    }
    
    /**
     * 构造函数，用于在不知道PlayerController实例的情况下创建
     * 
     * @param port 端口号
     * @param allowExternalAccess 是否允许外部访问
     * @param webRoot Web根目录
     * @throws IOException 如果创建服务器失败
     */
    public WebServer(int port, boolean allowExternalAccess, File webRoot) throws IOException {
        this.port = port;
        this.allowExternalAccess = allowExternalAccess;
        this.plugin = null;
        
        // 创建服务器并绑定到网络接口
        server = HttpServer.create();
        
        // 根据配置决定绑定的地址
        if (allowExternalAccess) {
            // 绑定到所有网络接口，允许外部访问
            server.bind(new InetSocketAddress(port), 0);
        } else {
            // 仅绑定到本地回环接口，仅允许本机访问
            server.bind(new InetSocketAddress("127.0.0.1", port), 0);
        }
        
        // 创建静态文件处理器
        StaticFileHandler staticHandler = new StaticFileHandler(webRoot);
        
        // 注册路由处理器
        server.createContext("/", staticHandler);
        
        // 使用默认的执行器
        server.setExecutor(null);
    }

    /**
     * 设置玩家控制器
     * 
     * @param playerController 玩家控制器
     */
    public void setPlayerController(PlayerController playerController) {
        this.playerController = playerController;
        
        // 如果已经注册了API上下文，则重新注册
        if (server != null && playerController != null) {
            server.removeContext("/api/players");
            server.removeContext("/api/player");
            server.createContext("/api/players", playerController);
            server.createContext("/api/player", playerController);
        }
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    private static class StaticFileHandler implements HttpHandler {
        private final PlayerInfo plugin;
        private final File webRoot;
        
        public StaticFileHandler(PlayerInfo plugin) {
            this.plugin = plugin;
            this.webRoot = null;
        }
        
        public StaticFileHandler(File webRoot) {
            this.plugin = null;
            this.webRoot = webRoot;
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            // 首先尝试从插件数据目录读取文件
            File dataFile = new File(plugin.getDataFolder(), "web" + path);
            if (dataFile.exists()) {
                // 从插件数据目录中读取文件
                try (InputStream is = new FileInputStream(dataFile)) {
                    byte[] response = readAllBytes(is);
                    String contentType = getContentType(path);
                    
                    exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(200, response.length);
                    
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response);
                    }
                    return;
                } catch (Exception e) {
                    plugin.getLogger().warning("Error reading file from data directory: " + e.getMessage());
                    // 继续尝试从JAR中读取
                }
            }

            // 如果数据目录中没有文件，尝试从插件jar中获取资源
            try (InputStream is = getClass().getResourceAsStream("/web" + path)) {
                if (is == null) {
                    sendError(exchange, 404, "Resource not found");
                    return;
                }
                
                byte[] response = readAllBytes(is);
                String contentType = getContentType(path);
                
                exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, response.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } catch (Exception e) {
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            byte[] response = message.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(code, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }

        private byte[] readAllBytes(InputStream is) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[4096];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        }

        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
            return "application/octet-stream";
        }
    }

    /**
     * 处理皮肤缓存的HttpHandler
     */
    private static class SkinCacheHandler implements HttpHandler {
        private final PlayerInfo plugin;
        
        public SkinCacheHandler(PlayerInfo plugin) {
            this.plugin = plugin;
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    // 从请求中获取玩家名称和UUID
                    String requestBody = new String(readAllBytes(exchange.getRequestBody()));
                    JSONObject jsonRequest = (JSONObject) new JSONParser().parse(requestBody);
                    
                    String playerName = (String) jsonRequest.get("playerName");
                    String uuid = (String) jsonRequest.get("uuid");
                    
                    if (playerName == null || uuid == null) {
                        sendError(exchange, 400, "Missing playerName or uuid");
                        return;
                    }
                    
                    // 通过Mojang API获取皮肤
                    String skinUrl = getSkinUrlFromMojang(uuid);
                    if (skinUrl == null) {
                        sendError(exchange, 404, "Could not find skin for player: " + playerName);
                        return;
                    }
                    
                    // 下载皮肤并保存到资源目录
                    File webResourceDir = new File(plugin.getDataFolder().getParentFile(), "PlayerInfo/web");
                    if (!webResourceDir.exists()) {
                        webResourceDir.mkdirs();
                    }
                    
                    File defaultSkinFile = new File(webResourceDir, "DefaultSkin.png");
                    boolean success = downloadSkin(skinUrl, defaultSkinFile);
                    
                    if (success) {
                        // 返回成功响应
                        JSONObject response = new JSONObject();
                        response.put("success", true);
                        response.put("message", "Skin cached successfully");
                        response.put("playerName", playerName);
                        response.put("uuid", uuid);
                        
                        String responseText = response.toJSONString();
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, responseText.getBytes(StandardCharsets.UTF_8).length);
                        
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(responseText.getBytes(StandardCharsets.UTF_8));
                        }
                    } else {
                        sendError(exchange, 500, "Failed to download and cache skin");
                    }
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                plugin.getLogger().severe("处理皮肤缓存请求时出错: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }
        
        /**
         * 从Mojang API获取玩家皮肤URL
         * @param uuid 玩家UUID
         * @return 皮肤URL或null
         */
        private String getSkinUrlFromMojang(String uuid) {
            try {
                // 调用Mojang API获取玩家profile
                URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                
                if (connection.getResponseCode() != 200) {
                    return null;
                }
                
                // 读取响应
                String response = new String(readAllBytes(connection.getInputStream()));
                JSONObject profileJson = (JSONObject) new JSONParser().parse(response);
                
                // 解析properties中的皮肤信息
                if (profileJson.containsKey("properties")) {
                    org.json.simple.JSONArray properties = (org.json.simple.JSONArray) profileJson.get("properties");
                    for (Object propertyObj : properties) {
                        JSONObject property = (JSONObject) propertyObj;
                        if ("textures".equals(property.get("name"))) {
                            // 解码base64编码的贴图数据
                            String encodedValue = (String) property.get("value");
                            String decodedValue = new String(Base64.getDecoder().decode(encodedValue));
                            
                            // 解析JSON
                            JSONObject textureJson = (JSONObject) new JSONParser().parse(decodedValue);
                            JSONObject textures = (JSONObject) textureJson.get("textures");
                            if (textures.containsKey("SKIN")) {
                                JSONObject skin = (JSONObject) textures.get("SKIN");
                                return (String) skin.get("url");
                            }
                        }
                    }
                }
                
                return null;
            } catch (Exception e) {
                plugin.getLogger().severe("获取皮肤URL时出错: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
        
        /**
         * 下载皮肤并保存到文件
         * @param skinUrl 皮肤URL
         * @param targetFile 目标文件
         * @return 是否成功
         */
        private boolean downloadSkin(String skinUrl, File targetFile) {
            try {
                URL url = new URL(skinUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                
                if (connection.getResponseCode() != 200) {
                    return false;
                }
                
                // 读取皮肤数据
                byte[] skinData = readAllBytes(connection.getInputStream());
                
                // 保存到文件
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    fos.write(skinData);
                }
                
                plugin.getLogger().info("成功缓存皮肤到: " + targetFile.getAbsolutePath());
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("下载皮肤时出错: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        
        /**
         * 发送错误响应
         */
        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            JSONObject errorJson = new JSONObject();
            errorJson.put("error", true);
            errorJson.put("message", message);
            
            String response = errorJson.toJSONString();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, response.getBytes(StandardCharsets.UTF_8).length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    
    /**
     * 处理服务器列表请求的处理器
     */
    private class ServersHandler implements HttpHandler {
        private final PlayerController playerController;
        private final PlayerInfo plugin;
        
        public ServersHandler(PlayerController playerController, PlayerInfo plugin) {
            this.playerController = playerController;
            this.plugin = plugin;
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method not allowed");
                return;
            }
            
            // 构建单服务器响应
            JsonObject response = new JsonObject();
            JsonArray serversArray = new JsonArray();
            JsonObject serverData = new JsonObject();
            
            // 获取当前服务器信息
            String serverName = plugin.getServer().getServerName();
            if (serverName == null || serverName.isEmpty()) {
                // 尝试获取服务器名称，优先使用服务器目录名称
                try {
                    File serverDir = new File(".");
                    serverName = serverDir.getCanonicalFile().getName();
                    if (serverName == null || serverName.isEmpty() || serverName.equals(".")) {
                        serverName = "Bukkit Server";
                    }
                } catch (Exception e) {
                    serverName = "Bukkit Server";
                }
            }
            
            serverData.addProperty("name", serverName);
            serverData.addProperty("playerCount", plugin.getServer().getOnlinePlayers().size());
            serversArray.add(serverData);
            
            response.add("servers", serversArray);
            String jsonResponse = new Gson().toJson(response);
            
            // 设置响应头
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            // 发送响应
            sendResponse(exchange, 200, jsonResponse);
        }
        
        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            byte[] responseBytes = response.getBytes("UTF-8");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
    
    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
} 