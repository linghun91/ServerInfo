/**
 * placeholder.js - 处理自定义占位符的前端显示
 * 该脚本负责将服务器发送的占位符数据动态渲染到界面上
 */

// 在DOM加载完成后初始化
document.addEventListener('DOMContentLoaded', () => {
    console.log('占位符处理模块已加载');
});

/**
 * 占位符管理器
 */
const PlaceholderManager = {
    // 存储当前显示的占位符元素
    elements: [],
    
    // 经济信息容器
    economyContainer: null,

    // 静态中文名称映射表（从配置文件加载）
    staticNameMapping: {},
    
    // 映射配置加载状态
    mappingLoaded: false,
    
    /**
     * 初始化占位符管理器
     */
    init() {
        // 获取经济信息容器
        this.economyContainer = document.querySelector('.economy-container');
        if (!this.economyContainer) {
            console.error('占位符初始化失败：未找到经济信息容器元素');
            return false;
        }
        
        // 添加滚动事件监听
        this.setupScrollListener();
        
        // 加载外部映射配置
        this.loadNameMapping()
            .then(mappingData => {
                this.staticNameMapping = mappingData;
                this.mappingLoaded = true;
                console.log('名称映射加载成功:', this.staticNameMapping);
                
                // 如果页面已经加载了占位符，使用新配置重新渲染
                if (document.querySelectorAll('.custom-placeholder').length > 0) {
                    console.log('检测到占位符已加载，使用新配置重新渲染');
                    // 触发事件通知重新加载数据
                    const event = new CustomEvent('namemap-loaded');
                    document.dispatchEvent(event);
                }
            })
            .catch(error => {
                console.error('名称映射加载失败:', error);
                this.mappingLoaded = false;
            });
        
        console.log('占位符模块初始化完成');
        return true;
    },
    
    /**
     * 设置滚动事件监听
     */
    setupScrollListener() {
        if (!this.economyContainer) return;
        
        // 初始状态设置
        this.checkScrollPosition();
        
        // 监听滚动事件
        this.economyContainer.addEventListener('scroll', () => {
            this.checkScrollPosition();
        });
        
        // 窗口大小变化时重新检查
        window.addEventListener('resize', () => {
            this.checkScrollPosition();
        });
        
        console.log('滚动事件监听已设置');
    },
    
    /**
     * 检查滚动位置并更新样式类
     */
    checkScrollPosition() {
        if (!this.economyContainer) return;
        
        const { scrollTop, scrollHeight, clientHeight } = this.economyContainer;
        const isAtTop = scrollTop <= 5;
        const isAtBottom = scrollTop + clientHeight >= scrollHeight - 5;
        
        // 更新顶部和底部状态类
        this.economyContainer.classList.toggle('scrolled-top', isAtTop);
        this.economyContainer.classList.toggle('scrolled-bottom', isAtBottom);
        
        // 如果需要滚动（内容超过容器高度），添加scrollable类
        const needsScroll = scrollHeight > clientHeight;
        this.economyContainer.classList.toggle('scrollable', needsScroll);
    },
    
    /**
     * 从外部文件加载占位符名称映射
     */
    loadNameMapping() {
        console.log('开始加载名称映射文件...');
        return fetch('namemap.cnf')
            .then(response => {
                if (!response.ok) {
                    throw new Error(`加载映射配置失败: ${response.status}`);
                }
                console.log('映射文件请求成功，正在解析JSON...');
                return response.json();
            })
            .then(data => {
                console.log('映射文件解析成功:', data);
                return data; // 返回解析后的数据
            })
            .catch(error => {
                console.error('加载名称映射文件失败:', error);
                // 在控制台显示创建namemap.cnf的提示
                console.info('可以创建namemap.cnf文件，格式如下：');
                console.info('{\n    "economy": "金币",\n    "points": "点券",\n    "attack": "攻击力"\n}');
                return {}; // 加载失败时返回空对象
            });
    },
    
    /**
     * 更新所有占位符显示
     * @param {Array} placeholdersArray 占位符数据数组
     * @param {Object} nameMapping 名称映射（可选）
     */
    updatePlaceholders(placeholdersArray, nameMapping) {
        // 检查参数
        if (!placeholdersArray || !Array.isArray(placeholdersArray)) {
            console.error('更新占位符失败：无效的数据格式', placeholdersArray);
            return;
        }
        
        // 获取占位符容器
        const container = this.economyContainer;
        if (!container) {
            console.error('占位符容器不存在');
            return;
        }
        
        // 记录处理开始
        console.log(`开始处理 ${placeholdersArray.length} 个占位符, 名称映射:`, nameMapping);
        
        // 清除所有现有的占位符元素
        this.clearCustomPlaceholders();
        
        // 按优先级排序占位符
        const sortedPlaceholders = [...placeholdersArray].sort((a, b) => {
            return (a.priority || 999) - (b.priority || 999);
        });
        
        // 提前计算容器的可视区域高度
        const containerHeight = container.clientHeight;
        console.log(`占位符容器高度: ${containerHeight}px`);
        
        // 记录元素总数，用于监控可能的溢出
        let placeholderCount = 0;
        
        // 处理每个占位符
        for (const data of sortedPlaceholders) {
            if (data.enabled === false) {
                console.log(`跳过已禁用的占位符: ${data.id}`);
                continue;
            }
            
            try {
                // 获取有效名称
                let effectiveName = this.getEffectiveName(data, nameMapping);
                
                // 创建并添加占位符元素
                this.addCustomPlaceholder({
                    ...data,
                    name: effectiveName
                });
                
                placeholderCount++;
            } catch (e) {
                console.error(`处理占位符时出错:`, e, data);
            }
        }
        
        console.log(`已添加 ${placeholderCount} 个占位符元素`);
        
        // 检查是否需要添加滚动条
        this.checkForScrollbar();
    },
    
    /**
     * 显示滚动提示（当首次有足够多的条目需要滚动时）
     * @param {HTMLElement} container 占位符容器
     */
    showScrollTip(container) {
        // 该功能已禁用，不再显示滚动提示
        console.log('滚动提示已禁用');
    },
    
    /**
     * 尝试修复乱码字符串
     * @param {string} text 可能的乱码字符串
     * @return {string|null} 修复后的字符串，如果无法修复则返回null
     */
    tryFixGarbledText(text) {
        if (!text) return null;
        
        // 尝试使用不同的编码方式解码
        try {
            // 先检查是否为常见的乱码模式
            if (/^(\ufffd|\u00ef|\u00bf|\u00bd)+$/.test(text)) {
                console.log(`检测到可能的UTF-8乱码: ${text}`);
                return null; // 无法修复的UTF-8乱码
            }
            
            // 针对GBK/GB2312编码错误的乱码尝试修复
            if (text.includes('') || text.includes('')) {
                console.log(`检测到中文乱码模式: ${text}`);
                return null; // 无法在前端可靠修复
            }
        } catch (e) {
            console.error(`修复乱码时出错: ${e.message}`);
            return null;
        }
        
        return null; // 默认无法修复
    },
    
    /**
     * 检查文本是否为乱码
     * @param {String} text 要检查的文本
     * @return {Boolean} 是否为乱码
     */
    isGarbledText(text) {
        if (!text) return true;
        
        // 检查是否为纯英文ID（基于常见命名规则判断）
        if (/^[a-z][a-z0-9_]*$/.test(text)) {
            return true; // 这些可能是ID，不应该作为名称
        }
        
        // 检查是否包含常见乱码特征
        const suspiciousChars = /[\uFFFD\u0000-\u0008\u000B-\u000C\u000E-\u001F]/.test(text);
        
        // 检查是否为GBK/GB2312编码错误导致的中文乱码
        const commonChineseGarbled = text.includes('') || text.includes('') || 
                                    text.includes('ȯ') || text.includes('') ||
                                    /^[\u00e0-\u00ef\u0080-\u00bf]{2,}$/.test(text);
        
        // 检查是否有其他乱码模式
        const otherPatterns = /^(\?+|#+|\x00+)$/.test(text);
        
        return suspiciousChars || commonChineseGarbled || otherPatterns;
    },
    
    /**
     * 检查是否为未解析的占位符
     * @param {String} value 要检查的值
     * @returns {Boolean} 是否为未解析的占位符
     */
    isUnresolvedPlaceholder(value) {
        if (!value || typeof value !== 'string') return true;
        
        // 检查值是否为原始占位符格式（%开头和结尾）或以$开头
        return value.startsWith('%') && value.endsWith('%') || value.startsWith('$');
    },
    
    /**
     * 获取有效名称
     * @param {Object} data 占位符数据
     * @param {Object} nameMapping 名称映射
     * @return {String} 有效名称
     */
    getEffectiveName(data, nameMapping) {
        let effectiveName = null;
        
        // 尝试以下步骤获取有效的名称:
        // 1. 检查占位符本身的名称是否有效（非乱码）
        // 2. 检查有效的nameMapping
        // 3. 检查静态映射表
        // 4. 使用ID作为最后的备选
        
        if (!this.isGarbledText(data.name) && data.name !== data.id) {
            // 名称有效且不等于ID，直接使用
            effectiveName = data.name;
            console.log(`使用占位符自带名称: ${data.id} -> ${effectiveName}`);
        } else if (nameMapping && nameMapping[data.id]) {
            // 使用有效的映射名称
            effectiveName = nameMapping[data.id];
            console.log(`使用映射名称: ${data.id} -> ${effectiveName}`);
        } else if (this.staticNameMapping[data.id]) {
            // 使用静态映射表
            effectiveName = this.staticNameMapping[data.id];
            console.log(`使用静态备选名称: ${data.id} -> ${effectiveName}`);
        } else {
            // 所有修复方法都失败，使用ID
            effectiveName = data.id;
            console.warn(`无法获得有效名称: ${data.id}，使用ID作为显示名称`);
        }
        
        return effectiveName;
    },
    
    /**
     * 添加自定义占位符
     * @param {Object} data 占位符数据
     */
    addCustomPlaceholder(data) {
        if (!data.enabled) return;
        
        // 创建占位符元素
        const placeholderElement = document.createElement('div');
        placeholderElement.className = 'economy-item';
        placeholderElement.dataset.placeholderId = data.id;
        
        // 创建图标容器
        const iconContainer = document.createElement('div');
        iconContainer.className = 'economy-icon';
        
        // 创建图标
        const iconImg = document.createElement('img');
        iconImg.src = `img/items/${data.icon || 'barrier'}.png`;
        iconImg.alt = data.name || data.id;
        iconImg.onerror = function() {
            // 图标加载失败时使用默认图标
            this.src = 'img/items/barrier.png';
            console.warn(`占位符 ${data.id} 的图标加载失败，使用默认图标`);
        };
        
        // 创建详情容器
        const detailsContainer = document.createElement('div');
        detailsContainer.className = 'economy-details';
        
        // 创建数值元素
        const valueElement = document.createElement('div');
        valueElement.className = 'balance-amount';
        
        // 检查是否为未解析的占位符
        let displayValue = data.value;
        let isUnresolved = this.isUnresolvedPlaceholder(displayValue);
        
        if (isUnresolved) {
            // 显示占位符值为"未解析"，添加特殊样式
            displayValue = "0";
            valueElement.style.color = "#FF9800"; // 橙色，表示未解析
            valueElement.title = "占位符未解析: " + (data.placeholder || "未知占位符");
        } else {
            // 正常显示解析后的值
            displayValue = this.formatNumber(displayValue);
        }
        
        valueElement.textContent = displayValue;
        
        // 创建名称元素
        const nameElement = document.createElement('div');
        nameElement.className = 'currency-name';
        
        // 使用数据中的名称，如果没有则使用ID
        nameElement.textContent = data.name || data.id;
        
        // 组装DOM
        iconContainer.appendChild(iconImg);
        detailsContainer.appendChild(valueElement);
        detailsContainer.appendChild(nameElement);
        
        placeholderElement.appendChild(iconContainer);
        placeholderElement.appendChild(detailsContainer);
        
        // 将元素添加到容器
        this.economyContainer.appendChild(placeholderElement);
        
        // 存储元素引用
        this.elements.push(placeholderElement);
        
        console.log(`添加了占位符: ${data.id}, 值: ${displayValue}, 名称: ${data.name || data.id}`);
    },
    
    /**
     * 清除所有占位符元素
     */
    clearCustomPlaceholders() {
        // 清空容器中的所有元素
        if (this.economyContainer) {
            // 保存现有元素引用
            const existingElements = this.elements;
            
            // 移除容器中的所有占位符元素
            this.economyContainer.innerHTML = '';
            
            // 清空元素引用数组
            this.elements = [];
            
            console.log(`清除了所有占位符元素，共 ${existingElements.length} 个`);
        }
    },
    
    /**
     * 检查是否需要添加滚动条
     */
    checkForScrollbar() {
        const container = this.economyContainer;
        const economyInfo = document.querySelector('.economy-info');
        
        if (!container || !economyInfo) return;
        
        // 获取容器高度和所有子元素高度总和
        const containerHeight = economyInfo.clientHeight;
        let childrenHeight = 0;
        
        // 计算所有子元素高度
        Array.from(container.children).forEach(child => {
            childrenHeight += child.offsetHeight;
        });
        
        // 如果子元素总高度超过容器高度，添加滚动条
        if (childrenHeight > containerHeight) {
            container.style.overflowY = 'auto';
            container.style.maxHeight = containerHeight + 'px';
        } else {
            container.style.overflowY = 'visible';
            container.style.maxHeight = 'none';
        }
    },
    
    /**
     * 格式化数字（添加千位分隔符）
     * @param {String|Number} num 要格式化的数字
     * @return {String} 格式化后的数字字符串
     */
    formatNumber(num) {
        if (num === undefined || num === null) return '0';
        
        // 如果是字符串但包含斜杠，可能是比率格式（如10/100）
        if (typeof num === 'string' && num.includes('/')) {
            // 分割并格式化比率的每一部分
            const parts = num.split('/');
            return parts.map(part => this.formatNumber(part.trim())).join('/');
        }
        
        // 将字符串转换为数字
        let number = num;
        if (typeof num === 'string') {
            // 去除非数字字符（保留小数点）
            number = num.replace(/[^\d.-]/g, '');
            // 如果结果为空或无效，返回0
            if (number === '' || isNaN(Number(number))) {
                return num; // 返回原始值，可能是非数字的占位符
            }
            number = Number(number);
        }
        
        // 格式化数字，添加千位分隔符
        return new Intl.NumberFormat().format(number);
    }
};

// 导出模块
window.PlaceholderManager = PlaceholderManager; 