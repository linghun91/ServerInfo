package cn.i7mc.playerinfo.auth;

import com.sun.net.httpserver.HttpExchange;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 认证控制器
 * 处理用户登录请求和权限验证
 */
public class AuthController {
    private static final Logger logger = Logger.getLogger("AuthController");
    
    private final File passwdFile;
    private final Map<String, Object> authConfig;
    private final List<Map<String, Object>> users;
    private final SessionManager sessionManager;
    
    // 添加登录失败计数器和锁定时间记录
    private final Map<String, Integer> loginFailureCount = new ConcurrentHashMap<>();
    private final Map<String, Long> accountLockTime = new ConcurrentHashMap<>();
    
    /**
     * 构造函数
     * @param pluginDataFolder 插件数据文件夹
     */
    @SuppressWarnings("unchecked")
    public AuthController(File pluginDataFolder) {
        // 初始化密码文件
        this.passwdFile = new File(pluginDataFolder, "passwd.yml");
        
        // 确保配置文件存在
        ensureConfigFile();
        
        // 加载配置
        Map<String, Object> config = loadConfig();
        this.authConfig = (Map<String, Object>) config.getOrDefault("authentication", new HashMap<>());
        this.users = (List<Map<String, Object>>) config.getOrDefault("users", new ArrayList<>());
        
        // 创建会话管理器
        int sessionTimeout = getSessionTimeout();
        this.sessionManager = new SessionManager(sessionTimeout);
        
        logger.fine("认证系统已初始化，共加载 " + users.size() + " 个用户账号");
    }
    
    /**
     * 获取会话超时时间
     * @return 超时时间（分钟）
     */
    private int getSessionTimeout() {
        Object timeout = authConfig.getOrDefault("session-timeout", 1440); // 默认24小时
        if (timeout instanceof Integer) {
            return (Integer) timeout;
        }
        return 1440;
    }
    
    /**
     * 获取最大登录尝试次数
     * @return 最大尝试次数
     */
    private int getMaxLoginAttempts() {
        Object attempts = authConfig.getOrDefault("max-login-attempts", 5);
        if (attempts instanceof Integer) {
            return (Integer) attempts;
        }
        return 5;
    }
    
    /**
     * 获取账户锁定时间
     * @return 锁定时间（分钟）
     */
    private int getLockoutDuration() {
        Object duration = authConfig.getOrDefault("lockout-duration", 30);
        if (duration instanceof Integer) {
            return (Integer) duration;
        }
        return 30;
    }
    
