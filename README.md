# ServerInfo插件 - 技术文档

## 1. 项目概述

ServerInfo（之前称为PlayerInfo）是一个适用于Bukkit/Spigot和BungeeCord平台的Minecraft服务器插件，提供用于显示玩家信息的集成网页界面。其主要特点是将后端逻辑、网页服务器和前端界面打包到单个JAR文件中的一体化部署架构。

### 主要特点

- **双模式运行**：同时支持Spigot（单服务器）和BungeeCord（代理）环境
- **一体化部署**：除Java外无需外部依赖
- **嵌入式网页服务器**：内置HTTP服务器用于提供网页界面
- **Minecraft风格UI**：原生游戏风格的玩家信息查看界面
- **跨服务器支持**：查看网络中多个服务器的玩家数据
- **零配置**：使用合理的默认设置即可开箱即用

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

插件自动检测其运行环境（Spigot或BungeeCord）并初始化适当的组件：

- **Spigot模式**：从服务器收集数据，并托管本地网页服务器或将数据转发到BungeeCord
- **BungeeCord模式**：接收来自连接的Spigot服务器的数据，并提供集中式网页界面

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

插件序列化玩家数据并通过消息通道定期发送：

```java
// 从Spigot向BungeeCord发送数据
private void sendPlayerData(Player player) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(stream);
    
    try {
        // 写入子通道名称
        out.writeUTF("playerinfo:channel");
        
        // 写入服务器标识符
        out.writeUTF(serverName);
        
        // 序列化玩家数据
        out.writeUTF(player.getName());
        // 写入额外的玩家数据...
        
        // 通过BungeeCord通道发送
        player.sendPluginMessage(this, "BungeeCord", stream.toByteArray());
    } catch (IOException e) {
        getLogger().severe("发送玩家数据失败：" + e.getMessage());
    }
}
```

### 4.4 数据压缩

为解决超过Minecraft消息大小限制（32,766字节）的问题，插件使用GZIP压缩大型数据：

```java
// 发送可能压缩的玩家数据
private void sendPlayerData(Player player) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(stream);
    
    try {
        // 准备玩家数据
        String playerDataJson = preparePlayerData(player);
        byte[] playerDataBytes = playerDataJson.getBytes(StandardCharsets.UTF_8);
        
        // 检查数据大小并在需要时压缩
        if (playerDataBytes.length > 30000) { // 设定阈值，小于实际限制32766
            // 使用GZIP压缩
            byte[] compressedData = CompressionUtil.compress(playerDataBytes);
            
            if (compressedData.length > 32000) {
                getLogger().severe("玩家数据过大，即使压缩后仍超过限制：" + compressedData.length + " 字节");
                return;
            }
            
            // 发送压缩数据，添加标记表示已压缩
            out.writeUTF("playerinfo:channel");
            out.writeUTF(serverName);
            out.writeUTF("COMPRESSED:" + player.getUniqueId().toString());
            out.writeInt(compressedData.length);
            out.write(compressedData);
            
            if (debug) {
                getLogger().info("发送压缩数据：原始 " + playerDataBytes.length + 
                                " 字节，压缩后 " + compressedData.length + 
                                " 字节，压缩率 " + 
                                String.format("%.2f", (1 - (double)compressedData.length / playerDataBytes.length) * 100) + "%");
            }
        } else {
            // 发送未压缩数据
            out.writeUTF("playerinfo:channel");
            out.writeUTF(serverName);
            out.writeUTF(player.getUniqueId().toString());
            out.writeUTF(playerDataJson);
            
            if (debug) {
                getLogger().info("发送未压缩数据：" + playerDataBytes.length + " 字节");
            }
        }
        
        // 通过BungeeCord通道发送
        player.sendPluginMessage(this, "BungeeCord", stream.toByteArray());
    } catch (IOException e) {
        getLogger().severe("发送玩家数据失败：" + e.getMessage());
    }
}
```

## 5. 配置系统

### 5.1 Spigot配置（config.yml）

