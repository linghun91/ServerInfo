/*
 * app.js - Minecraft玩家信息展示应用
 * 版本: 1.3.0 (2024-03-05)
 * 移除了所有硬编码的物品图标和翻译映射，统一使用配置文件
 */

document.addEventListener('DOMContentLoaded', () => {
    console.log('页面加载完成，初始化应用... 版本: 1.3.0');

    // 初始化全局变量
    let currentServer = null;
    let timer = null;
    let isViewingPlayerDetails = false;
    let reconnectAttempts = 0;

    // 尝试确定当前页面的基础路径
    let basePath = './';
    // 如果页面URL中包含playerinfo，可能处于子目录
    if (window.location.pathname.includes('playerinfo')) {
        basePath = '../';
        console.log('检测到在playerinfo子目录，设置基础路径为:', basePath);
    }

    // 初始化PlaceholderManager
    if (window.PlaceholderManager) {
        window.PlaceholderManager.init();
        console.log('PlaceholderManager初始化完成');
    } else {
        console.warn('PlaceholderManager模块未定义，占位符功能可能不可用');
    }

    // 监听名称映射加载完成事件，刷新数据
    document.addEventListener('namemap-loaded', function() {
        console.log('检测到名称映射已加载，准备刷新当前玩家信息');
        // 如果当前正在查看玩家详情，重新获取当前玩家数据
        if (isViewingPlayerDetails) {
            const playerName = document.querySelector('.player-info-header h3')?.textContent;
            if (playerName) {
                console.log('重新加载玩家详情:', playerName);
                loadPlayerInfo(playerName, currentServer);
            }
        }
    });

    // 尝试加载物品配置文件
    console.log('开始尝试加载配置文件...');
    tryAllConfigPaths();

    const playerList = document.getElementById('playerList');
    const inventorySlots = document.getElementById('inventorySlots');
    const serverSelectorHeader = document.getElementById('serverSelectorHeader');
    const serverList = document.getElementById('serverList');

    // 服务器选择相关变量
    let servers = [];

    // 初始状态显示加载中
    playerList.innerHTML = '<div class="notice">正在加载服务器列表，请稍候...</div>';

    // 控制自动刷新的变量
    let lastServerListUpdate = 0;
    let lastPlayerListUpdate = 0;
    const SERVER_LIST_UPDATE_INTERVAL = 10000; // 减少为10秒，原来是30秒
    const PLAYER_LIST_UPDATE_INTERVAL = 5000; // 减少为5秒，原来是10秒

    // 用户交互状态标志
    let isUserInteracting = false;   // 用户是否正在与界面交互
    let userInteractionTimeout = null; // 用户交互超时计时器


    // 初始化应用
    // 立即更新服务器列表
    updateServerList();

    // 设置一个单一的定时器，管理所有更新
    setInterval(() => {
        const now = Date.now();

        // 如果用户正在查看玩家详情，暂停自动更新
        if (isViewingPlayerDetails) {
            return;
        }

        // 检查是否需要更新服务器列表
        if (now - lastServerListUpdate >= SERVER_LIST_UPDATE_INTERVAL) {
            updateServerList();
            lastServerListUpdate = now;
        }

        // 检查是否需要更新玩家列表
        if (now - lastPlayerListUpdate >= PLAYER_LIST_UPDATE_INTERVAL) {
            updatePlayerList(currentServer);
            lastPlayerListUpdate = now;
        }
    }, 5000); // 每5秒检查一次是否需要更新

    // 监听用户交互，标记交互状态
    function startUserInteraction() {
        isUserInteracting = true;

        // 清除之前的定时器
        if (userInteractionTimeout) {
            clearTimeout(userInteractionTimeout);
        }

        // 设置新的定时器，3秒后恢复自动刷新
        userInteractionTimeout = setTimeout(() => {
            isUserInteracting = false;
        }, 3000);
    }

    // 服务器选择器弹出事件
    serverSelectorHeader.addEventListener('click', () => {
        startUserInteraction();

        // 检查是否有服务器可选
        if (servers.length === 0) {
            // 更新服务器列表
            updateServerList();
        }

        serverList.classList.toggle('active');
        serverSelectorHeader.querySelector('.arrow-icon').classList.toggle('active');
    });

    // 监听滚动事件
    document.addEventListener('scroll', startUserInteraction);

    // 监听触摸事件
    document.addEventListener('touchstart', startUserInteraction);
    document.addEventListener('touchmove', startUserInteraction);

    // Minecraft color codes to CSS colors
    const colorCodes = {
        '0': '#000000', // Black
        '1': '#0000AA', // Dark Blue
        '2': '#00AA00', // Dark Green
        '3': '#00AAAA', // Dark Aqua
        '4': '#AA0000', // Dark Red
        '5': '#AA00AA', // Dark Purple
        '6': '#FFAA00', // Gold
        '7': '#AAAAAA', // Gray
        '8': '#555555', // Dark Gray
        '9': '#5555FF', // Blue
        'a': '#55FF55', // Green
        'b': '#55FFFF', // Aqua
        'c': '#FF5555', // Red
        'd': '#FF55FF', // Light Purple
        'e': '#FFFF55', // Yellow
        'f': '#FFFFFF', // White
    };

    // 物品图标URL基础路径
    const ITEM_ICONS_BASE_PATH = './img/items/';
    const BLOCK_ICONS_BASE_PATH = './img/blocks/';

    // 物品ID到图标文件名的映射和物品中英文名称映射
    let itemIconMapping = {};
    let itemTranslations = {};


    // 加载物品图标映射表
    function loadItemIconsMapping() {
        console.log('开始加载物品图标映射表...');
        return fetch('../itemIcons.cnf')
            .then(response => {
                console.log('物品图标映射表请求状态:', response.status);
                if (!response.ok) {
                    throw new Error('网络请求失败: ' + response.status);
                }
                return response.text();
            })
            .then(text => {
                console.log('物品图标映射表内容长度:', text.length);
                if (text.length < 10) {
                    console.warn('物品图标映射表内容过短，可能未正确加载:', text);
                }
                const lines = text.split('\n');
                console.log('物品图标映射表行数:', lines.length);
                lines.forEach(line => {
                    // 跳过注释和空行
                    if (line.trim().startsWith('#') || line.trim() === '') {
                        return;
                    }
                    const parts = line.split('=');
                    if (parts.length >= 2) {
                        const key = parts[0].trim();
                        const value = parts[1].trim();
                        itemIconMapping[key] = value;
                    }
                });
                console.log('物品图标映射表加载完成，包含', Object.keys(itemIconMapping).length, '个映射');
            })
            .catch(error => {
                console.error('加载物品图标映射表出错:', error);
                // 如果加载失败，至少提供一个空映射
                itemIconMapping = {};
            });
    }

    // 加载物品转译表
    function loadItemTranslations() {
        console.log('开始加载物品转译表...');
        return fetch('../itemTranslations.cnf')
            .then(response => {
                console.log('物品转译表请求状态:', response.status);
                if (!response.ok) {
                    throw new Error('网络请求失败: ' + response.status);
                }
                return response.text();
            })
            .then(text => {
                console.log('物品转译表内容长度:', text.length);
                if (text.length < 10) {
                    console.warn('物品转译表内容过短，可能未正确加载:', text);
                }
                const lines = text.split('\n');
                console.log('物品转译表行数:', lines.length);
                lines.forEach(line => {
                    // 跳过注释和空行
                    if (line.trim().startsWith('#') || line.trim() === '') {
                        return;
                    }
                    const parts = line.split('=');
                    if (parts.length >= 2) {
                        const key = parts[0].trim();
                        const value = parts[1].trim();
                        itemTranslations[key] = value;
                    }
                });
                console.log('物品转译表加载完成，包含', Object.keys(itemTranslations).length, '个映射');
            })
            .catch(error => {
                console.error('加载物品转译表出错:', error);
                // 如果加载失败，至少提供一个空映射
                itemTranslations = {};
            });
    }

    // 在页面加载完成后加载配置文件
    document.addEventListener('DOMContentLoaded', function() {
        console.log('页面加载完成，开始尝试加载配置文件...');

        // 尝试多个可能的路径
        const tryLoadConfigs = async () => {
            const paths = [
                './',              // 相对于当前页面
                '../',             // 上一级目录
                '/web/',           // 网站根目录下的web文件夹
                'web/',            // 相对根目录的web文件夹
                '/playerinfo/web/' // 特定的插件路径
            ];

            let loaded = false;

            for (const basePath of paths) {
                console.log('尝试从路径加载配置文件:', basePath);
                try {
                    // 尝试从当前路径加载图标映射
                    const iconResponse = await fetch(basePath + 'itemIcons.cnf');
                    if (!iconResponse.ok) {
                        console.log(`路径 ${basePath} 加载itemIcons.cnf失败:`, iconResponse.status);
                        continue;
                    }

                    // 尝试从当前路径加载翻译
                    const translationResponse = await fetch(basePath + 'itemTranslations.cnf');
                    if (!translationResponse.ok) {
                        console.log(`路径 ${basePath} 加载itemTranslations.cnf失败:`, translationResponse.status);
                        continue;
                    }

                    // 如果两个文件都可以加载，则处理它们
                    const iconText = await iconResponse.text();
                    const translationText = await translationResponse.text();

                    console.log(`成功从路径 ${basePath} 加载两个配置文件`);

                    // 处理图标映射
                    const iconLines = iconText.split('\n');
                    iconLines.forEach(line => {
                        if (line.trim().startsWith('#') || line.trim() === '') return;
                        const parts = line.split('=');
                        if (parts.length >= 2) {
                            const key = parts[0].trim();
                            const value = parts[1].trim();
                            itemIconMapping[key] = value;
                        }
                    });

                    // 处理翻译
                    const translationLines = translationText.split('\n');
                    translationLines.forEach(line => {
                        if (line.trim().startsWith('#') || line.trim() === '') return;
                        const parts = line.split('=');
                        if (parts.length >= 2) {
                            const key = parts[0].trim();
                            const value = parts[1].trim();
                            itemTranslations[key] = value;
                        }
                    });

                    console.log('图标映射加载完成，包含', Object.keys(itemIconMapping).length, '个映射');
                    console.log('翻译表加载完成，包含', Object.keys(itemTranslations).length, '个映射');

                    loaded = true;
                    break; // 成功加载，跳出循环
                } catch (error) {
                    console.error(`从路径 ${basePath} 加载配置时出错:`, error);
                }
            }

            if (!loaded) {
                console.error('无法从任何路径加载配置文件，将回退到原始方法');
                // 回退到原始的加载方法
                return Promise.all([loadItemIconsMapping(), loadItemTranslations()]);
            } else {
                return Promise.resolve();
            }
        };

        tryLoadConfigs()
            .then(() => {
                console.log('配置文件加载过程完成');
                console.log('itemIconMapping 大小:', Object.keys(itemIconMapping).length);
                console.log('itemTranslations 大小:', Object.keys(itemTranslations).length);

                // 尝试输出几个示例值进行验证
                const iconKeys = Object.keys(itemIconMapping);
                if (iconKeys.length > 0) {
                    console.log('图标映射示例:', iconKeys.slice(0, 3).map(k => `${k}=${itemIconMapping[k]}`));
                }

                const translationKeys = Object.keys(itemTranslations);
                if (translationKeys.length > 0) {
                    console.log('翻译示例:', translationKeys.slice(0, 3).map(k => `${k}=${itemTranslations[k]}`));
                }
            })
            .catch(error => {
                console.error('加载配置文件过程中出错:', error);
            });
    });

    // Translate item names
    function translateItemName(englishName, damage) {
        // 处理带有元数据/损害值的物品
        if (damage !== undefined && damage !== null && damage > 0) {
            // 尝试使用带有元数据的物品ID
            const itemWithDamage = `${englishName}:${damage}`;

            // 检查是否存在带有特定元数据的翻译
            if (itemTranslations[itemWithDamage]) {
                return itemTranslations[itemWithDamage];
            }
        }

        // 尝试使用基本物品名称
        return itemTranslations[englishName] || englishName;
    }

    // 获取物品图标的URL
    function getItemIconUrl(itemType, damage) {
        // 添加调试日志
        console.log(`获取图标: itemType=${itemType}, damage=${damage}`);

        // 处理带有元数据/损害值的物品
        if (damage !== undefined && damage !== null && damage > 0) {
            // 尝试使用带元数据的物品ID
            const itemWithDamage = `${itemType}:${damage}`;

            // 检查是否有带特定元数据的映射
            if (itemIconMapping[itemWithDamage]) {
                console.log(`找到带元数据的映射: ${itemWithDamage} -> ${itemIconMapping[itemWithDamage]}`);
                return ITEM_ICONS_BASE_PATH + itemIconMapping[itemWithDamage];
            }
        }

        // 检查物品是否存在于图标映射中
        if (itemIconMapping[itemType]) {
            console.log(`找到物品映射: ${itemType} -> ${itemIconMapping[itemType]}`);
            return ITEM_ICONS_BASE_PATH + itemIconMapping[itemType];
        }

        // 默认物品图标（禁止标志）
        console.log(`未找到图标映射: ${itemType}，使用默认图标`);
        return ITEM_ICONS_BASE_PATH + 'barrier.png';
    }

    // Convert Minecraft color codes to HTML
    function convertMinecraftColors(text) {
        if (!text) return '';

        // 预处理：将HTML实体编码的十六进制颜色代码转换为Minecraft格式
        // 将 &#RRGGBB 转换为 §#RRGGBB
        text = text.replace(/&(#[0-9A-Fa-f]{6})/g, '§$1');

        // Split the text into segments based on color codes
        const segments = text.split('§');
        if (segments.length === 1) return text; // No color codes found

        let html = segments[0]; // Add first segment without color

        // Process each colored segment
        for (let i = 1; i < segments.length; i++) {
            const segment = segments[i];

            if (segment.length > 0) {
                // 检查是否是十六进制颜色代码 (§#RRGGBB 格式)
                if (segment.startsWith('#')) {
                    // 检查是否是有效的十六进制颜色代码
                    const hexColorRegex = /^#[0-9A-Fa-f]{6}/;
                    const match = segment.match(hexColorRegex);

                    if (match) {
                        const hexColor = match[0]; // 包含#符号
                        const remainingText = segment.substring(hexColor.length);
                        html += `<span style="color: ${hexColor}">${remainingText}</span>`;
                    } else {
                        // 如果以#开头但不是有效的十六进制颜色
                        html += '#' + segment.substring(1);
                    }
                } else {
                    const colorCode = segment[0].toLowerCase();
                    const text = segment.substring(1);

                    if (colorCode in colorCodes) {
                        html += `<span style="color: ${colorCodes[colorCode]}">${text}</span>`;
                    } else if (colorCode === 'l') {
                        html += `<span style="font-weight: bold">${text}</span>`;
                    } else if (colorCode === 'n') {
                        html += `<span style="text-decoration: underline">${text}</span>`;
                    } else if (colorCode === 'o') {
                        html += `<span style="font-style: italic">${text}</span>`;
                    } else if (colorCode === 'k') {
                        html += `<span class="obfuscated">${text}</span>`;
                    } else if (colorCode === 'm') {
                        html += `<span style="text-decoration: line-through">${text}</span>`;
                    } else if (colorCode === 'r') {
                        html += `<span style="color: inherit; font-weight: normal; text-decoration: none; font-style: normal">${text}</span>`;
                    } else {
                        html += colorCode + text;
                    }
                }
            }
        }

        return html;
    }

    // 动态获取服务器地址
    const serverAddress = window.location.href.split('/')[2];
    console.log('服务器地址:', serverAddress);

    // Create inventory slots
    for (let i = 0; i < 36; i++) {
        const slot = document.createElement('div');
        slot.className = 'item-slot';
        slot.setAttribute('data-tooltip', '');
        inventorySlots.appendChild(slot);
    }

    // 更新服务器列表
    function updateServerList() {
        console.log('更新服务器列表...');

        // 设置最后更新时间戳
        lastServerListUpdate = Date.now();

        // 显示加载中
        if (servers.length === 0) {
            playerList.innerHTML = '<div class="loading">正在加载服务器列表...</div>';
        }

        fetch('/api/servers')
            .then(response => {
                if (!response.ok) {
                    throw new Error('HTTP错误，状态: ' + response.status);
                }
                return response.text();
            })
            .then(text => {
                if (!text || text.trim() === '') {
                    throw new Error('服务器返回了空响应');
                }

                try {
                    return JSON.parse(text);
                } catch (e) {
                    console.error("JSON解析错误:", e);
                    throw new Error('无法解析JSON响应: ' + e.message);
                }
            })
            .then(data => {
                console.log('获取到服务器列表:', data);

                // 过滤出有玩家在线的服务器（在线人数 >= 1）
                const allServers = data.servers || [];
                servers = allServers.filter(server => {
                    // 确保服务器对象有效且玩家数大于等于1
                    return server && typeof server.playerCount === 'number' && server.playerCount >= 1;
                });

                console.log('过滤后的服务器列表（仅显示有人在线的服务器）:', servers);

                // 计算总在线人数
                const totalPlayerCount = calculateTotalPlayerCount(allServers); // 使用所有服务器计算总人数

                // 更新标题显示总在线人数
                const playerListTitle = document.querySelector('.player-list h2');
                if (playerListTitle) {
                    playerListTitle.textContent = `在线玩家 (${totalPlayerCount})`;
                }

                // 检查服务器列表是否为空
                if (!servers || servers.length === 0) {
                    serverSelectorHeader.querySelector('h3').textContent = '选择子服: 暂无玩家在线';
                    playerList.innerHTML = '<div class="notice">当前没有玩家在线的服务器</div>';
                    return;
                }

                // 保存当前选择的服务器名称，用于后续保持选择状态
                const previouslySelectedServer = currentServer;

                // 保存serverList的当前滚动位置
                const scrollPosition = serverList.scrollTop;

                serverList.innerHTML = '';

                // 变量跟踪是否找到了之前选择的服务器
                let foundPreviousServer = false;

                // 显示每个服务器
                servers.forEach(server => {
                    // 确保服务器对象有效
                    if (!server || !server.name) {
                        console.warn('收到无效服务器数据:', server);
                        return;
                    }

                    const serverItem = document.createElement('div');
                    serverItem.className = 'server-item';
                    serverItem.innerHTML = `
                        <span class="server-name">${server.name}</span>
                        <span class="player-count">${server.playerCount || 0} 玩家</span>
                    `;

                    // 如果是之前选中的服务器，添加active类并标记为已找到
                    if (previouslySelectedServer === server.name) {
                        serverItem.classList.add('active');
                        foundPreviousServer = true;
                    }

                    // 点击选择服务器 - 使用函数声明来确保事件正确绑定
                    serverItem.onclick = function() {
                        console.log(`选择服务器: ${server.name}`);

                        // 移除所有项的active类
                        document.querySelectorAll('.server-item').forEach(item => {
                            item.classList.remove('active');
                        });

                        // 添加active类到当前项
                        serverItem.classList.add('active');

                        // 更新当前服务器
                        currentServer = server.name;
                        serverSelectorHeader.querySelector('h3').textContent = `选择子服: ${server.name}`;

                        // 重置玩家详情查看状态
                        isViewingPlayerDetails = false;

                        // 点击服务器项后关闭服务器列表
                        serverList.classList.remove('active');
                        serverSelectorHeader.querySelector('.arrow-icon').classList.remove('active');

                        // 强制更新玩家列表（忽略用户交互状态）
                        forceUpdatePlayerList(server.name);

                        // 标记用户正在交互，防止自动刷新干扰
                        startUserInteraction();

                        // 更新最后的玩家列表更新时间戳
                        lastPlayerListUpdate = Date.now();
                    };

                    serverList.appendChild(serverItem);
                });

                // 如果没有之前选择的服务器或者没有找到之前选择的服务器，并且有服务器可选
                if (!previouslySelectedServer && servers.length > 0) {
                    currentServer = servers[0].name;
                    const firstServerItem = document.querySelector('.server-item');
                    if (firstServerItem) {
                        firstServerItem.classList.add('active');
                        serverSelectorHeader.querySelector('h3').textContent = `选择子服: ${currentServer}`;
                        forceUpdatePlayerList(currentServer);
                        lastPlayerListUpdate = Date.now();
                    }
                }
                // 如果之前有选择但在新列表中未找到，保持当前选择不变
                else if (previouslySelectedServer && !foundPreviousServer) {
                    console.log(`之前选择的服务器 ${previouslySelectedServer} 在更新后未找到，保持当前选择状态`);
                    currentServer = previouslySelectedServer;
                }

                // 恢复滚动位置
                serverList.scrollTop = scrollPosition;
            })
            .catch(error => {
                console.error('获取服务器列表时发生错误:', error);
                // 错误时不清空列表，只添加错误信息
                const errorMsg = document.createElement('div');
                errorMsg.className = 'server-item error';
                errorMsg.textContent = '加载服务器列表失败';

                // 只有在列表为空时才添加错误信息
                if (serverList.children.length === 0) {
                    serverList.appendChild(errorMsg);

                    // 如果没有服务器数据，显示错误消息在玩家列表区域
                    if (servers.length === 0) {
                        playerList.innerHTML = '<div class="error">无法加载服务器列表，请检查连接或刷新页面</div>';
                        serverSelectorHeader.querySelector('h3').textContent = '选择子服: 加载失败';
                    }
                }

                // 30秒后自动重试
                setTimeout(() => {
                    updateServerList();
                }, 30000);
            });
    }

    // 计算所有服务器的总在线人数
    function calculateTotalPlayerCount(serverList) {
        if (!serverList || !Array.isArray(serverList)) {
            return 0;
        }

        return serverList.reduce((total, server) => {
            // 确保playerCount是数字，如果不是则默认为0
            const count = server && typeof server.playerCount === 'number' ? server.playerCount : 0;
            return total + count;
        }, 0);
    }

    // 更新玩家列表
    function updatePlayerList(server) {
        // 设置最后更新时间戳
        lastPlayerListUpdate = Date.now();

        // 检查是否有有效的服务器名称
        if (!server) {
            console.log('无有效服务器，跳过玩家列表更新');
            playerList.innerHTML = '<div class="notice">请先选择一个子服</div>';
            return;
        }

        // 如果正在查看玩家详情，则不更新玩家列表
        if (isViewingPlayerDetails) {
            console.log('正在查看玩家详情，暂停玩家列表更新');
            return;
        }

        // 如果用户正在交互，延迟更新
        if (isUserInteracting) {
            console.log('用户正在交互，延迟玩家列表更新');
            return;
        }

        console.log(`更新玩家列表 - 服务器: ${server || '所有服务器'}`);

        // 显示加载中
        playerList.innerHTML = '<div class="loading">正在加载玩家列表...</div>';

        // 构建API URL
        let apiUrl = '/api/players';
        if (server) {
            apiUrl += `?server=${encodeURIComponent(server)}`;
        }

        console.log(`请求玩家列表API: ${apiUrl}`);

        // 请求配置
        const fetchOptions = {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            },
            mode: 'same-origin',
            cache: 'no-cache',
            credentials: 'same-origin'
        };

        // 设置请求超时
        const timeout = setTimeout(() => {
            console.error('请求玩家列表超时');
            playerList.innerHTML = '<div class="error">加载玩家列表超时</div>';
        }, 10000);

        // 发起请求与超时竞赛
        Promise.race([
            fetch(apiUrl, fetchOptions),
            new Promise((_, reject) =>
                setTimeout(() => reject(new Error('请求超时')), 10000)
            )
        ])
        .then(response => {
            clearTimeout(timeout);
            console.log(`玩家列表API响应状态: ${response.status}`);

            if (!response.ok) {
                throw new Error('HTTP错误，状态: ' + response.status);
            }
            return response.text();
        })
        .then(text => {
            if (!text || text.trim() === '') {
                throw new Error('服务器返回了空响应');
            }

            try {
                return JSON.parse(text);
            } catch (e) {
                console.error("JSON解析错误:", e);
                throw new Error('无法解析JSON响应: ' + e.message);
            }
        })
        .then(data => {
            console.log('获取到玩家列表数据:', data);
            renderPlayerList(data);
        })
        .catch(error => {
            console.error('获取玩家列表时发生错误:', error);
            playerList.innerHTML = `<div class="error">加载玩家列表失败: ${error.message}</div>`;

            // 5秒后自动重试，但只有在不是查看玩家详情时
            setTimeout(() => {
                if (!isViewingPlayerDetails && currentServer === server) {
                    console.log('自动重试强制加载玩家列表');
                    forceUpdatePlayerList(server);
                }
            }, 5000);
        });
    }

    // 强制更新玩家列表函数（不受用户交互状态影响）
    function forceUpdatePlayerList(server) {
        console.log(`强制更新玩家列表 - 服务器: ${server || '所有服务器'}`);

        // 检查是否有有效的服务器名称
        if (!server) {
            console.log('无有效服务器，跳过玩家列表更新');
            playerList.innerHTML = '<div class="notice">请先选择一个子服</div>';
            return;
        }

        // 移除判断isViewingPlayerDetails的代码，确保始终更新玩家列表

        // 显示加载中
        playerList.innerHTML = '<div class="loading">正在加载玩家列表...</div>';

        // 构建API URL
        let apiUrl = '/api/players';
        if (server) {
            apiUrl += `?server=${encodeURIComponent(server)}`;
        }

        console.log(`强制请求玩家列表API: ${apiUrl}`);

        // 请求配置
        const fetchOptions = {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            },
            mode: 'same-origin',
            cache: 'no-cache',
            credentials: 'same-origin'
        };

        // 设置请求超时
        const timeout = setTimeout(() => {
            console.error('请求玩家列表超时');
            playerList.innerHTML = '<div class="error">加载玩家列表超时</div>';
        }, 10000);

        // 发起请求与超时竞赛
        Promise.race([
            fetch(apiUrl, fetchOptions),
            new Promise((_, reject) =>
                setTimeout(() => reject(new Error('请求超时')), 10000)
            )
        ])
        .then(response => {
            clearTimeout(timeout);
            console.log(`玩家列表API响应状态: ${response.status}`);

            if (!response.ok) {
                throw new Error('HTTP错误，状态: ' + response.status);
            }
            return response.text();
        })
        .then(text => {
            if (!text || text.trim() === '') {
                throw new Error('服务器返回了空响应');
            }

            try {
                return JSON.parse(text);
            } catch (e) {
                console.error("JSON解析错误:", e);
                throw new Error('无法解析JSON响应: ' + e.message);
            }
        })
        .then(data => {
            console.log('获取到玩家列表数据:', data);

            if (!data) {
                playerList.innerHTML = '<div class="error">玩家列表数据无效</div>';
                return;
            }

            if (!Array.isArray(data.players)) {
                playerList.innerHTML = '<div class="error">玩家列表格式错误</div>';
                console.error('玩家列表数据格式错误:', data);
                return;
            }

            if (data.players.length === 0) {
                playerList.innerHTML = '<div class="notice">该服务器没有在线玩家</div>';
                return;
            }

            // 清空并重新生成玩家列表
            playerList.innerHTML = '';

            // 生成每个玩家的列表项
            data.players.forEach(player => {
                // 检查玩家数据类型并适当处理
                let playerName;

                if (typeof player === 'string') {
                    // 如果player是字符串，直接使用
                    playerName = player;
                } else if (player && player.name) {
                    // 如果player是对象并且有name属性
                    playerName = player.name;
                } else {
                    // 无效的玩家数据
                    console.warn('检测到无效的玩家数据:', player);
                    return;
                }

                const playerItem = document.createElement('li');
                playerItem.innerHTML = `
                    <div class="player-name">${playerName}</div>
                `;

                // 添加点击事件 - 查看玩家详情
                playerItem.addEventListener('click', () => {
                    // 设置正在查看玩家详情标志
                    isViewingPlayerDetails = true;

                    // 移除其他玩家的活动状态
                    document.querySelectorAll('#playerList li').forEach(item => {
                        item.classList.remove('active');
                    });

                    // 标记当前玩家为活动状态
                    playerItem.classList.add('active');

                    // 加载玩家详情
                    loadPlayerInfo(playerName, currentServer);
                });

                playerList.appendChild(playerItem);
            });
        })
        .catch(error => {
            console.error('获取玩家列表时发生错误:', error);
            playerList.innerHTML = `<div class="error">加载玩家列表失败: ${error.message}</div>`;
        });
    }

    // 载入玩家详情
    function loadPlayerInfo(playerName, serverName = null) {
        console.log(`加载玩家详情: ${playerName}, 服务器: ${serverName || '默认服务器'}`);

        // 设置正在查看玩家详情状态
        isViewingPlayerDetails = true;

        // 显示玩家信息区域
        document.querySelector('.player-info').classList.add('active');

        // 清空当前物品详情
        clearAllTooltips();

        // 清除玩家信息区域的旧内容
        const playerInfoContent = document.querySelector('.player-info-content');
        if (playerInfoContent) {
            playerInfoContent.innerHTML = '<div class="loading">正在加载玩家信息...</div>';
        }

        // 设置玩家名称标题
        const playerNameHeader = document.querySelector('.player-info-header h3');
        if (playerNameHeader) {
            playerNameHeader.textContent = playerName;
        }



        // 构建API URL - 修复格式为/api/player/[玩家名]?server=[服务器名]
        let apiUrl = `/api/player/${encodeURIComponent(playerName)}`;
        if (serverName) {
            apiUrl += `?server=${encodeURIComponent(serverName)}`;
        }

        console.log(`请求玩家详情API: ${apiUrl}`);

        // 请求配置
        const fetchOptions = {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            },
            mode: 'same-origin',
            cache: 'no-cache',
            credentials: 'same-origin'
        };

        // 设置请求超时
        const timeout = setTimeout(() => {
            console.error('请求玩家详情超时');
            // 显示错误信息在状态栏
            const playerNameStatus = document.getElementById('playerNameStatus');
            if (playerNameStatus) {
                playerNameStatus.innerHTML = '<span class="error-text">加载超时</span>';
            }

        }, 10000);

        // 发起请求与超时竞赛
        Promise.race([
            fetch(apiUrl, fetchOptions),
            new Promise((_, reject) =>
                setTimeout(() => reject(new Error('请求超时')), 10000)
            )
        ])
        .then(response => {
            clearTimeout(timeout);
            console.log(`玩家详情API响应状态: ${response.status}`);

            if (!response.ok) {
                throw new Error('HTTP错误，状态: ' + response.status);
            }
            return response.text();
        })
        .then(text => {
            if (!text || text.trim() === '') {
                throw new Error('服务器返回了空响应');
            }

            try {
                return JSON.parse(text);
            } catch (e) {
                console.error("JSON解析错误:", e);



                throw new Error('无法解析JSON响应: ' + e.message);
            }
        })
        .then(data => {
            console.log('获取到玩家详情数据:', data);



            // 添加DragonCore数据调试
            console.log('==================== DragonCore数据调试开始 ====================');
            console.log('收到的完整JSON数据:', JSON.stringify(data));
            console.log('完整JSON数据中一级属性:', Object.keys(data || {}));

            // 定义可能的DragonCore键名，包括各种大小写和格式变体
            const possibleDragonCoreKeys = [
                'dragonCore', 'DragonCore', 'dragoncore', 'DRAGONCORE',
                'dragon_core', 'Dragon_Core', 'dragon_Core', 'Dragon_core',
                'dc', 'DC', 'Dc', 'dC', 'dragon', 'Dragon', 'DRAGON',
                'dragonCoreItems', 'DragonCoreItems', 'dragoncoreitems',
                'extra_items', 'extraItems', 'ExtraItems', 'extra', 'Extra'
            ];

            // 检查所有可能的键名
            let foundDragonCore = false;
            possibleDragonCoreKeys.forEach(key => {
                if (data && data[key]) {
                    foundDragonCore = true;
                    console.log(`发现DragonCore数据，键名: ${key}，包含 ${Object.keys(data[key]).length} 个物品`);
                    console.log('DragonCore物品数据:', data[key]);
                }
            });

            // 如果没有找到，进行深度搜索
            if (!foundDragonCore) {
                console.log('未在一级属性中找到DragonCore数据，开始递归搜索...');

                // 递归函数，查找包含"dragon"或"core"的键
                function deepSearchForDragonCore(obj, path = '') {
                    if (!obj || typeof obj !== 'object') return null;

                    // 查找当前层级
                    for (const key in obj) {
                        const newPath = path ? `${path}.${key}` : key;
                        const lowerKey = key.toLowerCase();

                        // 检查键名是否包含相关关键词
                        if (lowerKey.includes('dragon') || lowerKey.includes('core') ||
                            lowerKey.includes('slot') || lowerKey.includes('extra')) {
                            console.log(`找到可能的DragonCore相关键: ${newPath} = `, obj[key]);

                            // 如果该值是对象且不是数组，并且有类型为物品的结构，可能是DragonCore数据
                            if (typeof obj[key] === 'object' && !Array.isArray(obj[key])) {
                                for (const itemKey in obj[key]) {
                                    const item = obj[key][itemKey];
                                    if (item && item.type && typeof item.type === 'string') {
                                        console.log(`在 ${newPath} 中找到可能的物品: ${itemKey} = `, item);
                                        return obj[key]; // 返回找到的疑似DragonCore数据
                                    }
                                }
                            }
                        }

                        // 递归搜索
                        if (obj[key] && typeof obj[key] === 'object') {
                            const result = deepSearchForDragonCore(obj[key], newPath);
                            if (result) return result;
                        }
                    }

                    return null;
                }

                const possibleDragonCoreData = deepSearchForDragonCore(data);
                if (possibleDragonCoreData) {
                    console.log('递归搜索找到可能的DragonCore数据:', possibleDragonCoreData);
                    // 将找到的数据放入data.dragonCore以便显示
                    data.dragonCore = possibleDragonCoreData;
                    console.log('已将发现的DragonCore数据添加到data.dragonCore属性中');
                } else {
                    console.log('未找到任何可能的DragonCore数据');

                    // 尝试检查装备栏是否包含DragonCore物品
                    console.log('检查装备栏中是否有DragonCore物品...');
                    if (data.armor && Array.isArray(data.armor)) {
                        // 查找装备栏中包含"core"或"dragon"的物品
                        const dragonCoreItems = {};
                        data.armor.forEach((item, index) => {
                            if (item && item.name && item.type) {
                                const itemName = item.name.toLowerCase();
                                const lore = item.lore || [];
                                const hasRelatedText = itemName.includes('dragon') ||
                                                      itemName.includes('core') ||
                                                      lore.some(line =>
                                                          line.toLowerCase().includes('dragon') ||
                                                          line.toLowerCase().includes('core'));

                                if (hasRelatedText) {
                                    console.log(`装备栏中发现可能的DragonCore物品: ${item.name}`, item);
                                    // 复制物品到DragonCore数据
                                    dragonCoreItems[`装备物品${index+1}`] = { ...item };
                                }
                            }
                        });

                        if (Object.keys(dragonCoreItems).length > 0) {
                            console.log('从装备栏中找到可能的DragonCore物品:', dragonCoreItems);
                            data.dragonCore = dragonCoreItems;
                            console.log('已将装备栏中的可能DragonCore物品添加到data.dragonCore属性中');
                        }
                    }

                    // 如果在所有可能的地方都没找到，创建测试数据
                    if (!data.dragonCore || Object.keys(data.dragonCore).length === 0) {
                        console.log('未找到DragonCore数据，不添加测试数据');
                        // 注释以下代码，不再使用测试数据
                        /*
                        console.log('创建DragonCore测试数据来展示效果...');
                        // 使用测试数据
                        data.dragonCore = {
                            "额外槽位1": {
                                "type": "LEATHER_CHESTPLATE",
                                "amount": 1,
                                "name": "§6DragonCore额外物品",
                                "lore": [
                                    "§7这是从后端日志中提及的物品",
                                    "§c类型: LEATHER_CHESTPLATE",
                                    "§e注意: 这是测试数据，用于展示效果"
                                ]
                            },
                            "额外槽位2": {
                                "type": "DIAMOND_SWORD",
                                "amount": 1,
                                "name": "§b测试数据 - 钻石剑",
                                "lore": ["§7这是测试数据用于验证DragonCore物品栏显示"]
                            }
                        };
                        console.log('已创建测试数据:', data.dragonCore);
                        */
                    }
                }
            }

            // 检查原始JSON字符串
            console.log('尝试在原始JSON字符串中查找DragonCore相关文本...');
            const jsonString = JSON.stringify(data);
            const dragonCoreIndices = [];
            let searchIndex = 0;

            while (true) {
                const foundIndex = jsonString.toLowerCase().indexOf('dragon', searchIndex);
                if (foundIndex === -1) break;
                dragonCoreIndices.push(foundIndex);
                searchIndex = foundIndex + 6; // 'dragon'的长度

                // 提取上下文
                const start = Math.max(0, foundIndex - 20);
                const end = Math.min(jsonString.length, foundIndex + 100);
                console.log(`发现'dragon'在位置 ${foundIndex}, 上下文: '${jsonString.substring(start, end)}'`);
            }

            if (dragonCoreIndices.length > 0) {
                console.log(`在原始JSON中找到 ${dragonCoreIndices.length} 处可能的DragonCore引用`);
            } else {
                console.log('在原始JSON中未找到任何DragonCore引用');
            }

            console.log('==================== DragonCore数据调试结束 ====================');

            // 后端API返回格式检查
            if (data && !data.error) {
                updatePlayerStatus(data);

                // 更新装备栏
                if (data.armor) {
                    updateArmorSlots(data.armor);
                }

                // 更新物品栏
                if (data.inventory) {
                    const filteredInventory = data.inventory.slice(0, 36);
                    console.log('过滤后的物品栏数据:', filteredInventory);
                    updateInventorySlots(filteredInventory);
                }

                // 更新主副手
                if (data.mainHand || data.offHand) {
                    updateHandSlots(data.mainHand, data.offHand);
                }

                // 更新自定义占位符信息
                if (data.placeholders) {
                    console.log('更新自定义占位符:', data.placeholders);
                    updateCustomPlaceholders(data);
                }

                // 处理DragonCore物品数据
                if (data.dragonCore && Object.keys(data.dragonCore).length > 0) {
                    console.log('检测到DragonCore物品数据:', data.dragonCore);
                    updateDragonCoreItems(data.dragonCore);
                } else {
                    console.log('没有检测到DragonCore物品数据');
                    clearDragonCoreItems(); // 清空DragonCore物品槽
                }

                // 移除经济信息相关代码

                // 创建并更新调试面板
                createDebugPanel(data);
            } else {
                console.error('加载玩家详情失败:', data.error || '未知错误');
                // 显示错误信息在状态栏
                const playerNameStatus = document.getElementById('playerNameStatus');
                if (playerNameStatus) {
                    playerNameStatus.innerHTML = '<span class="error-text">加载失败</span>';
                }

                // 移除经济相关默认值设置代码
            }
        })
        .catch(error => {
            clearTimeout(timeout);
            console.error('获取玩家详情时发生错误:', error);

            // 显示错误信息在状态栏
            const playerNameStatus = document.getElementById('playerNameStatus');
            if (playerNameStatus) {
                playerNameStatus.innerHTML = '<span class="error-text">加载失败</span>';
            }

            // 移除经济相关默认值设置代码
        });
    }

    // 创建调试面板，显示后端返回的所有数据
    function createDebugPanel(data) {
        console.log('创建调试面板，显示后端数据');

        // 移除旧的调试面板（如果存在）
        let debugPanel = document.getElementById('debug-panel');
        if (debugPanel) {
            debugPanel.remove();
        }

        // 创建新的调试面板
        debugPanel = document.createElement('div');
        debugPanel.id = 'debug-panel';
        debugPanel.className = 'debug-panel';

        // 添加标题和折叠按钮
        const panelHeader = document.createElement('div');
        panelHeader.className = 'debug-panel-header';
        panelHeader.innerHTML = `
            <h3>调试信息面板 - 后端数据</h3>
            <button id="toggle-debug-panel" class="debug-toggle-btn">显示/隐藏</button>
        `;
        debugPanel.appendChild(panelHeader);

        // 创建内容容器
        const panelContent = document.createElement('div');
        panelContent.className = 'debug-panel-content';
        panelContent.style.display = 'none'; // 默认隐藏

        // 将数据格式化为JSON字符串
        const jsonString = JSON.stringify(data, null, 2);

        // 分析DragonCore数据问题
        let dragonCoreAnalysis = '<p>未在JSON数据中找到DragonCore物品数据</p>';

        if (data.dragonCore && Object.keys(data.dragonCore).length > 0) {
            dragonCoreAnalysis = `
                <div class="debug-success">
                    <p><strong>✓ 找到DragonCore数据</strong></p>
                    <p>包含 ${Object.keys(data.dragonCore).length} 个物品</p>
                    <pre>${escapeHtml(JSON.stringify(data.dragonCore, null, 2))}</pre>
                </div>
            `;
        } else {
            // 检查后端日志与前端数据的不一致
            dragonCoreAnalysis = `
                <div class="debug-error">
                    <p><strong>✗ 未找到DragonCore数据</strong></p>
                    <p>前端未收到DragonCore物品数据。可能原因：</p>
                    <ul>
                        <li>后端未收集DragonCore物品或该玩家没有DragonCore物品</li>
                        <li>后端序列化JSON时丢失了DragonCore数据</li>
                        <li>DragonCore数据使用了不同的字段名</li>
                        <li>玩家没有权限查看DragonCore物品</li>
                        <li>DragonCore插件未正确加载或API调用失败</li>
                    </ul>
                    <p><strong>相关后端日志信息：</strong></p>
                    <p>请查看服务器端PlayerController.java日志，关注以下内容：</p>
                    <ol>
                        <li>是否成功检测到DragonCore插件</li>
                        <li>getPlayerItemsMap()方法是否返回物品</li>
                        <li>物品序列化过程中是否有错误</li>
                        <li>JSON构建过程中是否添加了DragonCore数据</li>
                    </ol>
                    <p><strong>调试建议：</strong> 检查是否有在线玩家确实拥有DragonCore物品。若有，确认后端日志输出是否表明成功检测到物品并添加到玩家数据中。</p>
                </div>
            `;
        }

        // 创建各部分内容
        panelContent.innerHTML = `
            <div class="debug-section debug-section-important">
                <h4>DragonCore数据分析 🔍</h4>
                <div class="debug-info" id="debug-dragoncore-analysis">
                    ${dragonCoreAnalysis}
                </div>
            </div>

            <div class="debug-section">
                <h4>基本信息</h4>
                <div class="debug-info">
                    <p><strong>玩家名称:</strong> ${data.name || '未提供'}</p>
                    <p><strong>等级:</strong> ${data.level || '未提供'}</p>
                    <p><strong>生命值:</strong> ${data.health || '0'}/${data.maxHealth || '0'}</p>

                </div>
            </div>

            <div class="debug-section">
                <h4>JSON数据结构分析</h4>
                <div class="debug-info">
                    <p><strong>顶级属性:</strong> ${Object.keys(data).join(', ')}</p>
                    <p><strong>包含DragonCore相关属性:</strong> ${
                        Object.keys(data).some(key =>
                            key.toLowerCase().includes('dragon') ||
                            key.toLowerCase().includes('core')) ? '是' : '否'
                    }</p>
                    <p><strong>数据大小:</strong> ${jsonString.length} 字符</p>
                </div>
            </div>

            <div class="debug-section">
                <h4>装备信息</h4>
                <div class="debug-info" id="debug-armor">
                    ${formatArmorInfo(data.armor)}
                </div>
            </div>

            <div class="debug-section">
                <h4>物品栏信息</h4>
                <div class="debug-info" id="debug-inventory">
                    ${formatInventoryInfo(data.inventory)}
                </div>
            </div>

            <div class="debug-section">
                <h4>DragonCore物品</h4>
                <div class="debug-info" id="debug-dragoncore">
                    ${formatDragonCoreInfo(data.dragonCore)}
                </div>
            </div>

            <div class="debug-section">
                <h4>占位符数据</h4>
                <div class="debug-info" id="debug-placeholders">
                    ${formatPlaceholderInfo(data.placeholders)}
                </div>
            </div>

            <div class="debug-section">
                <h4>完整JSON数据</h4>
                <pre id="debug-raw-json" class="debug-json">${escapeHtml(jsonString)}</pre>
            </div>
        `;

        debugPanel.appendChild(panelContent);

        // 添加面板样式
        const style = document.createElement('style');
        style.textContent = `
            .debug-panel {
                margin-top: 20px;
                margin-bottom: 20px;
                background-color: #f5f5f5;
                border: 1px solid #ddd;
                border-radius: 5px;
                font-family: Arial, sans-serif;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                max-width: 100%;
                overflow: hidden;
            }

            .debug-panel-header {
                background-color: #333;
                color: white;
                padding: 10px 15px;
                display: flex;
                justify-content: space-between;
                align-items: center;
            }

            .debug-panel-header h3 {
                margin: 0;
                font-size: 16px;
            }

            .debug-toggle-btn {
                background-color: #555;
                color: white;
                border: none;
                padding: 5px 10px;
                border-radius: 3px;
                cursor: pointer;
            }

            .debug-toggle-btn:hover {
                background-color: #777;
            }

            .debug-panel-content {
                padding: 0;
                overflow: hidden;
            }

            .debug-section {
                margin-bottom: 10px;
                border-bottom: 1px solid #ddd;
                padding: 10px 15px;
            }

            .debug-section-important {
                background-color: #fffcf0;
                border-left: 4px solid #f0ad4e;
            }

            .debug-section h4 {
                margin: 0 0 10px 0;
                color: #333;
                font-size: 14px;
            }

            .debug-info {
                font-size: 13px;
                line-height: 1.4;
                color: #444;
                max-height: 200px;
                overflow-y: auto;
            }

            .debug-json {
                background-color: #f9f9f9;
                border: 1px solid #ddd;
                padding: 10px;
                font-family: monospace;
                font-size: 12px;
                white-space: pre-wrap;
                max-height: 300px;
                overflow-y: auto;
                color: #333;
            }

            .debug-item {
                margin-bottom: 5px;
                padding: 5px;
                background-color: rgba(0,0,0,0.03);
                border-radius: 3px;
            }

            .debug-success {
                background-color: #dff0d8;
                border-left: 4px solid #5cb85c;
                padding: 10px;
                margin-bottom: 10px;
                border-radius: 3px;
            }

            .debug-error {
                background-color: #f2dede;
                border-left: 4px solid #d9534f;
                padding: 10px;
                margin-bottom: 10px;
                border-radius: 3px;
            }

            .debug-error ul, .debug-error ol {
                margin-top: 5px;
                margin-bottom: 5px;
                padding-left: 20px;
            }

            .debug-error li {
                margin-bottom: 3px;
            }
        `;
        document.head.appendChild(style);

        // 将面板添加到物品栏下方
        const inventorySection = document.querySelector('.inventory-section');
        if (inventorySection) {
            inventorySection.parentNode.insertBefore(debugPanel, inventorySection.nextSibling);
        } else {
            // 如果找不到物品栏，添加到页面底部
            document.body.appendChild(debugPanel);
        }

        // 添加点击事件处理程序，用于折叠/展开面板
        document.getElementById('toggle-debug-panel').addEventListener('click', function() {
            const content = document.querySelector('.debug-panel-content');
            content.style.display = content.style.display === 'none' ? 'block' : 'none';
        });
    }

    // 格式化装备信息
    function formatArmorInfo(armor) {
        if (!armor || !Array.isArray(armor) || armor.length === 0) {
            return '<p>没有装备数据</p>';
        }

        const armorTypes = ['靴子', '护腿', '胸甲', '头盔'];
        let html = '<div>';

        armor.forEach((item, index) => {
            if (item && item.type && item.type !== 'AIR') {
                const armorType = armorTypes[index] || `装备${index}`;
                html += `<div class="debug-item">
                    <strong>${armorType}:</strong> ${item.type}
                    ${item.name ? `(${escapeHtml(item.name)})` : ''}
                </div>`;
            }
        });

        html += '</div>';
        return html !== '<div></div>' ? html : '<p>没有装备数据</p>';
    }

    // 格式化物品栏信息
    function formatInventoryInfo(inventory) {
        if (!inventory || !Array.isArray(inventory) || inventory.length === 0) {
            return '<p>没有物品栏数据</p>';
        }

        let html = '<div>';
        let itemCount = 0;

        inventory.forEach((item, index) => {
            if (item && item.type && item.type !== 'AIR') {
                itemCount++;
                html += `<div class="debug-item">
                    <strong>槽位${index}:</strong> ${item.type} x${item.amount || 1}
                    ${item.name ? `(${escapeHtml(item.name)})` : ''}
                </div>`;
            }
        });

        html += '</div>';
        return itemCount > 0 ? html : '<p>物品栏中没有物品</p>';
    }

    // 格式化DragonCore物品信息
    function formatDragonCoreInfo(dragonCore) {
        if (!dragonCore || typeof dragonCore !== 'object' || Object.keys(dragonCore).length === 0) {
            return '<p>没有DragonCore物品数据</p>';
        }

        let html = '<div>';

        for (const key in dragonCore) {
            const item = dragonCore[key];
            if (item && item.type && item.type !== 'AIR') {
                html += `<div class="debug-item">
                    <strong>${escapeHtml(key)}:</strong> ${item.type} x${item.amount || 1}
                    ${item.name ? `(${escapeHtml(item.name)})` : ''}
                </div>`;
            }
        }

        html += '</div>';
        return html !== '<div></div>' ? html : '<p>没有DragonCore物品数据</p>';
    }

    // 格式化占位符信息
    function formatPlaceholderInfo(placeholders) {
        if (!placeholders) {
            return '<p>没有占位符数据</p>';
        }

        let placeholdersArray = null;

        // 处理两种可能的数据结构
        if (Array.isArray(placeholders)) {
            placeholdersArray = placeholders;
        } else if (placeholders.placeholders && Array.isArray(placeholders.placeholders)) {
            placeholdersArray = placeholders.placeholders;
        }

        if (!placeholdersArray || !Array.isArray(placeholdersArray) || placeholdersArray.length === 0) {
            return '<p>没有占位符数据</p>';
        }

        let html = '<div>';

        placeholdersArray.forEach(ph => {
            html += `<div class="debug-item">
                <strong>${escapeHtml(ph.id || ph.name || '未命名')}:</strong> ${escapeHtml(ph.value || '无值')}
            </div>`;
        });

        html += '</div>';
        return html;
    }

    // HTML转义函数
    function escapeHtml(text) {
        if (typeof text !== 'string') {
            return '';
        }
        return text
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }

    // 更新DragonCore物品
    function updateDragonCoreItems(dragonCoreItems) {
        console.log('==================== DragonCore物品更新开始 ====================');
        console.log('更新DragonCore物品:', dragonCoreItems);
        console.log('DragonCore物品数据类型:', typeof dragonCoreItems);

        // 检查数据结构
        if (typeof dragonCoreItems === 'string') {
            try {
                console.log('DragonCore数据是字符串，尝试解析为JSON');
                dragonCoreItems = JSON.parse(dragonCoreItems);
            } catch (e) {
                console.error('DragonCore字符串解析失败:', e);
            }
        }

        // 如果数据为null或不是对象，则没有物品可显示
        if (!dragonCoreItems || typeof dragonCoreItems !== 'object' || Array.isArray(dragonCoreItems)) {
            console.warn('DragonCore数据无效或为空，不显示任何物品');
            // 清空所有DragonCore物品槽
            clearDragonCoreItems();
            return;
        }

        // 获取所有DragonCore物品槽
        const dragonCoreSlots = document.querySelectorAll('.dragoncore-slot');
        if (!dragonCoreSlots || dragonCoreSlots.length === 0) {
            console.error('未找到DragonCore物品槽，物品槽DOM元素不存在');

            // 检查页面上是否存在物品槽的父容器
            const inventorySection = document.querySelector('.inventory-section');
            if (inventorySection) {
                console.log('找到物品栏区域:', inventorySection);
            } else {
                console.error('未找到物品栏区域容器');
            }

            return;
        }

        console.log(`找到 ${dragonCoreSlots.length} 个DragonCore物品槽`);

        // 将DragonCore物品数据转换为数组以便按顺序放置
        const itemsArray = [];
        for (const key in dragonCoreItems) {
            if (dragonCoreItems.hasOwnProperty(key)) {
                itemsArray.push({
                    key: key,
                    item: dragonCoreItems[key]
                });
            }
        }

        console.log(`共有 ${itemsArray.length} 个DragonCore物品准备显示`, itemsArray);

        // 清空所有DragonCore物品槽
        dragonCoreSlots.forEach(slot => {
            slot.innerHTML = '';
            slot.classList.remove('has-item');
            if (slot.dataset.itemKey) {
                delete slot.dataset.itemKey;
            }
        });

        // 按顺序将物品填充到DragonCore物品槽
        itemsArray.forEach((itemData, index) => {
            if (index < dragonCoreSlots.length) {
                const slot = dragonCoreSlots[index];
                const item = itemData.item;

                console.log(`处理物品 #${index+1}: ${itemData.key}`, item);

                if (item && item.type && item.type !== 'AIR') {
                    console.log(`显示DragonCore物品 #${index+1}: ${itemData.key} - ${item.type}`);

                    try {
                        // 创建物品图标
                        const iconDiv = document.createElement('div');
                        iconDiv.className = 'item-icon';
                        iconDiv.style.backgroundImage = `url(${getItemIconUrl(item.type, item.durability)})`;
                        slot.appendChild(iconDiv);

                        // 如果物品数量大于1，显示数量
                        if (item.amount && item.amount > 1) {
                            const countDiv = document.createElement('div');
                            countDiv.className = 'item-count';
                            countDiv.textContent = item.amount;
                            iconDiv.appendChild(countDiv);
                        }

                        // 创建工具提示
                        createItemTooltip(slot, item, `dragoncore-${itemData.key}`);

                        // 标记槽位有物品
                        slot.classList.add('has-item');
                        slot.dataset.itemKey = itemData.key;

                        console.log(`DragonCore物品 #${index+1} 添加成功`);
                    } catch (error) {
                        console.error(`创建DragonCore物品时出错:`, error);
                    }
                } else {
                    console.log(`DragonCore物品 #${index+1} 为空或AIR类型，不显示`);
                }
            } else {
                console.log(`物品 ${itemData.key} 索引超出范围，无法显示`);
            }
        });

        console.log('==================== DragonCore物品更新结束 ====================');
    }



    // 更新自定义占位符信息的函数
    function updateCustomPlaceholders(data) {
        // 检查是否有占位符数据
        if (!data) {
            console.log('没有占位符数据可显示');
            return;
        }

        // 处理两种可能的数据结构和提取名称映射
        let placeholdersArray = null;
        let nameMapping = null;

        // 检查并获取名称映射
        if (data.nameMapping) {
            nameMapping = data.nameMapping;
            console.log('找到占位符名称映射:', nameMapping);
        }

        if (data.placeholders) {
            // 如果是嵌套结构: data.placeholders.placeholders[...]
            if (data.placeholders.placeholders && Array.isArray(data.placeholders.placeholders)) {
                console.log('检测到嵌套的占位符数据结构');
                placeholdersArray = data.placeholders.placeholders;

                // 检查嵌套结构中的名称映射
                if (!nameMapping && data.placeholders.nameMapping) {
                    nameMapping = data.placeholders.nameMapping;
                    console.log('从嵌套结构中找到名称映射:', nameMapping);
                }
            }
            // 如果是直接数组: data.placeholders[...]
            else if (Array.isArray(data.placeholders)) {
                console.log('检测到直接的占位符数据数组');
                placeholdersArray = data.placeholders;
            }
        }

        // 没有找到有效的占位符数据
        if (!placeholdersArray || !Array.isArray(placeholdersArray)) {
            console.log('没有找到有效的占位符数据数组');
            return;
        }

        console.log(`处理 ${placeholdersArray.length} 个自定义占位符`);

        // 调试日志：打印所有占位符数据帮助排查问题
        placeholdersArray.forEach(ph => {
            console.log(`占位符ID: ${ph.id}, 名称: ${ph.name}, 值: ${ph.value}`);
        });

        // 移除经济容器相关代码

        // 如果PlaceholderManager可用，使用它来更新占位符
        if (window.PlaceholderManager) {
            // 检查nameMapping是否为空对象
            if (nameMapping && Object.keys(nameMapping).length === 0) {
                console.log('收到的nameMapping为空，将尝试使用PlaceholderManager中的静态映射');
            }

            // 传递占位符数组和名称映射
            window.PlaceholderManager.updatePlaceholders(placeholdersArray, nameMapping);
        } else {
            console.error('PlaceholderManager未找到，无法显示自定义占位符');
        }
    }

    // 添加清空DragonCore物品的函数
    function clearDragonCoreItems() {
        console.log('清空DragonCore物品槽');

        // 获取所有DragonCore物品槽
        const dragonCoreSlots = document.querySelectorAll('.dragoncore-slot');
        if (!dragonCoreSlots || dragonCoreSlots.length === 0) {
            console.log('未找到DragonCore物品槽');
            return;
        }

        // 清空所有DragonCore物品槽
        dragonCoreSlots.forEach(slot => {
            slot.innerHTML = '';
            slot.classList.remove('has-item');
            if (slot.dataset.itemKey) {
                delete slot.dataset.itemKey;
            }
        });
    }

    // 创建物品工具提示函数
    function createItemTooltip(slot, item, tooltipId) {
        // 创建工具提示内容
        const tooltipContent = document.createElement('div');
        tooltipContent.className = 'tooltip-content';
        tooltipContent.id = tooltipId || `tooltip-${Math.random().toString(36).substring(2, 11)}`;

        // 添加标题
        const titleDiv = document.createElement('div');
        titleDiv.className = 'tooltip-title';
        const itemName = item.name || item.displayName || translateItemName(item.type, item.durability);
        titleDiv.innerHTML = convertMinecraftColors(itemName);
        tooltipContent.appendChild(titleDiv);

        // 添加lore内容
        if (item.lore && item.lore.length > 0) {
            const loreDiv = document.createElement('div');
            loreDiv.className = 'tooltip-lore';

            item.lore.forEach(line => {
                const lineDiv = document.createElement('div');
                lineDiv.innerHTML = convertMinecraftColors(line);
                lineDiv.style.color = '#AAAAAA';
                loreDiv.appendChild(lineDiv);
            });

            tooltipContent.appendChild(loreDiv);
        }

        slot.appendChild(tooltipContent);

        // 修改鼠标移入事件处理，使用fixed定位计算tooltip位置
        slot.addEventListener('mouseenter', () => {
            // 计算物品槽的位置
            const slotRect = slot.getBoundingClientRect();
            const tooltip = slot.querySelector('.tooltip-content');

            // 默认显示在物品槽上方
            let positionTop = slotRect.top - 10; // 距离物品槽上边缘10px
            let positionLeft = slotRect.left + slotRect.width / 2;

            // 设置tooltip位置为fixed，基于视口计算
            tooltip.style.bottom = '';
            tooltip.style.top = '';
            tooltip.classList.remove('tooltip-bottom');
            tooltip.style.transform = 'translateX(-50%)';

            // 显示tooltip后再检查是否需要调整位置
            tooltip.style.display = 'block';
            const tooltipRect = tooltip.getBoundingClientRect();

            // 检查顶部是否有足够空间
            if (positionTop - tooltipRect.height < 10) {
                // 如果顶部空间不足，显示在底部
                positionTop = slotRect.bottom + 10; // 距离物品槽下边缘10px
                tooltip.classList.add('tooltip-bottom');
            } else {
                // 正常显示在上方
                positionTop = positionTop - tooltipRect.height;
            }

            // 检查水平方向是否溢出
            if (positionLeft - tooltipRect.width / 2 < 10) {
                // 左侧空间不足
                positionLeft = 10 + tooltipRect.width / 2;
            } else if (positionLeft + tooltipRect.width / 2 > window.innerWidth - 10) {
                // 右侧空间不足
                positionLeft = window.innerWidth - 10 - tooltipRect.width / 2;
            }

            // 应用最终位置
            tooltip.style.top = `${positionTop}px`;
            tooltip.style.left = `${positionLeft}px`;
        });

        // 鼠标离开时隐藏tooltip
        slot.addEventListener('mouseleave', () => {
            const tooltip = slot.querySelector('.tooltip-content');
            if (tooltip) {
                tooltip.style.display = 'none';
            }
        });

        // 添加点击事件监听器，显示物品详情弹窗
        slot.addEventListener('click', () => {
            showItemDetailModal(item, getItemIconUrl(item.type, item.durability));
        });
    }

    // 清除所有物品提示的函数
    function clearAllTooltips() {
        const tooltips = document.querySelectorAll('.item-tooltip');
        tooltips.forEach(tooltip => tooltip.remove());
    }

    // 更新玩家状态栏
    function updatePlayerStatus(playerData) {
        // 更新玩家名称
        const playerNameElement = document.getElementById('playerNameStatus');
        if (playerNameElement) {
            playerNameElement.textContent = playerData.name || '--';
        }

        // 更新玩家等级
        const playerLevelElement = document.getElementById('playerLevelStatus');
        if (playerLevelElement && playerData.level !== undefined) {
            playerLevelElement.textContent = playerData.level || '0';
        }

        // 更新玩家血量和血量条
        const healthBar = document.getElementById('healthBar');
        const healthValue = document.getElementById('healthValue');
        if (healthBar && healthValue && playerData.health !== undefined && playerData.maxHealth !== undefined) {
            const healthPercent = (playerData.health / playerData.maxHealth) * 100;
            healthBar.style.width = `${healthPercent}%`;

            // 根据血量百分比改变颜色
            if (healthPercent <= 20) {
                healthBar.style.backgroundColor = '#f44336'; // 红色
            } else if (healthPercent <= 50) {
                healthBar.style.backgroundColor = '#ff9800'; // 橙色
                } else {
                healthBar.style.backgroundColor = '#4caf50'; // 绿色
            }

            healthValue.textContent = `${Math.round(playerData.health)}/${Math.round(playerData.maxHealth)}`;
        }

        // 更新玩家坐标
        if (playerData.location) {
            const x = playerData.location.x ? playerData.location.x.toFixed(1) : '0';
            const y = playerData.location.y ? playerData.location.y.toFixed(1) : '0';
            const z = playerData.location.z ? playerData.location.z.toFixed(1) : '0';

            const xElement = document.getElementById('playerCoordsX');
            const yElement = document.getElementById('playerCoordsY');
            const zElement = document.getElementById('playerCoordsZ');

            if (xElement) xElement.textContent = x;
            if (yElement) yElement.textContent = y;
            if (zElement) zElement.textContent = z;
        }
    }

    // 新增：检测装备类型的辅助函数，根据物品名称识别应该显示在哪个槽位
    function detectArmorType(item) {
        if (!item || !item.type) return null;

        const type = item.type.toLowerCase();

        // 头盔类型检测
        if (type.includes('helmet') ||
            type.includes('cap') ||
            type.includes('skull') ||
            type.includes('head')) {
            return 'helmet';
        }

        // 胸甲类型检测
        if (type.includes('chestplate') ||
            type.includes('tunic') ||
            type.includes('vest') ||
            type.includes('chest')) {
            return 'chestplate';
        }

        // 护腿类型检测
        if (type.includes('leggings') ||
            type.includes('pants') ||
            type.includes('leg')) {
            return 'leggings';
        }

        // 靴子类型检测
        if (type.includes('boots') ||
            type.includes('shoes') ||
            type.includes('foot')) {
            return 'boots';
        }

        return null;
    }

    // Update armor slots with player data
    function updateArmorSlots(armor) {
        console.log('接收到的装备数据:', JSON.stringify(armor));

        const armorSlots = document.querySelectorAll('#armorSlots .item-slot');

        // 清空所有盔甲槽
        armorSlots.forEach(slot => {
            slot.innerHTML = '';
            slot.classList.remove('has-item');
        });

        // 直接通过对应的HTML类名找到每个装备槽位，而不是依赖顺序
        const helmetSlot = document.querySelector('#armorSlots .item-slot.helmet');
        const chestplateSlot = document.querySelector('#armorSlots .item-slot.chestplate');
        const leggingsSlot = document.querySelector('#armorSlots .item-slot.leggings');
        const bootsSlot = document.querySelector('#armorSlots .item-slot.boots');

        console.log('找到装备槽位:', {
            helmet: !!helmetSlot,
            chestplate: !!chestplateSlot,
            leggings: !!leggingsSlot,
            boots: !!bootsSlot
        });

        // 正常Minecraft盔甲数组顺序：[靴子(0), 护腿(1), 胸甲(2), 头盔(3)]
        if (armor && Array.isArray(armor) && armor.length > 0) {
            console.log('装备数组长度:', armor.length);

            // 首先尝试按照固定位置放置装备
            for (let i = 0; i < armor.length && i < 4; i++) {
                const item = armor[i];
                if (item === null) {
                    console.log(`装备位置 ${i} 为空`);
                    continue;
                }

                console.log(`处理装备位置 ${i}:`, item.type || '未知类型');

                // 首先，尝试基于装备名称识别装备类型
                const detectedType = detectArmorType(item);
                if (detectedType) {
                    console.log(`基于名称检测到装备类型: ${detectedType}`);

                    // 根据检测到的类型放置装备
                    switch(detectedType) {
                        case 'helmet':
                            if (helmetSlot) updateSlot(helmetSlot, item);
                            break;
                        case 'chestplate':
                            if (chestplateSlot) updateSlot(chestplateSlot, item);
                            break;
                        case 'leggings':
                            if (leggingsSlot) updateSlot(leggingsSlot, item);
                            break;
                        case 'boots':
                            if (bootsSlot) updateSlot(bootsSlot, item);
                            break;
                    }
                } else {
                    // 如果无法基于名称检测类型，则按照位置索引放置
                    console.log(`无法检测装备类型，按索引放置: ${i}`);

                    switch(i) {
                        case 0: // 靴子
                            if (bootsSlot) updateSlot(bootsSlot, item);
                            break;
                        case 1: // 护腿
                            if (leggingsSlot) updateSlot(leggingsSlot, item);
                            break;
                        case 2: // 胸甲
                            if (chestplateSlot) updateSlot(chestplateSlot, item);
                            break;
                        case 3: // 头盔
                            if (helmetSlot) updateSlot(helmetSlot, item);
                            break;
                    }
                }
            }
        } else {
            console.warn('无效的装备数据:', armor);
        }
    }

    // Update inventory slots with player data
    function updateInventorySlots(inventory) {
        console.log('==================== 物品栏数据开始 ====================');
        if (!Array.isArray(inventory)) {
            console.warn('物品栏数据不是数组');
            return;
        }

        // 确保只处理0-35的物品
        const validInventory = inventory.slice(0, 36);
        console.log('收到物品栏数据，处理前:', inventory.length, '个项目，处理后:', validInventory.length, '个项目');

        // 打印原始数据，用于调试
        console.log('物品槽数据:');
        validInventory.forEach((item, i) => {
            if (item && item.type && item.type !== 'AIR') {
                console.log(`槽位 ${i}: ${item.type} x${item.amount || 1}`, item);
            }
        });

        console.log('==================== 物品栏数据结束 ====================');

        // 创建物品栏的HTML结构
        const inventoryContainer = document.getElementById('inventorySlots');
        inventoryContainer.innerHTML = '';

        // 创建物品栏槽位 - 保持Minecraft的布局，9列x4行=36个槽位
        // 在Minecraft中，槽位索引顺序：
        // - 9-35: 主背包（从左到右，从上到下排列，共27个）
        // - 0-8: 快捷栏（从左到右排列，共9个）

        // 先创建主背包（9列x3行=27个）
        for (let row = 0; row < 3; row++) {
            for (let col = 0; col < 9; col++) {
                const index = 9 + (row * 9) + col; // 计算实际索引（9-35）
                const slot = document.createElement('div');
                slot.className = 'item-slot';
                slot.dataset.index = index;
                inventoryContainer.appendChild(slot);
            }
        }

        // 再创建快捷栏（9个）
        for (let col = 0; col < 9; col++) {
            const index = col; // 实际索引（0-8）
            const slot = document.createElement('div');
            slot.className = 'item-slot';
            slot.dataset.index = index;
            inventoryContainer.appendChild(slot);
        }

        // 填充物品到对应槽位
        validInventory.forEach((item, i) => {
            if (item && item.type && item.type !== 'AIR') {
                const matchingSlot = document.querySelector(`#inventorySlots .item-slot[data-index="${i}"]`);
                if (matchingSlot) {
                    updateSlot(matchingSlot, item);
                } else {
                    console.warn(`未找到索引为 ${i} 的槽位元素`);
                }
            }
        });

        // 添加分隔线
        const separator = document.createElement('div');
        separator.className = 'dragoncore-separator';
        separator.style.gridColumn = '1 / span 9'; // 横跨9列
        separator.style.gridRow = '5'; // 明确指定在第5行
        inventoryContainer.appendChild(separator);

        // 添加额外的2行DragonCore物品栏（2行x9列=18个物品槽）
        for (let row = 0; row < 2; row++) {
            for (let col = 0; col < 9; col++) {
                const dcIndex = row * 9 + col; // DragonCore物品索引
                const slot = document.createElement('div');
                slot.className = 'item-slot dragoncore-slot';
                slot.dataset.dcIndex = dcIndex;

                // 使用style属性明确指定网格位置
                slot.style.gridRow = row + 6; // 从第6行开始（4行原版+1行分隔线）
                slot.style.gridColumn = col + 1; // 列从1开始

                inventoryContainer.appendChild(slot);
            }
        }
    }

    // Update hand slots with player data
    function updateHandSlots(mainHand, offHand) {
        const mainHandSlot = document.querySelector('.item-slot.main-hand');
        const offHandSlot = document.querySelector('.item-slot.off-hand');

        if (mainHandSlot) {
            if (mainHand && mainHand.type && mainHand.type !== 'AIR') {
                updateSlot(mainHandSlot, mainHand);
            } else {
                mainHandSlot.innerHTML = '';
                mainHandSlot.classList.remove('has-item');
            }
        }

        if (offHandSlot) {
            if (offHand && offHand.type && offHand.type !== 'AIR') {
                updateSlot(offHandSlot, offHand);
            } else {
                offHandSlot.innerHTML = '';
                offHandSlot.classList.remove('has-item');
            }
        }
    }

    // Update individual slot with item data
    function updateSlot(slot, item) {
        if (!item) {
            slot.innerHTML = '';
            slot.classList.remove('has-item');
            return;
        }

        const itemType = item.type;
        const amount = item.amount || 1;
        const name = item.name || translateItemName(itemType, item.durability);
        const lore = item.lore || [];

        slot.classList.add('has-item');
        slot.innerHTML = '';

        const iconDiv = document.createElement('div');
        iconDiv.className = 'item-icon';
        iconDiv.style.backgroundImage = `url(${getItemIconUrl(itemType, item.durability)})`;
        slot.appendChild(iconDiv);

        if (amount > 1) {
            const countDiv = document.createElement('div');
            countDiv.className = 'item-count';
            countDiv.textContent = amount;
            iconDiv.appendChild(countDiv);
        }

        // 创建工具提示内容
        const tooltipContent = document.createElement('div');
        tooltipContent.className = 'tooltip-content';

        // 添加标题
        const titleDiv = document.createElement('div');
        titleDiv.className = 'tooltip-title';
        titleDiv.innerHTML = convertMinecraftColors(name);
        tooltipContent.appendChild(titleDiv);

        // 添加lore内容
        if (lore.length > 0) {
            const loreDiv = document.createElement('div');
            loreDiv.className = 'tooltip-lore';

            lore.forEach(line => {
                const lineDiv = document.createElement('div');
                lineDiv.innerHTML = convertMinecraftColors(line);
                lineDiv.style.color = '#AAAAAA';
                loreDiv.appendChild(lineDiv);
            });

            tooltipContent.appendChild(loreDiv);
        }

        slot.appendChild(tooltipContent);

        // 修改鼠标移入事件处理，使用fixed定位计算tooltip位置
        slot.addEventListener('mouseenter', () => {
            // 计算物品槽的位置
            const slotRect = slot.getBoundingClientRect();
            const tooltipContent = slot.querySelector('.tooltip-content');

            // 默认显示在物品槽上方
            let positionTop = slotRect.top - 10; // 距离物品槽上边缘10px
            let positionLeft = slotRect.left + slotRect.width / 2;

            // 设置tooltip位置为fixed，基于视口计算
            tooltipContent.style.bottom = '';
            tooltipContent.style.top = '';
            tooltipContent.classList.remove('tooltip-bottom');
            tooltipContent.style.transform = 'translateX(-50%)';

            // 显示tooltip后再检查是否需要调整位置
            // 强制浏览器重新计算尺寸以获取正确的tooltip大小
            tooltipContent.style.display = 'block';
            const tooltipRect = tooltipContent.getBoundingClientRect();

            // 检查顶部是否有足够空间
            if (positionTop - tooltipRect.height < 10) {
                // 如果顶部空间不足，显示在底部
                positionTop = slotRect.bottom + 10; // 距离物品槽下边缘10px
                tooltipContent.classList.add('tooltip-bottom');
            } else {
                // 正常显示在上方
                positionTop = positionTop - tooltipRect.height;
            }

            // 检查水平方向是否溢出
            if (positionLeft - tooltipRect.width / 2 < 10) {
                // 左侧空间不足
                positionLeft = 10 + tooltipRect.width / 2;
            } else if (positionLeft + tooltipRect.width / 2 > window.innerWidth - 10) {
                // 右侧空间不足
                positionLeft = window.innerWidth - 10 - tooltipRect.width / 2;
            }

            // 应用最终位置
            tooltipContent.style.top = `${positionTop}px`;
            tooltipContent.style.left = `${positionLeft}px`;
        });

        // 鼠标离开时隐藏tooltip
        slot.addEventListener('mouseleave', () => {
            const tooltipContent = slot.querySelector('.tooltip-content');
            tooltipContent.style.display = 'none';
        });

        // 添加点击事件监听器，显示物品详情弹窗
        slot.addEventListener('click', () => {
            showItemDetailModal(item, getItemIconUrl(item.type, item.durability));
        });
    }

    // 物品详情模态弹窗功能
    const modal = document.getElementById('itemDetailModal');
    const closeButton = document.querySelector('.close-button');

    // 关闭模态弹窗
    function closeModal() {
        // 关闭物品详情模态框
        const modal = document.getElementById('itemDetailModal');
        if (modal) {
            // 先移除active类触发CSS过渡效果
            modal.classList.remove('active');

            // 等待过渡动画完成后再隐藏元素
            setTimeout(() => {
                modal.style.display = 'none';
            }, 300); // 300毫秒是过渡时间，可根据CSS中定义的过渡时间调整
        }

        // 隐藏玩家信息区域
        const playerInfo = document.querySelector('.player-info');
        if (playerInfo) {
            playerInfo.classList.remove('active');

            // 重置交互状态
            isViewingPlayerDetails = false;

            // 短暂延迟后自动更新玩家列表
            setTimeout(() => {
                if (!isViewingPlayerDetails && Date.now() - lastPlayerListUpdate > 5000) {
                    updatePlayerList(currentServer);
                }
            }, 500);
        }
    }

    // 点击关闭按钮
    if (closeButton) {
        closeButton.addEventListener('click', closeModal);
    }

    // 点击模态弹窗外部区域也关闭
    window.addEventListener('click', (event) => {
        const modal = document.getElementById('itemDetailModal');
        if (event.target === modal) {
            closeModal();
        }
    });

    // ESC键关闭弹窗
    window.addEventListener('keydown', (event) => {
        const modal = document.getElementById('itemDetailModal');
        if (event.key === 'Escape' && modal && (modal.style.display === 'block' || modal.classList.contains('active'))) {
            closeModal();
        }
    });

    // 显示物品详情模态弹窗
    function showItemDetailModal(item, iconUrl) {
        const itemName = item.name || translateItemName(item.type, item.durability);
        const lore = item.lore || [];

        // 设置物品名称
        document.getElementById('itemDetailName').innerHTML = convertMinecraftColors(itemName);
        document.getElementById('itemDetailTitle').textContent = translateItemName(item.type, item.durability);

        // 设置物品图片
        document.getElementById('itemDetailImage').src = iconUrl;

        // 清空并设置LORE内容
        const loreContainer = document.getElementById('itemDetailLore');
        loreContainer.innerHTML = '';

        if (lore.length > 0) {
            lore.forEach(line => {
                const lineDiv = document.createElement('div');
                lineDiv.innerHTML = convertMinecraftColors(line);
                loreContainer.appendChild(lineDiv);
            });
        } else {
            // 如果没有LORE，显示基本信息
            const basicInfo = document.createElement('div');
            basicInfo.innerHTML = `
                <div>类型: ${item.type}</div>
                ${item.durability > 0 ? `<div>耐久度: ${item.durability}</div>` : ''}
                ${item.amount > 1 ? `<div>数量: ${item.amount}</div>` : ''}
            `;
            loreContainer.appendChild(basicInfo);
        }

        // 显示模态弹窗
        modal.style.display = 'block';
        modal.classList.add('active'); // 添加active类以应用CSS动画效果
    }

    // 确保页面加载时添加关闭按钮的点击事件
    document.addEventListener('DOMContentLoaded', () => {
        // 为关闭按钮添加事件
        const closeButtons = document.querySelectorAll('.close-button');
        closeButtons.forEach(button => {
            button.onclick = closeModal;
        });

        // 为玩家信息区域的关闭按钮添加单独的事件监听器
        const playerInfoCloseButton = document.querySelector('.player-info .close-button');
        if (playerInfoCloseButton) {
            playerInfoCloseButton.onclick = function() {
                console.log('关闭玩家详情');
                closeModal();
            };
        } else {
            console.log('警告：未找到玩家详情关闭按钮');
            // 不再动态创建关闭按钮，避免出现多余元素
        }
    });

    // 渲染玩家列表的通用函数
    function renderPlayerList(data) {
        console.log('渲染玩家列表数据:', data);

        if (!data) {
            playerList.innerHTML = '<div class="error">玩家列表数据无效</div>';
            return;
        }

        if (!Array.isArray(data.players)) {
            playerList.innerHTML = '<div class="error">玩家列表格式错误</div>';
            console.error('玩家列表数据格式错误:', data);
            return;
        }

        if (data.players.length === 0) {
            playerList.innerHTML = '<div class="notice">该服务器没有在线玩家</div>';
            return;
        }

        // 清空并重新生成玩家列表
        playerList.innerHTML = '';

        // 生成每个玩家的列表项
        data.players.forEach(player => {
            // 检查玩家数据类型并适当处理
            let playerName;

            if (typeof player === 'string') {
                // 如果player是字符串，直接使用
                playerName = player;
            } else if (player && player.name) {
                // 如果player是对象并且有name属性
                playerName = player.name;
            } else {
                // 无效的玩家数据
                console.warn('检测到无效的玩家数据:', player);
                return;
            }

            const playerItem = document.createElement('li');
            playerItem.innerHTML = `
                <div class="player-name">${playerName}</div>
            `;

            // 添加点击事件 - 查看玩家详情
            playerItem.addEventListener('click', () => {
                // 设置正在查看玩家详情标志
                isViewingPlayerDetails = true;

                // 移除其他玩家的活动状态
                document.querySelectorAll('#playerList li').forEach(item => {
                    item.classList.remove('active');
                });

                // 标记当前玩家为活动状态
                playerItem.classList.add('active');

                // 加载玩家详情
                loadPlayerInfo(playerName, currentServer);
            });

            playerList.appendChild(playerItem);
        });
    }

    // 添加清理物品栏数据的函数 - 移除装备栏和副手物品
    function cleanInventoryData(inventory) {
        if (!Array.isArray(inventory)) return [];

        // 创建一个新数组，只包含前36个物品（真正的物品栏物品）
        const cleanedInventory = [];

        // 在Minecraft 1.12.2中，物品栏索引结构：
        // 0-8: 快捷栏
        // 9-35: 主背包
        // 36-39: 装备栏（头盔、胸甲、护腿、靴子）
        // 40: 副手

        for (let i = 0; i < inventory.length; i++) {
            // 只保留索引0-35的物品
            if (i < 36) {
                cleanedInventory.push(inventory[i]);
            }
        }

        console.log(`物品栏数据清理：原始物品数 ${inventory.length}，清理后物品数 ${cleanedInventory.length}`);
        return cleanedInventory;
    }

    function updateDebugInfo(data) {
        // 更新原始数据
        document.getElementById('debugRawData').textContent =
            JSON.stringify(data, null, 2);

        // 更新物品栏数据
        const inventoryItems = data.inventory
            ? data.inventory.slice(0, 36).map((item, index) => ({index, ...item}))
                .filter(item => item.type && item.type !== 'AIR')
            : [];
        document.getElementById('debugInventoryData').textContent =
            JSON.stringify(inventoryItems, null, 2);

        // 更新装备栏数据
        const armorItems = data.armor
            ? data.armor.map((item, index) => ({
                index: index + 36,
                slot: ['靴子', '护腿', '胸甲', '头盔'][index],
                ...item
            })).filter(item => item.type && item.type !== 'AIR')
            : [];
        document.getElementById('debugArmorData').textContent =
            JSON.stringify(armorItems, null, 2);

        // 更新主副手数据
        const handItems = {
            mainHand: data.mainHand || null,
            offHand: data.offHand || null
        };
        document.getElementById('debugHandData').textContent =
            JSON.stringify(handItems, null, 2);
    }

    // 处理玩家详细信息
    function handlePlayerDetails(data) {
        console.log('收到玩家详细信息:', data);
        isViewingPlayerDetails = true;

        // 当接收到玩家详情时，取消之前的自动刷新计时
        lastPlayerListUpdate = Date.now();

        if (data) {
            if (data.error) {
                showError('无法获取玩家信息: ' + data.error);
                return;
            }

            // 显示玩家名称
            const playerNameElement = document.getElementById('playerNameStatus');
            if (playerNameElement) {
                playerNameElement.textContent = data.username || '--';
            }

            // 显示玩家等级
            const playerLevelElement = document.getElementById('playerLevelStatus');
            if (playerLevelElement) {
                playerLevelElement.textContent = data.level || '0';
            }

            // 更新生命值
            const maxHealth = data.maxHealth || 20;
            const health = data.health || 0;
            const healthPercentage = Math.min(100, Math.max(0, (health / maxHealth) * 100));

            const healthBar = document.getElementById('healthBar');
            const healthValue = document.getElementById('healthValue');

            if (healthBar) {
                healthBar.style.width = healthPercentage + '%';
                // 根据生命值百分比设置颜色
                if (healthPercentage > 60) {
                    healthBar.style.backgroundColor = '#4caf50'; // 绿色
                } else if (healthPercentage > 30) {
                    healthBar.style.backgroundColor = '#ff9800'; // 橙色
                } else {
                    healthBar.style.backgroundColor = '#f44336'; // 红色
                }
            }

            if (healthValue) {
                healthValue.textContent = health + '/' + maxHealth;
            }

            // 显示坐标信息
            const playerCoordsX = document.getElementById('playerCoordsX');
            const playerCoordsY = document.getElementById('playerCoordsY');
            const playerCoordsZ = document.getElementById('playerCoordsZ');

            if (playerCoordsX && playerCoordsY && playerCoordsZ) {
                playerCoordsX.textContent = Math.round(data.x || 0);
                playerCoordsY.textContent = Math.round(data.y || 0);
                playerCoordsZ.textContent = Math.round(data.z || 0);
            }

            // 更新物品栏
            updateInventorySlots(data.inventory);

            // 更新装备栏
            updateEquipment(data.equipment);

            // 更新皮肤
            if (window.SkinViewer && data.username) {
                window.SkinViewer.loadSkin(data.username);
            }

            // 更新自定义占位符信息
            if (data.placeholders) {
                updateCustomPlaceholders(data.placeholders);
            }

        }
    }

    // 添加诊断函数，方便在控制台排查问题
    window.diagnoseMappings = function() {
        console.log('======= 映射文件诊断 =======');
        console.log('当前itemIconMapping大小:', Object.keys(itemIconMapping).length);
        console.log('当前itemTranslations大小:', Object.keys(itemTranslations).length);

        // 检查几个关键映射是否存在
        console.log('检查DIAMOND_SWORD映射:',
                   'itemIconMapping:', itemIconMapping['DIAMOND_SWORD'] || '未找到',
                   'itemTranslations:', itemTranslations['DIAMOND_SWORD'] || '未找到');

        console.log('检查IRON_HELMET映射:',
                   'itemIconMapping:', itemIconMapping['IRON_HELMET'] || '未找到',
                   'itemTranslations:', itemTranslations['IRON_HELMET'] || '未找到');

        // 尝试再次从当前页面路径加载
        console.log('尝试从当前路径重新加载配置文件...');
        const currentUrl = window.location.href;
        const basePath = currentUrl.substring(0, currentUrl.lastIndexOf('/') + 1);
        console.log('当前页面基础路径:', basePath);

        // 尝试上一级目录（web目录）
        const parentPath = basePath + '../';
        console.log('尝试上一级目录:', parentPath);

        Promise.all([
            fetch(parentPath + 'itemIcons.cnf')
                .then(r => r.ok ? r.text() : Promise.reject('请求失败: ' + r.status))
                .catch(() => fetch(basePath + 'itemIcons.cnf')
                    .then(r => r.ok ? r.text() : Promise.reject('请求失败: ' + r.status))),
            fetch(parentPath + 'itemTranslations.cnf')
                .then(r => r.ok ? r.text() : Promise.reject('请求失败: ' + r.status))
                .catch(() => fetch(basePath + 'itemTranslations.cnf')
                    .then(r => r.ok ? r.text() : Promise.reject('请求失败: ' + r.status)))
        ])
        .then(([iconText, translationText]) => {
            console.log('成功从当前路径获取配置文件');
            console.log('itemIcons.cnf 大小:', iconText.length, '字节');
            console.log('itemTranslations.cnf 大小:', translationText.length, '字节');

            const tempIconMap = {};
            const tempTransMap = {};

            // 解析图标映射
            iconText.split('\n').forEach(line => {
                if (line.trim().startsWith('#') || line.trim() === '') return;
                const parts = line.split('=');
                if (parts.length >= 2) {
                    tempIconMap[parts[0].trim()] = parts[1].trim();
                }
            });

            // 解析翻译映射
            translationText.split('\n').forEach(line => {
                if (line.trim().startsWith('#') || line.trim() === '') return;
                const parts = line.split('=');
                if (parts.length >= 2) {
                    tempTransMap[parts[0].trim()] = parts[1].trim();
                }
            });

            console.log('临时解析出的itemIconMapping大小:', Object.keys(tempIconMap).length);
            console.log('临时解析出的itemTranslations大小:', Object.keys(tempTransMap).length);

            // 如果这些临时映射有效，则更新全局映射
            if (Object.keys(tempIconMap).length > 0) {
                console.log('更新全局itemIconMapping...');
                Object.assign(itemIconMapping, tempIconMap);
            }

            if (Object.keys(tempTransMap).length > 0) {
                console.log('更新全局itemTranslations...');
                Object.assign(itemTranslations, tempTransMap);
            }

            console.log('诊断和重载完成，请刷新页面查看是否解决问题');
        })
        .catch(error => {
            console.error('诊断过程中尝试重新加载配置文件失败:', error);
        });

        return '诊断执行完毕，请查看控制台输出';
    };

    // 试图从多个可能的路径加载配置文件
    function tryAllConfigPaths() {
        console.log('开始尝试从多个路径加载配置文件...');

        // 定义可能的路径
        const possiblePaths = [
            '../',           // 上一级路径（web目录）
            './',           // 当前页面路径
            '/web/',        // web文件夹（根目录）
            'web/',         // 相对于根目录
            '/playerinfo/web/' // 插件特定路径
        ];

        let iconMappingLoaded = false;
        let translationsLoaded = false;

        // 依次尝试每个路径
        function tryNextPath(index) {
            if (index >= possiblePaths.length) {
                console.log('所有路径均已尝试');
                // 如果所有路径都尝试失败，回退到原始加载方法
                if (!iconMappingLoaded) {
                    console.warn('所有路径尝试加载itemIcons.cnf均失败，使用原始方法...');
                    loadItemIconsMapping();
                }

                if (!translationsLoaded) {
                    console.warn('所有路径尝试加载itemTranslations.cnf均失败，使用原始方法...');
                    loadItemTranslations();
                }
                return;
            }

            const basePath = possiblePaths[index];
            console.log(`尝试路径[${index+1}/${possiblePaths.length}]: ${basePath}`);

            // 尝试加载图标映射
            fetch(basePath + 'itemIcons.cnf')
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`HTTP错误: ${response.status}`);
                    }
                    return response.text();
                })
                .then(iconText => {
                    console.log(`成功从 ${basePath} 加载图标映射，长度: ${iconText.length}`);

                    // 一旦加载成功，就解析图标映射
                    const tempIconMap = {};
                    iconText.split('\n').forEach(line => {
                        if (line.trim().startsWith('#') || line.trim() === '') return;
                        const parts = line.split('=');
                        if (parts.length >= 2) {
                            tempIconMap[parts[0].trim()] = parts[1].trim();
                        }
                    });

                    if (Object.keys(tempIconMap).length > 0) {
                        console.log(`成功解析图标映射，包含 ${Object.keys(tempIconMap).length} 个项目`);
                        Object.assign(itemIconMapping, tempIconMap);
                        iconMappingLoaded = true;
                    }

                    // 尝试加载翻译
                    return fetch(basePath + 'itemTranslations.cnf');
                })
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`HTTP错误: ${response.status}`);
                    }
                    return response.text();
                })
                .then(transText => {
                    console.log(`成功从 ${basePath} 加载翻译映射，长度: ${transText.length}`);

                    // 解析翻译映射
                    const tempTransMap = {};
                    transText.split('\n').forEach(line => {
                        if (line.trim().startsWith('#') || line.trim() === '') return;
                        const parts = line.split('=');
                        if (parts.length >= 2) {
                            tempTransMap[parts[0].trim()] = parts[1].trim();
                        }
                    });

                    if (Object.keys(tempTransMap).length > 0) {
                        console.log(`成功解析翻译映射，包含 ${Object.keys(tempTransMap).length} 个项目`);
                        Object.assign(itemTranslations, tempTransMap);
                        translationsLoaded = true;
                    }

                    // 如果两个文件都已加载成功，就不需要尝试其他路径了
                    if (iconMappingLoaded && translationsLoaded) {
                        console.log('配置文件已成功加载，不再尝试其他路径');

                        // 打印一些示例映射作为验证
                        console.log('示例图标映射 (DIAMOND_SWORD):', itemIconMapping['DIAMOND_SWORD']);
                        console.log('示例翻译映射 (DIAMOND_SWORD):', itemTranslations['DIAMOND_SWORD']);
                    } else {
                        // 否则继续尝试下一个路径
                        tryNextPath(index + 1);
                    }
                })
                .catch(error => {
                    console.warn(`从路径 ${basePath} 加载配置失败:`, error.message);
                    // 出错时尝试下一个路径
                    tryNextPath(index + 1);
                });
        }

        // 开始尝试第一个路径
        tryNextPath(0);
    }
});