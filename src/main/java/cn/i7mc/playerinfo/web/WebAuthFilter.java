package cn.i7mc.playerinfo.web;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import cn.i7mc.playerinfo.auth.AuthController;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Web认证过滤器
 * 拦截未认证的请求并重定向到登录页面
 */
public class WebAuthFilter extends Filter {
    private final AuthController authController;
    private final List<String> publicPaths;
    
    /**
     * 构造函数
     * @param authController 认证控制器
     */
    public WebAuthFilter(AuthController authController) {
        this.authController = authController;
        this.publicPaths = new ArrayList<>();
        
        // 添加公开路径（不需要认证）
        publicPaths.add("/login.html");
        publicPaths.add("/css/login.css");
        publicPaths.add("/js/auth.js");
        publicPaths.add("/api/auth/login");
        publicPaths.add("/api/auth/check");
        publicPaths.add("/images/");
        publicPaths.add("/favicon.ico");
    }
    
    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        // 如果认证未启用，直接放行
        if (!authController.isAuthenticationEnabled()) {
            chain.doFilter(exchange);
            return;
        }
        
        // 获取请求路径
        String path = exchange.getRequestURI().getPath();
        
        // 检查是否是公开路径
        if (isPublicPath(path)) {
            chain.doFilter(exchange);
            return;
        }
        
        // 检查会话是否有效
        if (isAuthenticated(exchange)) {
            // 会话有效，放行请求
            chain.doFilter(exchange);
        } else {
            // 会话无效，重定向到登录页面
            if (path.startsWith("/api/")) {
                // API请求返回401状态码
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(401, 0);
                exchange.getResponseBody().write("{\"error\":\"Unauthorized\",\"message\":\"请先登录\"}".getBytes());
                exchange.getResponseBody().close();
            } else {
                // 页面请求重定向到登录页面
                URI requestUri = exchange.getRequestURI();
                String redirectUrl = "/login.html";
                
                // 添加重定向参数
                if (!path.equals("/") && !path.equals("/index.html")) {
                    redirectUrl += "?redirect=" + path;
                }
                
                exchange.getResponseHeaders().add("Location", redirectUrl);
                exchange.sendResponseHeaders(302, -1);
            }
        }
    }
    
    /**
     * 检查路径是否是公开路径
     * @param path 请求路径
     * @return 是否是公开路径
     */
    private boolean isPublicPath(String path) {
        // 检查完全匹配
        if (publicPaths.contains(path)) {
            return true;
        }
        
        // 检查前缀匹配
        for (String publicPath : publicPaths) {
            if (publicPath.endsWith("/") && path.startsWith(publicPath)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 获取会话ID
     * @param exchange HTTP交换
     * @return 会话ID
     */
    public static String getSessionId(HttpExchange exchange) {
        // 从Cookie中获取会话ID
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader != null) {
            for (String cookie : cookieHeader.split(";")) {
                cookie = cookie.trim();
                if (cookie.startsWith("session=")) {
                    return cookie.substring(8);
                }
            }
        }
        return null;
    }
    
    /**
     * 检查请求是否已认证
     * @param exchange HTTP交换对象
     * @return 是否已认证
     */
    public boolean isAuthenticated(HttpExchange exchange) {
        String sessionId = getSessionId(exchange);
        return sessionId != null && authController.isValidSession(sessionId);
    }
    
    /**
     * 检查是否已通过验证
     * @param exchange HTTP交换
     * @param authController 授权控制器
     * @return 是否已通过验证
     */
    public static boolean isAuthenticated(HttpExchange exchange, AuthController authController) {
        // 获取请求会话ID
        String sessionId = getSessionId(exchange);
        if (sessionId == null) {
            return false;
        }
        
        // 检查会话是否有效
        return authController.isValidSession(sessionId);
    }
    
    @Override
    public String description() {
        return "Web认证过滤器";
    }
} 