```yaml
# 网页服务器设置
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

# 消息设置
messaging:
  channel: "playerinfo:channel"
  data-send-interval: 30

# 调试模式
debug: false
```

### 5.2 BungeeCord配置（bungee_config.yml）

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

## 6. 网页服务器实现

### 6.1 服务器初始化

```java
// 网页服务器初始化
webServer = new WebServer(webServerPort, playerController, this);
webServer.start();
```

### 6.2 关键组件

- **HTTP请求处理器**：处理API端点和静态资源的传入请求
- **API控制器**：处理来自前端的数据请求
- **静态资源服务器**：提供HTML、CSS、JS和图像文件
- **安全控制**：IP过滤和访问控制

## 7. 资源管理

### 7.1 资源提取

插件在启动期间从JAR文件中提取嵌入的资源到文件系统：

```java
// 提取网页资源
extractResource("web/index.html", new File(webDir, "index.html"));
extractResource("web/css/style.css", new File(webDir, "css/style.css"));
extractResource("web/js/app.js", new File(webDir, "js/app.js"));
extractResource("web/js/skinViewer.js", new File(webDir, "js/skinViewer.js"));
```

### 7.2 自动资源下载

对于缺失的资源，插件可以从互联网下载：

```java
private boolean downloadIconWithRetry(String url, File targetFile, int maxRetries) {
    // 带重试机制的HTTP下载实现
    // ...
}
```

## 8. 前端技术实现

### 8.1 物品详情模态系统

```javascript
// 模态窗口管理
function openItemDetailModal(itemData) {
    const modal = document.getElementById('itemDetailModal');
    const content = document.getElementById('modalContent');
    
    // 填充内容
    content.innerHTML = generateItemHTML(itemData);
    
    // 显示并添加动画
    modal.style.display = 'block';
    setTimeout(() => {
        modal.classList.add('active');
    }, 10);
}

function closeModal() {
    const modal = document.getElementById('itemDetailModal');
    
    // 关闭动画
    modal.classList.remove('active');
    setTimeout(() => {
        modal.style.display = 'none';
    }, 300);
}
```

### 8.2 服务器选择器（BungeeCord模式）

