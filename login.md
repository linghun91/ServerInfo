# ServerInfo 登录验证系统 - 产品规划文档

## 一、需求概述

为ServerInfo插件增加Web页面登录验证系统，提升服务器管理安全性。此系统将保持Minecraft原版风格界面，实现无缝集成，确保用户体验一致性。

## 二、功能需求

### 1. 登录界面
- **设计风格**：与现有玩家物品栏查看页面保持一致，采用原版Minecraft风格
- **界面元素**：
  - 用户名输入框（类似Minecraft命令方块UI）
  - 密码输入框（带掩码显示）
  - 登录按钮（样式类似Minecraft按钮）
  - 可选的"记住我"选项
  - 适当的错误提示区域

### 2. 认证系统
- **账号管理**：通过passwd.yml配置文件管理用户账号和密码
- **验证机制**：服务端验证用户输入凭据与配置匹配
- **会话管理**：成功登录后创建有效会话，避免重复登录

### 3. 配置文件
- **文件名称**：passwd.yml
- **生成时机**：插件启动时自动检查并生成（无论BungeeCord端还是Spigot子服端）
- **配置选项**：
  - 用户账号列表（支持多账号）
  - 密码存储（考虑加密存储）
  - 会话超时设置
  - 登录尝试限制（可选）

## 三、技术实现要点

### 1. 前端实现
- **登录页面**：创建新的index.html作为登录入口，将原index.html重命名为dashboard.html
- **样式设计**：复用现有Minecraft风格CSS，确保一致的用户体验
- **表单验证**：客户端基本验证（非空、格式等）
- **AJAX请求**：使用JavaScript发送异步登录请求
- **状态处理**：根据服务器响应显示成功/失败消息
- **重定向逻辑**：登录成功后跳转到玩家信息页面

### 2. 后端实现
- **API端点**：新增`/api/auth/login`处理登录请求
- **配置解析**：读取passwd.yml中的用户信息
- **密码验证**：比对提交的密码与存储密码
- **会话生成**：创建安全会话标识（如JWT或session cookie）
- **访问控制**：拦截未认证请求，重定向至登录页面
- **会话验证**：验证会话有效性和过期状态

### 3. 配置文件设计
```yaml
# passwd.yml 示例结构
authentication:
  enabled: true                   # 是否启用认证
  session-timeout: 1440           # 会话有效期（分钟）
  max-login-attempts: 5           # 最大尝试次数（可选）
  lockout-duration: 30            # 锁定时间（分钟，可选）

users:
  - username: admin               # 管理员用户名
    password: "hashed_password"   # 密码（应存储为哈希值）
    permission: admin             # 权限级别
  
  - username: viewer              # 普通查看用户
    password: "hashed_password"   # 密码（应存储为哈希值）
    permission: view              # 权限级别
```

## 四、实施流程

### 阶段一：基础设计与准备
1. 创建登录页面HTML/CSS设计
2. 设计passwd.yml配置结构
3. 实现配置文件自动生成逻辑

### 阶段二：后端功能实现
1. 开发配置文件解析器
2. 实现密码哈希和验证逻辑
3. 创建认证API端点
4. 开发会话管理系统

### 阶段三：前端实现
1. 实现登录表单和客户端验证
2. 开发AJAX登录请求机制
3. 实现登录状态处理
4. 开发页面重定向逻辑

### 阶段四：集成与测试
1. 集成前后端认证流程
2. 测试登录成功/失败场景
3. 验证会话持久性和超时机制
4. 安全测试（防止暴力破解等）

## 五、技术要点与知识点

### 1. 安全相关
- **密码存储**：应使用单向哈希（如bcrypt、Argon2）存储密码，避免明文
- **防暴力破解**：实现登录尝试限制和IP封锁机制
- **XSS防护**：对用户输入进行适当转义
- **CSRF防护**：实现CSRF令牌验证

### 2. 用户体验
- **记住登录状态**：实现"记住我"功能，延长会话有效期
- **友好错误提示**：提供清晰易懂的错误信息
- **加载指示器**：在登录处理过程中显示加载状态

### 3. 技术集成
- **会话处理**：可使用JWT或cookie-based session
- **配置加载**：在插件启动时加载passwd.yml
- **访问控制模式**：拦截器模式，验证每个受保护资源的访问

## 六、注意事项与风险

### 1. 关键注意点
- **配置文件生成**：确保在BungeeCord和Spigot端都能正确生成passwd.yml
- **默认账户**：首次生成配置文件时应创建默认管理员账户（用户名和密码记录在日志中）
- **页面跳转**：确保登录成功后能正确跳转到玩家信息页面，避免路径或重定向问题
- **会话验证**：所有API请求都需验证会话有效性

### 2. 潜在风险
- **配置文件权限**：确保passwd.yml具有适当的文件系统权限，防止未授权访问
- **会话劫持**：实现必要的安全措施防止会话劫持
- **密码复杂度**：考虑实施密码复杂度要求
- **兼容性问题**：确保在不同浏览器和设备上正常运行