    /**
     * 确保配置文件存在，不存在则从模板创建
     */
    private void ensureConfigFile() {
        if (passwdFile.exists()) {
            logger.fine("passwd.yml file already exists, skipping creation");
            return;
        }

        logger.fine("Creating passwd.yml file...");
        
        try {
            // 创建文件父目录（如果不存在）
            File parentDir = passwdFile.getParentFile();
            if (!parentDir.exists()) {
                if (parentDir.mkdirs()) {
                    logger.fine("Created directory: " + parentDir.getAbsolutePath());
                } else {
                    logger.severe("Cannot create directory: " + parentDir.getAbsolutePath());
                }
            }
            
            // 直接创建默认配置文件，不再尝试读取模板
            createDefaultConfig();
            logger.fine("Successfully created passwd.yml file: " + passwdFile.getAbsolutePath());
        } catch (Exception e) {
            logger.severe("Error creating passwd.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 创建默认配置文件
     */
    private void createDefaultConfig() {
        logger.fine("Starting to create default passwd.yml config file...");
        
        try {
            // 确保目标文件的父目录存在
            File parentDir = passwdFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (!created) {
                    logger.severe("Cannot create directory: " + parentDir.getAbsolutePath());
                }
            }
            
            // 使用纯文本写入，保持与模板完全一致的格式
            String defaultConfig = 
                "# ServerInfo Login Authentication System Config File\n" +
                "# This file is automatically generated when the plugin starts for the first time\n" +
                "# Default accounts: admin/admin123 and viewer/view123\n" +
                "# Please change the default passwords after first login\n\n" +
                "authentication:\n" +
                "  enabled: true                   # Enable authentication\n" +
                "  session-timeout: 1440           # Session timeout in minutes (24 hours)\n" +
                "  max-login-attempts: 5           # Maximum login attempts before lockout\n" +
                "  lockout-duration: 30            # Lockout duration in minutes\n\n" +
                "users:\n" +
                "  - username: admin               # Admin user\n" +
                "    password: \"admin123\"          # Default password (change after first login)\n" +
                "    permission: admin             # Permission level\n" +
                "  \n" +
                "  - username: viewer              # Regular viewer user\n" +
                "    password: \"view123\"           # Default password (change after first login)\n" +
                "    permission: view              # Permission level\n\n" +
                "# Permission levels:\n" +
                "# admin - Full access\n" +
                "# view - Read-only access";
                
            Files.write(passwdFile.toPath(), defaultConfig.getBytes(StandardCharsets.UTF_8));
            
            // 验证文件是否已创建
            if (passwdFile.exists() && passwdFile.length() > 0) {
                logger.fine("Default passwd.yml config file created successfully, path: " + passwdFile.getAbsolutePath());
                logger.fine("Default accounts: admin/admin123 and viewer/view123, please change passwords after first login");
            } else {
                logger.severe("passwd.yml file creation seems to have failed, file doesn't exist or is empty");
            }
        } catch (Exception e) {
            logger.severe("Error creating default config: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 加载配置文件
     * @return 配置对象
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadConfig() {
        try {
            Yaml yaml = new Yaml();
            // 使用InputStreamReader明确指定UTF-8编码
            InputStreamReader reader = new InputStreamReader(
                new FileInputStream(passwdFile), StandardCharsets.UTF_8);
            Map<String, Object> config = yaml.load(reader);
            reader.close();
            return config != null ? config : new HashMap<>();
        } catch (Exception e) {
            logger.severe("加载passwd.yml时出错: " + e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * 处理登录请求
     * @param exchange HTTP交换对象
     * @throws IOException IO异常
     */
    public void handleLogin(HttpExchange exchange) throws IOException {
        logger.fine("===== 收到登录请求 =====");
        
        // 只接受POST请求
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            logger.warning("请求方法不允许: " + exchange.getRequestMethod());
            sendResponse(exchange, 405, "{\"success\":false,\"message\":\"方法不允许\"}");
            return;
        }
        
        // 读取请求体
        InputStream requestBody = exchange.getRequestBody();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = requestBody.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        String requestData = result.toString(StandardCharsets.UTF_8.name());
        logger.fine("收到的原始请求数据: " + requestData);
        
        // 解析请求数据
        Map<String, String> credentials = parseCredentials(requestData);
        String username = credentials.get("username");
        String password = credentials.get("password");
        
        logger.fine("解析后的用户凭据: 用户名='" + username + "', 密码长度=" + (password != null ? password.length() : 0));
        
        // 验证凭据
        if (username == null || password == null) {
            logger.warning("登录失败: 用户名或密码为空");
            sendResponse(exchange, 400, "{\"success\":false,\"message\":\"用户名和密码不能为空\"}");
            return;
        }
        
        // 检查账户是否被锁定
        if (isAccountLocked(username)) {
            long lockTimeRemaining = getRemainingLockTime(username);
            logger.fine("登录失败: 用户 '" + username + "' 账户已锁定，剩余锁定时间: " + lockTimeRemaining + " 分钟");
            sendResponse(exchange, 401, "{\"success\":false,\"message\":\"账户已锁定，请在 " + lockTimeRemaining + " 分钟后重试\"}");
            return;
        }
        
        // 查找用户并验证
        logger.fine("开始查找用户: '" + username + "'");
        Map<String, Object> user = findUser(username);
        if (user == null) {
            logger.fine("登录失败: 找不到用户 '" + username + "'");
            logger.fine("当前系统中的所有用户: " + getUsersList());
            sendResponse(exchange, 401, "{\"success\":false,\"message\":\"用户名或密码错误\"}");
            return;
        }
        
        // 获取存储的密码
        String storedPassword = (String) user.get("password");
        logger.fine("用户 '" + username + "' 的存储密码: '" + storedPassword + "'");
        
        // 直接比较密码，不检查哈希
        boolean passwordMatch = password.equals(storedPassword);
        logger.fine("密码比较结果: " + (passwordMatch ? "匹配成功" : "匹配失败"));
        
        if (!passwordMatch) {
            // 增加失败计数并检查是否达到锁定阈值
            incrementFailureCount(username);
            int currentFailures = getFailureCount(username);
            int maxAttempts = getMaxLoginAttempts();
            
            logger.fine("登录失败: 密码不匹配, 用户='" + username + "', 失败次数=" + currentFailures + "/" + maxAttempts);
            
            if (currentFailures >= maxAttempts) {
                lockAccount(username);
                int lockDuration = getLockoutDuration();
                logger.fine("用户 '" + username + "' 已被锁定 " + lockDuration + " 分钟");
                sendResponse(exchange, 401, "{\"success\":false,\"message\":\"登录失败次数过多，账户已被锁定 " + lockDuration + " 分钟\"}");
            } else {
                sendResponse(exchange, 401, "{\"success\":false,\"message\":\"用户名或密码错误\"}");
            }
            return;
        }
        
        // 登录成功，重置失败计数
        resetFailureCount(username);
        
        // 创建会话
        String permission = (String) user.getOrDefault("permission", "view");
        String sessionId = sessionManager.createSession(username, permission);
        logger.fine("登录成功, 创建会话: " + sessionId + ", 权限级别: " + permission);
        
        // 返回成功响应和会话ID
        String response = "{\"success\":true,\"message\":\"登录成功\",\"session\":\"" + sessionId + "\"}";
        
        // 设置会话Cookie
        exchange.getResponseHeaders().add("Set-Cookie", "session=" + sessionId + "; Path=/; Max-Age=" + (getSessionTimeout() * 60));
        logger.fine("设置会话Cookie, 过期时间: " + getSessionTimeout() + "分钟");
        sendResponse(exchange, 200, response);
    }
    
    /**
     * 解析请求中的凭据
     * @param requestData 请求数据
     * @return 用户名和密码的映射
     */
    private Map<String, String> parseCredentials(String requestData) {
        Map<String, String> result = new HashMap<>();
        logger.fine("开始解析凭据: " + requestData);
        
        // 尝试解析JSON
        if (requestData.startsWith("{")) {
            try {
                // 简单JSON解析，实际应使用JSON库
                logger.fine("检测到JSON格式数据，进行JSON解析");
                String jsonData = requestData;
                jsonData = jsonData.replaceAll("[{}\"]", "");
                logger.fine("处理后的JSON数据: " + jsonData);
                
                String[] pairs = jsonData.split(",");
                for (String pair : pairs) {
                    String[] keyValue = pair.split(":");
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim();
                        String value = keyValue[1].trim();
                        result.put(key, value);
                        logger.fine("解析JSON键值对: " + key + "=" + value);
                    } else {
                        logger.warning("JSON键值对格式不正确: " + pair);
                    }
                }
                logger.fine("JSON解析结果: " + result);
                return result;
            } catch (Exception e) {
                logger.warning("解析JSON失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // 回退到表单数据解析
        logger.fine("尝试解析表单数据");
        String[] pairs = requestData.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                result.put(keyValue[0], keyValue[1]);
                logger.fine("解析表单键值对: " + keyValue[0] + "=" + keyValue[1]);
            } else {
                logger.warning("表单键值对格式不正确: " + pair);
            }
        }
        
        logger.fine("最终解析结果: " + result);
        return result;
    }
    
    /**
     * 查找用户
     * @param username 用户名
     * @return 用户对象，如果不存在则返回null
     */
    private Map<String, Object> findUser(String username) {
        logger.fine("开始查找用户: '" + username + "'");
        int index = 0;
        for (Map<String, Object> user : users) {
            String currentUsername = (String) user.get("username");
            logger.fine("比较用户 #" + index + ": '" + currentUsername + "'");
            if (username.equals(currentUsername)) {
                logger.fine("用户匹配成功: '" + username + "'");
                return user;
            }
            index++;
        }
        logger.fine("未找到用户: '" + username + "'");
        return null;
    }
    
    /**
     * 获取所有用户列表信息(仅用于调试)
     */
    private String getUsersList() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < users.size(); i++) {
            Map<String, Object> user = users.get(i);
            sb.append("'").append(user.get("username")).append("'");
            if (i < users.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * 保存配置
     */
    private void saveConfig() {
        try {
            // 从当前配置构建新的配置文本，保持与模板一致的格式
            StringBuilder configText = new StringBuilder();
            configText.append("# ServerInfo Login Authentication System Config File\n");
            configText.append("# This file is automatically generated when the plugin starts for the first time\n");
            configText.append("# Default accounts: admin/admin123 and viewer/view123\n");
            configText.append("# Please change the default passwords after first login\n\n");
            
            // 添加authentication部分
            configText.append("authentication:\n");
            configText.append("  enabled: ").append(authConfig.getOrDefault("enabled", true)).append("                   # Enable authentication\n");
            configText.append("  session-timeout: ").append(authConfig.getOrDefault("session-timeout", 1440)).append("           # Session timeout in minutes (24 hours)\n");
            configText.append("  max-login-attempts: ").append(authConfig.getOrDefault("max-login-attempts", 5)).append("           # Maximum login attempts before lockout\n");
            configText.append("  lockout-duration: ").append(authConfig.getOrDefault("lockout-duration", 30)).append("            # Lockout duration in minutes\n\n");
            
            // 添加users部分
            configText.append("users:\n");
            for (int i = 0; i < users.size(); i++) {
                Map<String, Object> user = users.get(i);
                String username = (String) user.get("username");
                String password = (String) user.get("password");
                String permission = (String) user.getOrDefault("permission", "view");
                
                String userComment = username.equals("admin") ? "Admin user" : 
                                    username.equals("viewer") ? "Regular viewer user" : 
                                    "User account";
                
                configText.append("  - username: ").append(username).append("               # ").append(userComment).append("\n");
                configText.append("    password: \"").append(password).append("\"          # Default password (change after first login)\n");
                configText.append("    permission: ").append(permission).append("             # Permission level\n");
                
                // 在用户之间添加空行，除非是最后一个用户
                if (i < users.size() - 1) {
                    configText.append("  \n");
                }
            }
            
            // 添加权限说明
            configText.append("\n# Permission levels:\n");
            configText.append("# admin - Full access\n");
            configText.append("# view - Read-only access");
            
            // 写入文件，使用UTF-8编码
            Files.write(passwdFile.toPath(), configText.toString().getBytes(StandardCharsets.UTF_8));
            
            logger.info("已更新passwd.yml配置文件，保持原始格式");
        } catch (IOException e) {
            logger.severe("保存配置时出错: " + e.getMessage());
        }
    }
    
    /**
     * 验证会话有效性
     * @param exchange HTTP交换对象
     * @return 会话是否有效
     */
    public boolean validateSession(HttpExchange exchange) {
        // 从Cookie获取会话ID
        String sessionId = null;
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies != null) {
            logger.fine("验证会话: 收到Cookies: " + cookies);
            for (String cookie : cookies) {
                String[] pairs = cookie.split(";");
                for (String pair : pairs) {
                    String[] keyValue = pair.trim().split("=");
                    if (keyValue.length == 2 && keyValue[0].equals("session")) {
                        sessionId = keyValue[1];
                        logger.fine("验证会话: 从Cookie中提取会话ID: " + sessionId);
                        break;
                    }
                }
            }
        } else {
            logger.fine("验证会话: 请求中无Cookie");
        }
        
        // 也尝试从URL参数获取会话ID
        if (sessionId == null) {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("session=")) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=");
                    if (keyValue.length == 2 && keyValue[0].equals("session")) {
                        sessionId = keyValue[1];
                        logger.fine("验证会话: 从URL参数提取会话ID: " + sessionId);
                        break;
                    }
                }
            }
        }
        
        boolean isValid = sessionManager.isValidSession(sessionId);
        logger.fine("验证会话结果: " + (isValid ? "有效" : "无效") + " 会话ID: " + sessionId);
        return isValid;
    }
    
    /**
     * 获取会话数据
     * @param exchange HTTP交换对象
     * @return 会话数据，如果会话无效则返回null
     */
    public SessionManager.SessionData getSessionData(HttpExchange exchange) {
        // 从Cookie获取会话ID
        String sessionId = null;
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies != null) {
            for (String cookie : cookies) {
                String[] pairs = cookie.split(";");
                for (String pair : pairs) {
                    String[] keyValue = pair.trim().split("=");
                    if (keyValue.length == 2 && keyValue[0].equals("session")) {
                        sessionId = keyValue[1];
                        break;
                    }
                }
            }
        }
        
        // 也尝试从URL参数获取会话ID
        if (sessionId == null) {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("session=")) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=");
                    if (keyValue.length == 2 && keyValue[0].equals("session")) {
                        sessionId = keyValue[1];
                        break;
                    }
                }
            }
        }
        
        return sessionManager.getSessionData(sessionId);
    }
    