```javascript
// 服务器选择功能
function updateServerList() {
    fetch('/api/servers')
        .then(response => response.json())
        .then(data => {
            renderServerList(data);
        });
}

function selectServer(serverName) {
    currentServer = serverName;
    fetch(`/api/players?server=${serverName}`)
        .then(response => response.json())
        .then(data => {
            updatePlayerList(data);
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

### 10.1 自定义BungeeCord API

插件使用位于`D:\aicore\ServerInfo\libs\wybc`的自定义BungeeCord API进行BungeeCord集成：

```java
// BungeeCord API集成
// 注意：使用自定义API而非标准BungeeCord API
public void initBungeeIntegration() {
    // 使用自定义API初始化
    // 实现细节取决于自定义API结构
}
```

## 11. 技术挑战与解决方案

### 11.1 跨服务器数据同步

**挑战**：高效同步多个服务器之间的玩家数据。

**解决方案**：周期性更新和事件触发更新的结合：
- 定期数据传输（可配置，默认：30秒）
- 重要事件（加入、退出、物品栏变化）触发即时更新
- 优化数据序列化以最小化网络流量

### 11.2 资源管理

**挑战**：在不同环境中管理网页资源。

**解决方案**：
- 带时间戳检查的一次性提取
- 仅在有新版本可用时更新文件
- 更新期间保留用户自定义内容

### 11.3 API兼容性

**挑战**：使用自定义BungeeCord API。

**解决方案**：
- 使用适配器模式抽象API交互
- 缺失功能的回退机制
- 全面的错误处理

### 11.4 数据大小限制

**挑战**：处理超过Minecraft插件消息大小限制（32,766字节）的玩家数据。

**解决方案**：
- 实现GZIP压缩以减小大型数据的大小（压缩率约65-75%）
- 为小数据包维持未压缩传输以优化性能
- 自动检测数据大小并应用适当的压缩策略
- 提供详细的日志记录以监控压缩效率

### 11.5 跨服务器玩家在线状态同步

**挑战**：当玩家从一个子服切换到另一个子服时，Web界面会错误地显示玩家同时在多个服务器在线。

**问题原因**：
- 当玩家切换服务器时，玩家退出事件没有正确清理BungeeCord中的数据
- 新服务器发送的玩家数据与旧服务器的数据同时存在
- 数据映射中没有机制来检测和处理同一玩家在多个服务器中的情况

**解决方案**：
- **数据主动修正**：实现`correctPlayerServerData`方法，主动检查并修正玩家数据
- **数据一致性维护**：当收到玩家数据时，自动从其他服务器中移除此玩家的记录
- **周期性数据清理**：设置较短的数据过期时间（60秒），定期清理过期数据
- **详细日志记录**：增加关键操作的日志记录，便于问题诊断

**实现细节**：
```java
// 玩家服务器数据修正方法
public void correctPlayerServerData(UUID playerUUID, String currentServer) {
    // 检查玩家是否在其他服务器的数据中存在
    for (String serverName : playerDataMap.keySet()) {
        if (!serverName.equals(currentServer)) {
            Map<UUID, String> serverPlayers = playerDataMap.get(serverName);
            if (serverPlayers != null && serverPlayers.containsKey(playerUUID)) {
                // 从其他服务器移除此玩家
                serverPlayers.remove(playerUUID);
                lastUpdateTimeMap.get(serverName).remove(playerUUID);
            }
        }
    }
}
```

**调用时机**：
- 每次接收到PlayerData消息时
- 每次请求玩家列表或服务器列表时执行数据清理

**效果**：
- 确保每个玩家在任意时刻只存在于一个服务器的数据中
- Web界面始终显示玩家的当前真实位置
- 解决了玩家"幽灵显示"问题

## 12. 数据压缩系统

### 12.1 压缩原理

插件使用GZIP压缩算法处理大型数据包，该算法特别适合JSON等文本数据：

```java
// 压缩工具类
public class CompressionUtil {
    // 使用最高压缩级别（9）进行GZIP压缩
    public static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(baos) {
            { def.setLevel(9); } // 设置最高压缩级别
        }) {
            gzipOutputStream.write(data);
        }
        return baos.toByteArray();
    }
    
    // 解压GZIP数据
    public static byte[] decompress(byte[] compressedData) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedData))) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipInputStream.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
        }
        return baos.toByteArray();
    }
}
```

### 12.2 压缩效率

根据数据类型，GZIP压缩可以实现以下压缩率：

- **普通玩家数据**：约70-80%的压缩率
- **丰富的物品栏数据**：约65-75%的压缩率
- **带有大量附魔的数据**：约60-70%的压缩率

压缩率计算公式：
```
压缩率 = (1 - 压缩后大小/原始大小) * 100%
```

### 12.3 压缩流程

1. 检查序列化的玩家数据大小
2. 如果数据超过30KB阈值，应用GZIP压缩
3. 在传输数据前添加压缩标记
4. 在接收端检测压缩标记并解压数据

### 12.4 BungeeCord端处理

BungeeCord端实现了智能检测和处理压缩数据的能力：

```java
// 处理来自Spigot服务器的消息
@EventHandler
public void onPluginMessage(PluginMessageEvent event) {
    if (!event.getTag().equals("BungeeCord")) {
        return;
    }
    
    try {
        // 读取消息数据
        ByteArrayInputStream stream = new ByteArrayInputStream(event.getData());
        DataInputStream in = new DataInputStream(stream);
        
        String subChannel = in.readUTF();
        if (!subChannel.equals(channelName)) {
            return;
        }
        
        String serverName = in.readUTF();
        String playerIdStr = in.readUTF();
        
        // 检查是否为压缩数据
        if (playerIdStr.startsWith("COMPRESSED:")) {
            String playerUUIDStr = playerIdStr.substring("COMPRESSED:".length());
            int compressedLength = in.readInt();
            byte[] compressedData = new byte[compressedLength];
            in.readFully(compressedData);
            
            // 解压数据
            byte[] decompressedData = CompressionUtil.decompress(compressedData);
            String playerData = new String(decompressedData, StandardCharsets.UTF_8);
            
            // 处理玩家数据
            processPlayerData(serverName, playerUUIDStr, playerData);
            
            if (debug) {
                logger.info("接收到压缩数据：原始 " + decompressedData.length + 
                           " 字节，压缩后 " + compressedData.length + 
                           " 字节，压缩率 " + 
                           String.format("%.2f", (1 - (double)compressedData.length / decompressedData.length) * 100) + "%");
            }
        } else {
            // 处理未压缩数据
            String playerData = in.readUTF();
            processPlayerData(serverName, playerIdStr, playerData);
            
            if (debug) {
                logger.info("接收到未压缩数据：" + playerData.getBytes(StandardCharsets.UTF_8).length + " 字节");
            }
        }
    } catch (IOException e) {
        logger.severe("处理插件消息时出错：" + e.getMessage());
    }
}
```

## 13. 自定义占位符系统

### 13.1 功能概述

自定义占位符功能允许服务器管理员通过Placeholder.yml配置文件动态自定义显示的信息内容，实现服务器特色信息的灵活展示。系统集成了PlaceholderAPI，能够显示任何已安装扩展的占位符数值。

### 13.2 主要特点

- **完全自定义**：管理员可以自由配置显示的信息种类、图标、单位名称
- **PlaceholderAPI集成**：支持服务器上所有已安装的PlaceholderAPI扩展
- **动态布局**：信息条目过多时自动添加滚动条
- **实时更新**：支持热重载，配置更改后无需重启
- **跨服数据同步**：BungeeCord环境下实现跨服数据传输
- **中文支持**：使用nameMapping实现ID到显示名称的映射

### 13.3 配置文件

系统使用`Placeholder.yml`配置文件定义显示的占位符：

```yaml
# 更多信息区域占位符配置
placeholders:
  # 经济信息（默认内置）
  economy:
    enabled: true
    icon: "gold_ingot"
    placeholder: "%vault_eco_balance%"
    name: "金币"
    priority: 10
  
  # 点券信息（默认内置）
  points:
    enabled: true
    icon: "emerald"
    placeholder: "%playerpoints_points%"
    name: "点券"
    priority: 20
