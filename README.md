# ServerInfo插件 - 技术文档

## 1. 项目概述

ServerInfo(PlayerInfo)是一个适用于Bukkit/Spicgt和BungeeCord平台的Minecraft服务器插件，提供用于显示玩家信息的集成网页界面。其主要特点是将后端逻辑、网页服务器和前端界面打包到单个JAR文件中的一体化部署架构。

### 主要特点

- **双模式运行**：同时支持Spigot（单服务器）和BungeeCord（代理）环境
- **一体化部署**：除Java外无需外部依赖
- **嵌入式网页服务器**：内置HTTP服务器用于提供网页界面
- **Minecraft风格UI**：原生游戏风格的玩家信息查看界面
- **跨服务器支持**：查看网络中多个服务器的玩家数据
- **零配置**：使用合理的默认设置即可开箱即用（但认证系统默认为关闭状态）

## 2. 架构设计

ServerInfo插件使用多层架构：

```
ServerInfo插件
├── 核心系统
│   ├── 环境检测
│   ├── 双模式初始化
│   ├── 配置管理
│   └── 资源处理
├── 后端组件
│   ├── 数据收集（玩家状态、物品栏等）
│   ├── 事件监听器
│   ├── 跨服务器消息传递
│   └── 命令系统
├── 网页服务
│   ├── 嵌入式HTTP服务器
│   ├── API控制器
│   ├── 资源服务
│   └── 安全控制
└── 前端界面
    ├── HTML/CSS结构
    ├── JavaScript逻辑
    ├── 3D皮肤查看器
    ├── 物品详情系统
    └── 自定义占位符显示
```

## 3. 双模式运行

### 3.1 环境检测

插件通过Bootstrap.java自动检测其运行环境（Spigot或BungeeCord），并初始化适当的组件：

- **Spigot模式**：从服务器收集数据，并托管本地网页服务器或将数据转发到BungeeCord
- **BungeeCord模式**：接收来自连接的Spigot服务器的数据，并提供集中式网页界面

环境检测使用Java的反射机制，通过尝试加载特定类来判断当前环境：
```java
boolean isBukkit = isClassAvailable(BUKKIT_CLASS); // org.bukkit.plugin.java.JavaPlugin
boolean isBungee = isClassAvailable(BUNGEE_CLASS); // net.md_5.bungee.api.plugin.Plugin
```

### 3.2 初始化过程

```java
// 双模式初始化的伪代码
public void onEnable() {
    // 根据环境加载适当的配置
    if (isBungeeCordEnvironment()) {
        // 初始化BungeeCord组件
        initBungeeMode();
    } else {
        // 初始化Spigot组件
        initSpigotMode();
    }
    
    // 两种模式的通用初始化
    initCommonComponents();
}
```

Bootstrap.java通过适配器模式加载对应环境的实现：
```java
// 检查插件实例类型
if (plugin.getClass().getName().contains("bungee")) {
    logger.info("§3基于插件类型判断为 BungeeCord 环境");
    return loadAdapter(BUNGEE_ADAPTER, plugin, dataFolder);
} else {
    logger.info("§3基于插件类型判断为 Bukkit/Spigot 环境");
    return loadAdapter(BUKKIT_ADAPTER, plugin, dataFolder);
}
```

## 4. 消息通道系统

### 4.1 通道配置

插件使用专用消息通道进行Spigot服务器和BungeeCord代理之间的通信：

**通道名称**：`playerinfo:channel`

这个通道名称在两种环境中都是可配置的：

```yaml
# 来自bungee_config.yml
messaging:
  channel: "playerinfo:channel"

# 来自config.yml（Spigot端）
messaging:
  channel: "playerinfo:channel"
  data-send-interval: 30  # 数据同步间隔（秒）
```

### 4.2 通道注册

```java
// Spigot端通道注册
getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);

// 监听传入数据
@Override
public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    if (!channel.equals("BungeeCord")) {
        return;
    }
    // 处理消息...
}
```

### 4.3 数据传输

插件序列化玩家数据并通过消息通道定期发送。实际实现中，MessageSender类负责此功能：