    /**
     * 发送响应
     * @param exchange HTTP交换对象
     * @param statusCode 状态码
     * @param response 响应内容
     * @throws IOException IO异常
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        byte[] responseBytes = response.getBytes("UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    /**
     * 用户登录
     * @param username 用户名
     * @param password 密码
     * @return 成功返回会话ID，失败返回null
     */
    public String login(String username, String password) {
        // 查找用户
        Map<String, Object> user = findUser(username);
        if (user == null) {
            logger.fine("登录失败：找不到用户 " + username);
            return null;
        }
        
        // 检查密码 - 直接比较明文密码
        String storedPassword = (String) user.get("password");
        if (storedPassword == null || !password.equals(storedPassword)) {
            logger.fine("登录失败：密码错误 " + username);
            return null;
        }
        
        // 密码正确，创建会话
        String permission = (String) user.getOrDefault("permission", "view");
        String sessionId = sessionManager.createSession(username, permission);
        
        logger.fine("用户 " + username + " 登录成功");
        return sessionId;
    }
    
    /**
     * 注销会话
     * @param sessionId 会话ID
     */
    public void logout(String sessionId) {
        if (sessionId != null) {
            sessionManager.removeSession(sessionId);
            logger.fine("会话已注销: " + sessionId);
        }
    }
    