```

### 13.4 名称映射系统

使用`namemap.cnf`文件提供ID到中文显示名称的映射：

```json
{
    "economy": "金币",
    "points": "点券",
    "attack": "攻击力",
    "defense": "物理防御",
    "health": "生命上限",
    "mana": "魔力值"
}
```

### 13.5 技术实现

- **PlaceholderManager类**：加载配置文件、处理占位符解析
- **PlaceholderData类**：封装占位符数据
- **placeholder.js**：前端处理和显示
- **nameMapping**：解决中文编码问题

## 14. DragonCore物品支持

### 14.1 功能概述

ServerInfo插件支持与DragonCore插件集成，可以获取和显示玩家的DragonCore容器物品，为RPG服务器提供了额外的物品展示功能。DragonCore是一个提供自定义物品栏和GUI功能的插件，广泛用于RPG服务器中。

### 14.2 主要特点

- **DragonCore集成**：自动检测并与DragonCore插件集成
- **容器物品获取**：使用反射安全地获取玩家的DragonCore容器物品
- **物品序列化**：将DragonCore物品转换为前端可显示的格式
- **跨服数据传输**：支持在BungeeCord环境下传输DragonCore物品数据
- **详细日志**：提供详细的日志记录，方便调试和问题排查

### 14.3 技术实现

- **反射机制**：使用Java反射API安全地调用DragonCore插件的方法，避免直接依赖
```java
// 尝试获取DragonCore API类
Class<?> slotAPIClass = Class.forName("eos.moe.dragoncore.api.SlotAPI");
// 获取getCacheAllSlotItem方法
java.lang.reflect.Method getCacheAllSlotItem = slotAPIClass.getMethod("getCacheAllSlotItem", Player.class);
// 调用静态方法获取物品
Map<String, ItemStack> result = (Map<String, ItemStack>) getCacheAllSlotItem.invoke(null, player);
```

- **物品过滤**：跳过AIR类型的物品，只序列化有效物品
```java
if (entry.getValue() != null && !entry.getValue().getType().name().equals("AIR")) {
    dragonCoreItems.put(entry.getKey(), ItemStackSerializer.serializeItemStack(entry.getValue()));
} else {
    logger.info("[DragonCore调试] 跳过AIR类型物品: " + entry.getKey());
}
```

- **数据序列化**：在PlayerData模型中添加DragonCore容器数据字段
```java
// DragonCore容器数据
private Map<String, Map<String, Object>> dragonCore;