```java
// 从Spigot向BungeeCord发送数据
public void sendPlayerData(Player player, Map<String, Object> playerData) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(stream);
    
    try {
        // 写入子通道名称
        out.writeUTF("playerinfo:channel");
        
        // 写入服务器标识符
        out.writeUTF(serverName);
        
        // 序列化玩家数据成JSON
        String playerDataJson = gson.toJson(playerData);
        
        // 写入玩家UUID和数据
        out.writeUTF(player.getUniqueId().toString());
        out.writeUTF(playerDataJson);
        
        // 通过BungeeCord通道发送
        player.sendPluginMessage(plugin, "BungeeCord", stream.toByteArray());
        
        if (debug) {
            logger.info("已发送玩家数据: " + player.getName() + ", 数据大小: " + 
                       playerDataJson.getBytes(StandardCharsets.UTF_8).length + " 字节");
        }
    } catch (IOException e) {
        logger.severe("发送玩家数据失败：" + e.getMessage());
    }
}
```

### 4.4 数据压缩

对于大型数据包，项目设计了数据压缩机制，但在现有代码中未发现实际压缩实现的完整细节。设计中提到的GZIP压缩方法可能需要在后续版本中实现，以解决大型数据传输问题。

## 5. 配置系统

### 5.1 Spigot配置（config.yml - 39行）

```yaml
# Web服务器设置
web-server:
  port: 25581
  allow-external-access: true

# BungeeCord集成
bungeecord:
  enabled: true
  server:
    name: ""  # 如果为空则自动检测

# 独立设置
standalone:
  web-server: false  # 即使在BungeeCord模式下也运行本地网页服务器

# 认证系统设置
authentication:
  enabled: false  # 是否启用Web界面认证系统

# 消息设置
messaging:
  channel: "playerinfo:channel"
  data-send-interval: 30

# 调试模式
debug: false
```

### 5.2 BungeeCord配置（bungee_config.yml - 30行）

```yaml
# 调试模式
debug: false

# 网页服务器设置
web-server:
  port: 25581
  allow-external-access: true

# 消息设置
messaging:
  channel: "playerinfo:channel"
```

### 5.3 认证配置（passwd.yml - 23行）

```yaml
# ServerInfo认证配置
# 此文件用于配置Web界面的访问认证

# 是否启用认证系统
enabled: false

# 访问凭据
credentials:
  admin: "adminpassword"  # 用户名: 密码（明文）
  user: "userpassword"
  
# 会话设置
sessions:
  # 会话超时时间（秒）
  timeout: 1800  # 30分钟
  
  # 会话Cookie设置
  cookie:
    # 是否使用安全Cookie（仅HTTPS）
    secure: false
    # 是否仅允许HTTP访问Cookie
    httpOnly: true
```

## 6. 网页服务器实现

### 6.1 服务器初始化

WebServer类(432行)负责创建和管理HTTP服务器实例：

```java
webServer = new WebServer(webServerPort, playerController, this);
webServer.start();
```

实际初始化过程中，WebServer使用Java内置的HttpServer类：

```java
server = HttpServer.create();

// 根据配置决定绑定的地址
if (allowExternalAccess) {
    // 绑定到所有网络接口，允许外部访问
    server.bind(new InetSocketAddress(port), 0);
} else {
    // 仅绑定到本地回环接口，仅允许本机访问
    server.bind(new InetSocketAddress("127.0.0.1", port), 0);
}
```

### 6.2 路由注册

WebServer会注册一系列路由处理器：

```java
// 注册API路由
server.createContext("/api/players", playerController);
server.createContext("/api/player", playerController);
server.createContext("/api/cache-skin", new SkinCacheHandler(plugin));

// 认证相关API端点
if (authController != null) {
    server.createContext("/api/auth/login", exchange -> authController.handleLogin(exchange));
    server.createContext("/api/auth/check", exchange -> authController.handleCheckSession(exchange));
    server.createContext("/api/auth/logout", exchange -> authController.handleLogout(exchange));
    
    // 静态文件路由，加入认证过滤器
    HttpContext context = server.createContext("/", new StaticFileHandler(plugin));
    context.getFilters().add(new WebAuthFilter(authController));
} else {
    // 没有认证的静态文件路由
    server.createContext("/", new StaticFileHandler(plugin));
}
```

### 6.3 PlayerController的HTTP处理

PlayerController类(1166行)实现了HttpHandler接口，可以直接响应HTTP请求：