    /**
     * 检查会话是否有效
     * @param sessionId 会话ID
     * @return 是否有效
     */
    public boolean isValidSession(String sessionId) {
        return sessionId != null && sessionManager.getSessionData(sessionId) != null;
    }
    
    /**
     * 验证认证系统是否启用
     * @return 是否启用认证
     */
    public boolean isAuthenticationEnabled() {
        return (boolean) authConfig.getOrDefault("enabled", true);
    }
    
    /**
     * 处理会话检查请求
     * @param exchange HTTP交换对象
     * @throws IOException IO异常
     */
    public void handleCheckSession(HttpExchange exchange) throws IOException {
        // 检查会话是否有效
        SessionManager.SessionData sessionData = getSessionData(exchange);
        boolean isLoggedIn = sessionData != null;
        
        // 构建响应
        String response;
        if (isLoggedIn) {
            response = "{\"loggedIn\":true,\"username\":\"" + sessionData.getUsername() + 
                      "\",\"permission\":\"" + sessionData.getPermission() + "\"}";
        } else {
            response = "{\"loggedIn\":false}";
        }
        
        // 发送响应
        sendResponse(exchange, 200, response);
    }
    
    /**
     * 处理注销请求
     * @param exchange HTTP交换对象
     * @throws IOException IO异常
     */
    public void handleLogout(HttpExchange exchange) throws IOException {
        // 从Cookie获取会话ID
        String sessionId = null;
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies != null) {
            for (String cookie : cookies) {
                String[] pairs = cookie.split(";");
                for (String pair : pairs) {
                    String[] keyValue = pair.trim().split("=");
                    if (keyValue.length == 2 && keyValue[0].equals("session")) {
                        sessionId = keyValue[1];
                        break;
                    }
                }
            }
        }
        