public Map<String, Map<String, Object>> getDragonCore() {
    return dragonCore;
}

public void setDragonCore(Map<String, Map<String, Object>> dragonCore) {
    this.dragonCore = dragonCore;
}
```

- **JSON序列化**：在ItemStackSerializer中添加DragonCore数据的序列化支持
```java
// 添加DragonCore数据到JSON结果
if (playerData.getDragonCore() != null && !playerData.getDragonCore().isEmpty()) {
    result.put("dragonCore", playerData.getDragonCore());
    System.out.println("[DragonCore调试] 已添加DragonCore数据到序列化结果");
} else {
    System.out.println("[DragonCore调试] 玩家没有DragonCore数据或数据为空，跳过序列化");
}
```

### 14.4 前端集成

前端实现了对DragonCore物品的特殊处理和显示：

- **物品检测**：检测JSON数据中是否包含DragonCore字段
- **物品渲染**：根据DragonCore物品的特性，使用专门的UI元素显示
- **物品交互**：支持点击DragonCore物品查看详情
- **调试信息**：提供DragonCore数据分析和调试信息，方便排查问题

### 14.5 故障排查

DragonCore物品数据可能因多种原因而不显示：

- **DragonCore插件未安装或未启用**：日志会显示`[DragonCore调试] DragonCore插件未安装或未启用`
- **API调用失败**：可能出现`[DragonCore调试] 调用DragonCore API时出错`的日志
- **JSON序列化问题**：需要在ItemStackSerializer中正确序列化DragonCore数据
- **玩家没有DragonCore物品**：会显示`[DragonCore调试] 玩家没有DragonCore物品或数据为空`

## 15. 开发路线图

### 当前阶段
- 双模式运行与无缝转换
- 跨服务器玩家信息查看
- 基本服务器选择界面
- DragonCore物品集成

### 计划增强
- 通过身份验证增强安全性
- 高级过滤和搜索功能
- 针对大型服务器网络的性能优化
- 移动界面改进

## 16. 结论

ServerInfo插件代表了Minecraft服务器中玩家信息可视化的一种复杂方法。通过利用嵌入式网页服务器和跨服务器通信通道，它为服务器管理员和玩家提供了无缝的体验。

双模式架构使其能够在单服务器环境和复杂的多服务器网络中运行，使其适应各种部署场景。数据压缩系统确保即使是具有丰富物品栏和附魔的玩家数据也能高效传输，而不会超过Minecraft的消息大小限制。同时，插件还支持DragonCore物品数据的展示，增强了对RPG服务器的支持。

## 17. 项目文件结构

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
│       │               ├── controller/  # API控制器
│       │               ├── messaging/   # 消息通道系统
│       │               ├── model/       # 数据模型
│       │               │   └── PlaceholderData.java # 占位符数据模型
│       │               ├── plugin/      # 插件核心
│       │               ├── util/        # 工具类
│       │               │   ├── PlaceholderManager.java # 占位符管理器
│       │               │   └── ItemStackSerializer.java # 物品序列化工具
│       │               ├── web/         # 网页服务相关
│       │               │   └── WebAuthFilter.java   # Web认证过滤器
│       │               ├── Bootstrap.java       # 启动引导类
│       │               ├── PlayerInfo.java      # 插件主类
│       │               └── PlayerInfoMain.java  # 入口点
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
│           │   ├── dashboard.html     # 玩家信息页面(原index.html)
│           │   ├── index.html         # 重定向到登录或仪表盘
│           │   ├── login.html         # 登录页面
│           │   ├── namemap.cnf        # 占位符名称映射文件
│           │   └── *.ps1              # PowerShell资源下载脚本
│           ├── bungee.yml         # BungeeCord插件配置
│           ├── bungee_config.yml  # BungeeCord模式配置
│           ├── config.yml         # Spigot模式配置
│           ├── passwd.yml         # 认证配置模板
│           ├── Placeholder.yml    # 占位符配置文件
│           └── plugin.yml         # Bukkit插件配置
├── target/                        # 编译输出目录
├── dependency-reduced-pom.xml     # Maven优化后的POM
├── pom.xml                        # Maven项目配置
├── README.md                      # 项目说明
├── READMENEW.md                   # 技术文档
├── Placeholder.md                 # 占位符功能说明
├── ServerInfo-Phase1-Summary.md   # 阶段性总结
└── UPDATE.md                      # 更新记录
```