```java
@Override
public void handle(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    
    // 处理CORS
    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
    exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
    
    if (exchange.getRequestMethod().equals("OPTIONS")) {
        exchange.sendResponseHeaders(204, -1);
        return;
    }
    
    // 解析路径参数
    if (path.startsWith("/api/players")) {
        handleGetAllPlayers(exchange);
    } else if (path.startsWith("/api/player")) {
        handleGetPlayer(exchange);
    } else {
        send404(exchange);
    }
}
```

### 6.4 WebAuthFilter实现

WebAuthFilter类(154行)实现了HttpFilter接口，用于拦截Web请求并验证认证状态：

```java
@Override
public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
    // 检查请求是否需要认证
    if (needsAuthentication(exchange)) {
        // 查看Cookie判断认证状态
        String sessionId = extractSessionId(exchange);
        if (sessionId != null && authController.isValidSession(sessionId)) {
            // 会话有效，继续处理
            chain.doFilter(exchange);
        } else {
            // 未认证，重定向到登录页面
            redirectToLogin(exchange);
        }
    } else {
        // 不需要认证的资源（如登录页面）
        chain.doFilter(exchange);
    }
}
```

## 7. 资源管理

### 7.1 资源提取

PlayerInfo.java中的extractWebResources方法负责从JAR文件中提取网页资源：

```java
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
    
    // CSS文件
    extractResource("web/css/style.css", new File(cssDir, "style.css"));
    
    // JS文件
    extractResource("web/js/app.js", new File(jsDir, "app.js"));
    extractResource("web/js/skinViewer.js", new File(jsDir, "skinViewer.js"));
}
```

### 7.2 资源文件说明

项目使用了几个重要的配置文件来支持界面显示：

1. **itemIcons.cnf** (268行) - 物品ID到图标路径的映射:
```
DIAMOND = diamond.png
IRON_INGOT = iron_ingot.png
GOLDEN_APPLE = golden_apple.png
...
```

2. **itemTranslations.cnf** (333行) - 物品ID到中文名称的映射:
```
DIAMOND = 钻石
IRON_INGOT = 铁锭
GOLDEN_APPLE = 金苹果
...
```

3. **namemap.cnf** (11行) - 占位符ID到显示名称的映射:
```json
{
    "economy": "金币",
    "points": "点券",
    "attack": "攻击力",
    "defense": "物理防御"
}
```

## 8. 前端技术实现

### 8.1 皮肤查看器实现

项目使用skinview3d库实现3D皮肤查看：

```javascript
// 初始化皮肤查看器
function initSkinViewer(uuid) {
    const skinViewer = new skinview3d.SkinViewer({
        canvas: document.getElementById("skin_container"),
        width: 250,
        height: 300,
        skin: `/api/cache-skin?uuid=${uuid}`
    });
    
    // 设置皮肤查看器姿势和动画
    skinViewer.camera.position.z = 70;
    skinViewer.controls.enableRotate = true;
    
    // 添加动画
    const walk = skinViewer.animations.add(skinview3d.WalkingAnimation);
    walk.speed = 0.5;
}
```

### 8.2 物品详情模态系统

物品详情显示通过模态窗口实现：

```javascript
// 模态窗口管理
function openItemDetailModal(itemData) {
    const modal = document.getElementById('itemDetailModal');
    const content = document.getElementById('modalContent');
    
    // 生成物品HTML
    let html = `<h3>${itemData.name || "未知物品"}</h3>`;
    
    if (itemData.lore) {
        html += `<div class="item-lore">`;
        itemData.lore.forEach(line => {
            html += `<p>${line}</p>`;
        });
        html += `</div>`;
    }
    
    // 显示模态窗口
    content.innerHTML = html;
    modal.style.display = "block";
}
```

### 8.3 占位符处理

前端使用placeholder.js处理服务器返回的占位符数据：

```javascript
// 处理占位符数据
function renderPlaceholders(placeholderData) {
    const container = document.getElementById("placeholder-container");
    container.innerHTML = "";
    
    // 排序占位符（按优先级）
    const sortedPlaceholders = Object.keys(placeholderData)
        .map(key => ({
            id: key,
            ...placeholderData[key]
        }))
        .sort((a, b) => a.priority - b.priority);
    
    // 渲染每个占位符
    sortedPlaceholders.forEach(placeholder => {
        // 获取映射的名称
        const name = nameMapping[placeholder.id] || placeholder.id;
        
        // 创建占位符元素
        const div = document.createElement("div");
        div.className = "placeholder-item";
        
        // 图标
        const icon = document.createElement("img");
        icon.src = `img/icons/${placeholder.icon}.png`;
        icon.alt = name;
        
        // 名称和值
        const nameSpan = document.createElement("span");
        nameSpan.className = "placeholder-name";
        nameSpan.textContent = name;
        
        const valueSpan = document.createElement("span");
        valueSpan.className = "placeholder-value";
        valueSpan.textContent = placeholder.value;
        
        // 组装元素
        div.appendChild(icon);
        div.appendChild(nameSpan);
        div.appendChild(valueSpan);
        
        container.appendChild(div);
    });
}
```

