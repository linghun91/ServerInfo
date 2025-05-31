package cn.i7mc.playerinfo.auth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 会话管理器
 * 处理用户登录状态和会话
 */
public class SessionManager {
    private static final Logger logger = Logger.getLogger("SessionManager");
    
    // 存储会话信息：会话ID -> 会话数据
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();
    
    // 存储用户最后一次访问时间：会话ID -> 最后访问时间
    private final Map<String, Long> lastAccess = new ConcurrentHashMap<>();
    
    // 会话有效期（分钟）
    private long sessionTimeout;
    
    /**
     * 会话数据类
     */
    public static class SessionData {
        private final String username;
        private final String permission;
        
        public SessionData(String username, String permission) {
            this.username = username;
            this.permission = permission;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getPermission() {
            return permission;
        }
    }
    
    /**
     * 构造函数
     * @param sessionTimeoutMinutes 会话超时时间（分钟）
     */
    public SessionManager(long sessionTimeoutMinutes) {
        this.sessionTimeout = sessionTimeoutMinutes * 60 * 1000; // 转换为毫秒
        
        // 启动定期清理过期会话的线程
        Thread cleanupThread = new Thread(this::cleanupExpiredSessions);
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
    
    /**
     * 创建新会话
     * @param username 用户名
     * @param permission 权限级别
     * @return 会话ID
     */
    public String createSession(String username, String permission) {
        String sessionId = UUID.randomUUID().toString();
        SessionData sessionData = new SessionData(username, permission);
        
        sessions.put(sessionId, sessionData);
        updateLastAccess(sessionId);
        
        logger.fine("为用户 " + username + " 创建新会话: " + sessionId);
        return sessionId;
    }
    
    /**
     * 验证会话有效性
     * @param sessionId 会话ID
     * @return 会话是否有效
     */
    public boolean isValidSession(String sessionId) {
        if (sessionId == null) {
            logger.fine("会话验证失败: sessionId为null");
            return false;
        }
        
        if (!sessions.containsKey(sessionId)) {
            logger.fine("会话验证失败: 会话不存在, ID: " + sessionId);
            return false;
        }
        
        Long lastAccessTime = lastAccess.get(sessionId);
        if (lastAccessTime == null) {
            logger.fine("会话验证失败: 无法获取上次访问时间, ID: " + sessionId);
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastAccess = currentTime - lastAccessTime;
        boolean isValid = timeSinceLastAccess < sessionTimeout;
        
        if (isValid) {
            SessionData data = sessions.get(sessionId);
            logger.fine("会话验证成功: ID: " + sessionId + ", 用户: " + (data != null ? data.getUsername() : "unknown"));
            updateLastAccess(sessionId);
        } else {
            // 会话已过期，移除
            SessionData data = sessions.get(sessionId);
            logger.fine("会话已过期: ID: " + sessionId + ", 用户: " + (data != null ? data.getUsername() : "unknown") + 
                      ", 过期时间: " + (timeSinceLastAccess / 1000) + "秒 (超时设置: " + (sessionTimeout / 1000) + "秒)");
            sessions.remove(sessionId);
            lastAccess.remove(sessionId);
        }
        
        return isValid;
    }
    
    /**
     * 获取会话数据
     * @param sessionId 会话ID
     * @return 会话数据，如果会话无效则返回null
     */
    public SessionData getSessionData(String sessionId) {
        if (!isValidSession(sessionId)) {
            return null;
        }
        return sessions.get(sessionId);
    }
    
    /**
     * 移除会话（注销）
     * @param sessionId 会话ID
     */
    public void removeSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        
        SessionData data = sessions.remove(sessionId);
        lastAccess.remove(sessionId);
        
        if (data != null) {
            logger.fine("用户 " + data.getUsername() + " 的会话已注销: " + sessionId);
        }
    }
    
    /**
     * 更新会话的最后访问时间
     * @param sessionId 会话ID
     */
    private void updateLastAccess(String sessionId) {
        lastAccess.put(sessionId, System.currentTimeMillis());
    }
    
    /**
     * 设置会话超时时间
     * @param sessionTimeoutMinutes 会话超时时间（分钟）
     */
    public void setSessionTimeout(long sessionTimeoutMinutes) {
        this.sessionTimeout = sessionTimeoutMinutes * 60 * 1000; // 转换为毫秒
        logger.fine("会话超时时间已更新为 " + sessionTimeoutMinutes + " 分钟");
    }
    
    /**
     * 清理过期会话
     */
    private void cleanupExpiredSessions() {
        while (true) {
            try {
                Thread.sleep(30 * 60 * 1000); // 每30分钟检查一次
                
                long now = System.currentTimeMillis();
                int cleaned = 0;
                
                // 遍历所有会话，移除过期的
                for (Map.Entry<String, Long> entry : lastAccess.entrySet()) {
                    String sessionId = entry.getKey();
                    long lastAccessTime = entry.getValue();
                    
                    if ((now - lastAccessTime) >= sessionTimeout) {
                        SessionData data = sessions.remove(sessionId);
                        lastAccess.remove(sessionId);
                        cleaned++;
                        
                        if (data != null) {
                            logger.fine("清理过期会话: " + sessionId + " (用户: " + data.getUsername() + ")");
                        }
                    }
                }
                
                if (cleaned > 0) {
                    logger.fine("已清理 " + cleaned + " 个过期会话");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warning("清理过期会话时出错: " + e.getMessage());
            }
        }
    }
} 