        // 移除会话
        if (sessionId != null) {
            sessionManager.removeSession(sessionId);
        }
        
        // 清除Cookie
        exchange.getResponseHeaders().add("Set-Cookie", "session=; Path=/; Max-Age=0");
        
        // 发送响应
        sendResponse(exchange, 200, "{\"success\":true,\"message\":\"已成功注销\"}");
    }
    
    /**
     * 获取会话管理器
     * @return 会话管理器
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }
    
    /**
     * 重新加载配置
     */
    public void reload() {
        logger.fine("正在重新加载认证系统配置...");
        Map<String, Object> newConfig = loadConfig();
        
        // 更新用户列表
        if (newConfig.containsKey("users")) {
            synchronized (users) {
                users.clear();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> newUsers = (List<Map<String, Object>>) newConfig.get("users");
                if (newUsers != null) {
                    users.addAll(newUsers);
                }
            }
        }
        
        // 更新认证配置
        if (newConfig.containsKey("authentication")) {
            synchronized (authConfig) {
                authConfig.clear();
                @SuppressWarnings("unchecked")
                Map<String, Object> newAuthConfig = (Map<String, Object>) newConfig.get("authentication");
                if (newAuthConfig != null) {
                    authConfig.putAll(newAuthConfig);
                }
            }
            
            // 更新会话超时设置
            int newSessionTimeout = getSessionTimeout();
            sessionManager.setSessionTimeout(newSessionTimeout);
        }
        
        logger.fine("认证系统配置已重新加载，共加载 " + users.size() + " 个用户账号");
    }
    
    /**
     * 增加登录失败计数
     * @param username 用户名
     */
    private void incrementFailureCount(String username) {
        int currentCount = loginFailureCount.getOrDefault(username, 0);
        loginFailureCount.put(username, currentCount + 1);
    }
    
    /**
     * 获取当前失败计数
     * @param username 用户名
     * @return 失败次数
     */
    private int getFailureCount(String username) {
        return loginFailureCount.getOrDefault(username, 0);
    }
    
    /**
     * 重置失败计数
     * @param username 用户名
     */
    private void resetFailureCount(String username) {
        loginFailureCount.remove(username);
        accountLockTime.remove(username);
    }
    
    /**
     * 锁定账户
     * @param username 用户名
     */
    private void lockAccount(String username) {
        long lockUntil = System.currentTimeMillis() + (getLockoutDuration() * 60 * 1000);
        accountLockTime.put(username, lockUntil);
    }
    
    /**
     * 检查账户是否被锁定
     * @param username 用户名
     * @return 是否锁定
     */
    private boolean isAccountLocked(String username) {
        Long lockTime = accountLockTime.get(username);
        if (lockTime == null) {
            return false;
        }
        
        // 检查锁定是否已过期
        if (System.currentTimeMillis() > lockTime) {
            // 锁定时间已过，解锁账户
            accountLockTime.remove(username);
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取剩余锁定时间（分钟）
     * @param username 用户名
     * @return 剩余锁定时间（分钟）
     */
    private long getRemainingLockTime(String username) {
        Long lockTime = accountLockTime.get(username);
        if (lockTime == null) {
            return 0;
        }
        
        long remainingMs = lockTime - System.currentTimeMillis();
        if (remainingMs <= 0) {
            return 0;
        }
        
        return (remainingMs / (60 * 1000)) + 1; // 向上取整分钟数
    }
} 