## 9. 异步操作

插件广泛使用异步操作以避免阻塞主服务器线程：

```java
// 异步资源处理
getServer().getScheduler().runTaskAsynchronously(this, () -> {
    // 在后台线程中执行资源提取
    copyResourcesFromJar(webDir);
    
    // 如果需要，下载缺失的资源
    downloadMissingIcons(webDir);
    
    // 返回主线程以启动网页服务器
    getServer().getScheduler().runTask(this, () -> {
        startWebServer();
    });
});
```

## 10. API集成

### 10.1 PlaceholderAPI集成

ServerInfo与PlaceholderAPI集成，支持自定义占位符：

```java
// 检查PlaceholderAPI状态
public boolean isPlaceholderAPIAvailable() {
    return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
}

// 处理占位符
public String processPlaceholders(String text, Player player) {
    if (isPlaceholderAPIAvailable() && player != null) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
    return text;
}
```

### 10.2 自定义BungeeCord API

项目设计中提到使用自定义BungeeCord API，但在当前代码实现中没有看到明确的引用。这可能是计划中或预留的接口，需要在后续版本中实现。

## 11. 技术挑战与解决方案

### 11.1 跨服务器数据同步

通过消息通道系统实现了跨服务器数据同步，重点是事件触发的更新和周期性更新的结合：

```java
// PlayerInfo.java中的周期性数据更新
private void startDataRefreshTask() {
    int interval = plugin.getConfig().getInt("messaging.data-send-interval", 30) * 20;
    dataRefreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
        if (messageSender != null) {
            // 遍历所有在线玩家，发送他们的数据
            for (Player player : Bukkit.getOnlinePlayers()) {
                messageSender.sendPlayerData(player);
            }
        }
    }, 40, interval); // 延迟2秒后启动，然后按配置的间隔运行
}
```

### 11.2 资源管理

资源提取时的版本控制和更新机制：

```java
private void extractResource(String resourcePath, File targetFile) {
    try (InputStream in = plugin.getResource(resourcePath)) {
        if (in == null) {
            logger.warning("无法在JAR中找到资源: " + resourcePath);
            return;
        }
        
        if (!targetFile.exists() || isResourceNewer(resourcePath, targetFile)) {
            // 确保父目录存在
            if (!targetFile.getParentFile().exists()) {
                targetFile.getParentFile().mkdirs();
            }
            
            // 复制资源
            Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("已提取资源: " + resourcePath + " 到 " + targetFile.getPath());
        }
    } catch (Exception e) {
        logger.severe("提取资源时出错: " + resourcePath + " - " + e.getMessage());
    }
}
```

### 11.3 跨服务器玩家在线状态同步

PlayerController中实现了玩家服务器数据的修正，确保玩家不会同时显示在多个服务器上：

```java
public void correctPlayerServerData(UUID playerUUID, String currentServer) {
    for (Map.Entry<String, Map<UUID, PlayerData>> entry : serverPlayerData.entrySet()) {
        String serverName = entry.getKey();
        if (!serverName.equals(currentServer)) {
            Map<UUID, PlayerData> players = entry.getValue();
            if (players.containsKey(playerUUID)) {
                logger.info("修正数据: 玩家 " + playerUUID + " 已从服务器 " + serverName + " 移除");
                players.remove(playerUUID);
            }
        }
    }
}
```

## 12. 安全性考虑

### 12.1 认证系统

认证系统虽然默认关闭，但提供了基本的安全保护：

```java
// WebAuthFilter中的认证检查
private boolean needsAuthentication(HttpExchange exchange) {
    String path = exchange.getRequestURI().getPath();
    
    // 登录相关页面不需要认证
    if (path.equals("/login.html") || 
        path.startsWith("/api/auth/") ||
        path.startsWith("/css/") ||
        path.startsWith("/js/")) {
        return false;
    }
    
    return true;
}
```