### 3. 特殊场景处理
- **密码重置**：提供管理员重置密码的机制
- **多用户访问**：处理并发用户访问的情况
- **网络问题**：在网络不稳定情况下的重连和会话恢复机制

## 七、与现有系统集成

# ServerInfo项目文件结构

```
ServerInfo/
├── .vscode/                       # Visual Studio Code配置目录
├── libs/                          # 第三方依赖库
│   ├── Bukkit/                    # Bukkit相关依赖
│   ├── wybc/                      # 自定义BungeeCord API
│   ├── BungeeCord.jar             # BungeeCord依赖
│   └── spigot-1.12.2.jar          # Spigot服务器依赖
├── src/                           # 源代码目录
│   └── main/                      # 主要源代码
│       ├── java/                  # Java源代码
│       │   └── cn/
│       │       └── i7mc/
│       │           └── playerinfo/   # 主包
│       │               ├── auth/        # 新增: 认证相关类
│       │               │   ├── AuthController.java  # 认证控制器
│       │               │   ├── PasswordHash.java    # 密码哈希工具
│       │               │   └── SessionManager.java  # 会话管理
│       │               ├── bukkit/      # Bukkit平台相关实现
│       │               ├── bungee/      # BungeeCord平台相关实现
│       │               ├── command/     # 命令系统
│       │               ├── controller/  # API控制器
│       │               ├── messaging/   # 消息通道系统
│       │               ├── model/       # 数据模型
│       │               ├── plugin/      # 插件核心
│       │               ├── util/        # 工具类
│       │               ├── web/         # 网页服务相关
│       │               │   └── WebAuthFilter.java   # 新增: Web认证过滤器
│       │               ├── Bootstrap.java       # 启动引导类
│       │               ├── PlayerInfo.java      # 插件主类
│       │               └── PlayerInfoMain.java  # 入口点
│       └── resources/             # 资源文件
│           ├── web/               # 网页资源
│           │   ├── css/           # 样式表
│           │   │   ├── login.css      # 新增: 登录页面样式
│           │   │   └── style.css      # 主页面样式
│           │   ├── img/           # 图片资源
│           │   ├── js/            # JavaScript文件
│           │   │   ├── app.js         # 主应用脚本
│           │   │   ├── auth.js        # 新增: 认证相关脚本
│           │   │   └── skinViewer.js  # 皮肤查看器脚本
│           │   ├── src/           # 前端源文件
│           │   ├── DefaultSkin.png    # 默认皮肤图
│           │   ├── dashboard.html     # 新增: 玩家信息页面(原index.html)
│           │   ├── index.html         # 新增: 重定向到登录或仪表盘
│           │   ├── login.html         # 新增: 登录页面
│           │   └── *.ps1              # PowerShell资源下载脚本
│           ├── bungee.yml         # BungeeCord插件配置
│           ├── bungee_config.yml  # BungeeCord模式配置
│           ├── config.yml         # Spigot模式配置
│           ├── passwd.yml         # 新增: 认证配置模板
│           └── plugin.yml         # Bukkit插件配置
├── target/                        # 编译输出目录
├── dependency-reduced-pom.xml     # Maven优化后的POM
├── pom.xml                        # Maven项目配置
├── README.md                      # 项目说明
├── READMENEW.md                   # 技术文档
├── ServerInfo-Phase1-Summary.md   # 阶段性总结
└── UPDATE.md                      # 更新记录
```

### 2. API扩展
- 扩展WebServer类处理认证端点
- 添加会话验证中间件/过滤器
- 修改现有API端点，增加认证验证

### 3. 页面流程修改
- 修改页面加载逻辑，首先验证登录状态
- 未登录时重定向到登录页面
- 确保登录状态在页面间传递

## 八、兼容性与可扩展性考虑

### 1. 向下兼容
- 提供配置选项禁用认证功能，便于兼容现有部署
- 保留原有访问路径和API结构

### 2. 未来扩展
- 预留权限级别，为未来实现更细粒度的权限控制做准备
- 考虑支持OAuth/外部认证集成
- 为用户配置文件和个性化设置预留扩展点

## 九、验收标准

1. passwd.yml在Spigot和BungeeCord环境均能正确生成
2. 登录页面符合Minecraft风格设计要求
3. 输入正确凭据能成功登录并跳转到玩家信息页面
4. 错误凭据提供适当的错误提示
5. 会话在配置的时间内保持有效
6. 未登录用户无法访问受保护资源
7. 密码以安全方式存储（非明文）
8. 浏览器刷新不丢失登录状态

## 十、后续优化方向

1. 实现基于角色的访问控制
2. 添加登录历史记录
3. 支持双因素认证
4. 实现用户自助密码重置
5. 登录活动邮件通知 