### 17.1 核心文件描述

#### 主要类文件

- **Bootstrap.java**: 环境检测和初始化引导
- **PlayerInfo.java**: 插件核心实现
- **PlayerInfoMain.java**: 程序入口点

#### 核心包结构

- **auth/**: 用户认证和会话管理
- **bukkit/**: Bukkit服务器端相关实现
- **bungee/**: BungeeCord服务器端相关实现
- **command/**: 插件命令处理
- **controller/**: Web API控制器
- **messaging/**: 跨服务器通信系统
- **model/**: 数据模型定义
  - **PlaceholderData.java**: 占位符数据模型，存储自定义占位符信息
- **plugin/**: 插件核心功能
- **util/**: 通用工具类
  - **EconomyManager.java**: 经济系统管理器，负责与Vault API交互
  - **PointsManager.java**: 点券系统管理器，负责与PlayerPoints API交互
  - **PlaceholderManager.java**: 占位符管理器，负责与PlaceholderAPI交互
  - **ItemStackSerializer.java**: 物品序列化工具
- **web/**: 嵌入式Web服务器和认证过滤器

#### 配置文件

- **config.yml**: Spigot端配置
- **bungee_config.yml**: BungeeCord端配置
- **plugin.yml**: Bukkit插件描述（已添加PlaceholderAPI软依赖）
- **bungee.yml**: BungeeCord插件描述
- **passwd.yml**: 用户认证配置
- **Placeholder.yml**: 占位符配置模板

#### 网页资源

- **index.html**: 主页重定向
- **login.html**: 登录页面
- **dashboard.html**: 玩家信息页面（支持自定义占位符显示）
- **namemap.cnf**: 占位符名称映射配置
- **css/**: 样式文件
- **js/**: JavaScript逻辑
  - **auth.js**: 认证相关脚本
  - **app.js**: 主应用脚本（支持处理占位符数据）
  - **placeholder.js**: 占位符处理专用脚本
  - **skinViewer.js**: 皮肤查看器脚本
- **img/**: 图像资源
- **src/**: 前端源代码
- **download_*.ps1**: 资源下载脚本

#### 依赖库

- **libs/Bukkit/**: Bukkit API依赖
- **libs/wybc/**: 自定义BungeeCord API
- **spigot-1.12.2.jar**: Spigot服务器依赖
- **BungeeCord.jar**: BungeeCord依赖
- **PlaceholderAPI.jar**: PlaceholderAPI依赖，提供占位符解析功能
- **PlayerPoints.jar**: PlayerPoints点券插件依赖
- **Vault.jar**: Vault经济插件依赖 