### 12.2 安全最佳实践

为提高安全性，建议服务器管理员：

1. 启用认证系统: 在passwd.yml中设置`enabled: true`
2. 使用强密码: 避免使用默认或弱密码
3. 限制外部访问: 在config.yml中设置`allow-external-access: false`来限制仅本地访问
4. 定期更换密码: 定期更新passwd.yml中的凭据
5. 使用反向代理: 考虑使用Nginx或Apache作为安全的前端代理

## 13. 开发路线图

### 当前阶段
- 双模式运行与无缝转换
- 跨服务器玩家信息查看
- 基本服务器选择界面
- 基础占位符系统

### 计划增强
- 数据压缩系统完整实现
- DragonCore物品支持完善
- 高级过滤和搜索功能
- 针对大型服务器网络的性能优化
- 移动界面改进

## 14. 结论

ServerInfo插件代表了Minecraft服务器中玩家信息可视化的一种复杂方法。通过利用嵌入式网页服务器和跨服务器通信通道，它为服务器管理员和玩家提供了无缝的体验。

双模式架构使其能够在单服务器环境和复杂的多服务器网络中运行，使其适应各种部署场景。虽然数据压缩系统的设计考虑了大型数据的传输需求，但在当前版本中可能尚未完全实现。

同时，插件通过Web界面提供了直观的玩家数据查看体验，支持自定义占位符显示，为服务器管理提供了有力工具。

## 15. 项目文件结构

项目整体文件结构如下：

```
ServerInfo/
├── .vscode/                       # Visual Studio Code配置目录
├── libs/                          # 第三方依赖库
│   ├── Bukkit/                    # Bukkit相关依赖
│   ├── wybc/                      # 自定义BungeeCord API
│   ├── BungeeCord.jar             # BungeeCord依赖
│   ├── PlaceholderAPI.jar         # PlaceholderAPI依赖
│   └── spigot-1.12.2.jar          # Spigot服务器依赖
├── src/                           # 源代码目录
│   └── main/                      # 主要源代码
│       ├── java/                  # Java源代码
│       │   └── cn/
│       │       └── i7mc/
│       │           └── playerinfo/   # 主包
│       │               ├── auth/        # 认证相关类
│       │               │   ├── AuthController.java  # 认证控制器
│       │               │   ├── PasswordHash.java    # 密码哈希工具
│       │               │   └── SessionManager.java  # 会话管理
│       │               ├── bukkit/      # Bukkit平台相关实现
│       │               ├── bungee/      # BungeeCord平台相关实现
│       │               ├── command/     # 命令系统
│       │               │   └── PlayerInfoCommand.java # 主命令处理类(160行)
│       │               ├── controller/  # API控制器
│       │               │   └── PlayerController.java # 玩家数据控制器(1166行)
│       │               ├── messaging/   # 消息通道系统
│       │               │   └── MessageSender.java   # 跨服消息发送器
│       │               ├── model/       # 数据模型
│       │               │   ├── PlayerData.java      # 玩家数据模型(254行)
│       │               │   └── PlaceholderData.java # 占位符数据模型(137行)
│       │               ├── plugin/      # 插件核心
│       │               ├── util/        # 工具类
│       │               │   ├── PlaceholderManager.java # 占位符管理器
│       │               │   └── ItemStackSerializer.java # 物品序列化工具
│       │               ├── web/         # 网页服务相关
│       │               │   ├── WebAuthFilter.java   # Web认证过滤器(154行)
│       │               │   └── WebServer.java       # 嵌入式Web服务器(432行)
│       │               ├── Bootstrap.java       # 启动引导类(155行)
│       │               ├── PlayerInfo.java      # 插件主类(540行)
│       │               └── PlayerInfoMain.java  # 入口点(35行)
│       └── resources/             # 资源文件
│           ├── web/               # 网页资源
│           │   ├── css/           # 样式表
│           │   │   ├── login.css      # 登录页面样式
│           │   │   └── style.css      # 主页面样式
│           │   ├── img/           # 图片资源
│           │   ├── js/            # JavaScript文件
│           │   │   ├── app.js         # 主应用脚本
│           │   │   ├── auth.js        # 认证相关脚本
│           │   │   ├── placeholder.js # 占位符处理脚本
│           │   │   └── skinViewer.js  # 皮肤查看器脚本
│           │   ├── src/           # 前端源文件
│           │   ├── DefaultSkin.png    # 默认皮肤图
│           │   ├── dashboard.html     # 玩家信息页面(270行)
│           │   ├── index.html         # 重定向到登录或仪表盘(103行)
│           │   ├── login.html         # 登录页面(46行)
│           │   ├── namemap.cnf        # 占位符名称映射文件(11行)
│           │   ├── itemTranslations.cnf # 物品翻译配置文件(333行)
│           │   └── itemIcons.cnf      # 物品图标配置文件(268行)
│           ├── bungee.yml         # BungeeCord插件配置(10行)
│           ├── bungee_config.yml  # BungeeCord模式配置(30行)
│           ├── config.yml         # Spigot模式配置(39行)
│           ├── passwd.yml         # 认证配置模板(23行)
│           ├── Placeholder.yml    # 占位符配置文件(78行)
│           └── plugin.yml         # Bukkit插件配置(23行)
├── pom.xml                        # Maven项目配置
├── README.md                      # 项目说明
└── UPDATE.md                      # 更新记录
```

### 15.1 核心文件描述

#### 主要类文件

- **Bootstrap.java** (155行): 环境检测和初始化引导，通过反射自动检测运行环境并选择适当的适配器
- **PlayerInfo.java** (540行): 插件核心实现，负责初始化各组件，管理Web服务器和玩家数据
- **PlayerInfoMain.java** (35行): 程序入口点，负责检测环境并加载对应插件实现
- **PlayerController.java** (1166行): 核心控制器，处理玩家数据收集和API响应
- **WebServer.java** (432行): 嵌入式HTTP服务器，提供Web界面和API服务
- **WebAuthFilter.java** (154行): HTTP认证过滤器，保护Web资源免受未授权访问

#### 核心包结构

- **auth/**: 用户认证和会话管理，负责Web界面的安全访问控制
- **bukkit/**: Bukkit服务器端相关实现，处理Spigot/Bukkit服务器的集成
- **bungee/**: BungeeCord服务器端相关实现，处理BungeeCord网关的集成
- **command/**: 插件命令处理，注册和响应游戏内命令
- **controller/**: Web API控制器，提供HTTP端点处理玩家数据请求
- **messaging/**: 跨服务器通信系统，用于BungeeCord集群环境下数据同步
- **model/**: 数据模型定义
  - **PlayerData.java**: 玩家数据模型，封装玩家信息（位置、物品、属性等）
  - **PlaceholderData.java**: 占位符数据模型，存储自定义占位符信息
- **plugin/**: 插件核心功能，包含Bukkit和BungeeCord的具体实现
- **util/**: 通用工具类
  - **PlaceholderManager.java**: 占位符管理器，负责与PlaceholderAPI交互并解析自定义占位符
  - **ItemStackSerializer.java**: 物品序列化工具，将物品数据转换为JSON格式

#### 配置文件

- **config.yml** (39行): Spigot端配置，包含Web服务器设置、BungeeCord模式和认证选项
- **bungee_config.yml** (30行): BungeeCord端配置，包含BungeeCord特有设置
- **plugin.yml** (23行): Bukkit插件描述（包含PlaceholderAPI软依赖）和命令定义
- **bungee.yml** (10行): BungeeCord插件描述
- **passwd.yml** (23行): 用户认证配置，存储Web界面访问密码
- **Placeholder.yml** (78行): 占位符配置文件，用于定义自定义占位符

#### 网页资源

- **index.html** (103行): 主页重定向，检查认证状态并跳转到登录或仪表盘
- **login.html** (46行): 登录页面，提供Web认证界面
- **dashboard.html** (270行): 玩家信息页面（支持自定义占位符显示）
- **namemap.cnf** (11行): 占位符ID到显示名称的映射文件
- **itemTranslations.cnf** (333行): 物品ID到显示名称的翻译映射
- **itemIcons.cnf** (268行): 物品ID到图标路径的映射配置
- **css/**: 样式文件
- **js/**: JavaScript逻辑
  - **auth.js**: 认证相关脚本
  - **app.js**: 主应用脚本
  - **placeholder.js**: 占位符处理专用脚本
  - **skinViewer.js**: 皮肤查看器脚本