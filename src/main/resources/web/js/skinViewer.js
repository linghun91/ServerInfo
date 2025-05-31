/**
 * 玩家皮肤渲染器
 * 使用2D模式显示Minecraft皮肤
 * 如果需要，可以通过WebGL诊断按钮查看WebGL支持情况
 */

class SkinViewer {
    constructor(containerId) {
        // 初始化变量
        this.container = document.getElementById(containerId);
        this.viewer = null;
        this.isInitialized = false;
        this.currentUUID = null;
        
        // 默认玩家名称和UUID - MoonlightCurse
        this.defaultPlayerName = "MoonlightCurse";
        this.defaultPlayerUUID = "69ac039bac8c40ac813d0e0d3a846795"; // 已知的MoonlightCurse UUID
        
        // 直接使用2D模式
        this.use2DFallback = true;
        
        // 显示加载状态
        this.showLoadingMessage();
        
        // 直接初始化2D模式
        this.initialize();
    }
    
    /**
     * 检测WebGL是否可用 (仅供诊断使用)
     */
    isWebGLAvailable() {
        try {
            // 尝试创建WebGL上下文
            const canvas = document.createElement('canvas');
            return !!(window.WebGLRenderingContext && 
                (canvas.getContext('webgl') || canvas.getContext('experimental-webgl')));
        } catch (e) {
            return false;
        }
    }
    
    /**
     * 初始化皮肤查看器
     */
    initialize() {
        try {
            // 使用2D模式
            this.initialize2DViewer();
        } catch (error) {
            console.error('初始化皮肤查看器失败:', error);
            this.showErrorMessage('初始化皮肤查看器失败，请刷新页面重试');
        }
    }
    
    /**
     * 初始化2D皮肤查看器
     */
    initialize2DViewer() {
        // 清空容器
        this.container.innerHTML = '';
        
        // 创建2D查看器元素
        const viewer2D = document.createElement("div");
        viewer2D.className = "skin-viewer-2d";
        
        // 创建皮肤容器
        const skinContainer = document.createElement("div");
        skinContainer.className = "skin-container";
        
        // 创建皮肤完整图
        this.fullSkinImage = document.createElement("img");
        this.fullSkinImage.className = "skin-full-image";
        this.fullSkinImage.alt = "Player Skin";
        
        // 添加到容器
        skinContainer.appendChild(this.fullSkinImage);
        viewer2D.appendChild(skinContainer);
        this.container.appendChild(viewer2D);
        
        // 添加2D专用样式
        const style = document.createElement('style');
        style.textContent = `
            .skin-viewer-2d {
                display: flex;
                align-items: center;
                justify-content: center;
                width: 100%;
                height: 100%;
                background-color: #1e272e;
                position: relative;
            }
            .skin-container {
                display: flex;
                justify-content: center;
                align-items: center;
                height: 100%;
            }
            .skin-full-image {
                height: 240px;
                image-rendering: pixelated;
                border: 2px solid #555;
                background-color: #2a2a2a;
                border-radius: 4px;
                box-shadow: 0 2px 10px rgba(0,0,0,0.3);
                transition: transform 0.3s ease;
            }
            .skin-full-image:hover {
                transform: scale(1.05);
            }
        `;
        document.head.appendChild(style);
        
        this.isInitialized = true;
        console.log('2D皮肤查看器初始化完成');
        
        // 加载DefaultSkin.png而不是默认皮肤
        this.loadLocalMoonlightCurseSkin();
        
        // 隐藏加载消息
        this.hideLoadingMessage();
    }
    
    /**
     * 根据玩家UUID加载皮肤
     * @param {string} playerName - 玩家名称
     * @param {string} uuid - 玩家UUID
     * @param {string} skinURL - 皮肤URL (不使用)
     */
    loadSkin(playerName, uuid, skinURL) {
        if (!this.isInitialized) {
            console.error('皮肤查看器尚未初始化');
            return;
        }
        
        // 如果是同一个玩家，不需要重新加载
        if (this.currentUUID === uuid) return;
        this.currentUUID = uuid;
        
        this.showLoadingMessage();
        console.log(`加载玩家 ${playerName} 的皮肤... UUID: ${uuid}`);
        
        try {
            // 使用Crafatar API获取皮肤 - 这是一个可靠的玩家皮肤API
            const renderUrl = `https://crafatar.com/renders/body/${uuid}?scale=10&overlay`;
            
            // 加载完整皮肤展示图
            this.fullSkinImage.src = renderUrl;
            
            // 在图片加载完成后隐藏加载消息
            this.fullSkinImage.onload = () => {
                this.hideLoadingMessage();
            };
            
            // 如果图片加载失败
            this.fullSkinImage.onerror = () => {
                console.error('加载皮肤失败');
                this.loadLocalMoonlightCurseSkin();
            };
        } catch (error) {
            console.error('皮肤加载过程出错:', error);
            this.showErrorMessage(`加载 ${playerName} 皮肤时出错`);
            this.loadLocalMoonlightCurseSkin();
        }
    }
    
    /**
     * 通过Mojang API获取玩家的UUID
     * @param {string} playerName - 玩家名称
     * @returns {Promise<string>} - 返回一个Promise，resolve为玩家UUID
     */
    async getPlayerUUID(playerName) {
        try {
            const response = await fetch(`https://api.mojang.com/users/profiles/minecraft/${playerName}`);
            if (!response.ok) {
                throw new Error(`获取UUID失败: ${response.status} ${response.statusText}`);
            }
            const data = await response.json();
            return data.id;
        } catch (error) {
            console.error(`获取玩家 ${playerName} 的UUID时出错:`, error);
            throw error;
        }
    }
    
    /**
     * 直接从本地加载MoonlightCurse的皮肤
     */
    loadLocalMoonlightCurseSkin() {
        console.log("加载本地缓存的MoonlightCurse皮肤...");
        
        // 使用版本戳来防止缓存
        const timestamp = new Date().getTime();
        // 修正皮肤文件的路径 - 应该指向DefaultSkin.png
        const skinPath = `/resources/web/DefaultSkin.png?v=${timestamp}`;
        
        // 尝试加载本地缓存的皮肤
        this.fullSkinImage.src = skinPath;
        
        // 在图片加载完成后隐藏加载消息
        this.fullSkinImage.onload = () => {
            console.log('成功加载本地MoonlightCurse皮肤');
            this.hideLoadingMessage();
        };
        
        // 如果本地皮肤加载失败，使用MoonlightCurse的Crafatar渲染
        this.fullSkinImage.onerror = () => {
            console.log('本地皮肤加载失败，尝试从Crafatar加载MoonlightCurse皮肤');
            // 使用已知的UUID
            if (this.defaultPlayerUUID) {
                this.fullSkinImage.src = `https://crafatar.com/renders/body/${this.defaultPlayerUUID}?scale=10&overlay`;
                this.fullSkinImage.onerror = () => {
                    // 如果从Crafatar加载失败，回退到默认的史蒂夫皮肤
                    console.log('从Crafatar加载MoonlightCurse皮肤失败，使用史蒂夫皮肤');
                    this.loadSteveDefaultSkin();
                };
            } else {
                this.loadSteveDefaultSkin();
            }
        };
    }
    
    /**
     * 加载MoonlightCurse的皮肤
     */
    async loadMoonlightCurseSkin() {
        try {
            console.log("正在加载MoonlightCurse的皮肤...");
            
            // 如果已经缓存了UUID，直接使用
            if (this.defaultPlayerUUID) {
                this.loadSkin(this.defaultPlayerName, this.defaultPlayerUUID);
                return;
            }
            
            // 获取MoonlightCurse的UUID
            const uuid = await this.getPlayerUUID(this.defaultPlayerName);
            console.log(`获取到玩家 ${this.defaultPlayerName} 的UUID: ${uuid}`);
            
            // 缓存UUID以便将来使用
            this.defaultPlayerUUID = uuid;
            
            // 加载皮肤
            this.loadSkin(this.defaultPlayerName, uuid);
            
            // 保存到localStorage，以便在页面刷新后仍然可用
            localStorage.setItem("defaultPlayerName", this.defaultPlayerName);
            localStorage.setItem("defaultPlayerUUID", uuid);
            
        } catch (error) {
            console.error(`加载 ${this.defaultPlayerName} 皮肤时出错:`, error);
            // 如果无法加载MoonlightCurse的皮肤，尝试加载本地皮肤
            this.loadLocalMoonlightCurseSkin();
        }
    }
    
    /**
     * 加载Steve的默认皮肤
     */
    loadSteveDefaultSkin() {
        console.log("加载Steve的默认皮肤...");
        const steveUUID = '8667ba71b85a4004af54457a9734eed7'; // Steve的UUID
        this.fullSkinImage.src = `https://crafatar.com/renders/body/${steveUUID}?scale=10&overlay`;
        
        // 图片加载完成后隐藏加载消息
        this.fullSkinImage.onload = () => {
            this.hideLoadingMessage();
        };
    }
    
    /**
     * 加载默认的皮肤
     */
    loadDefaultSkin() {
        // 直接加载本地MoonlightCurse皮肤，不再尝试从API获取
        this.loadLocalMoonlightCurseSkin();
    }
    
    /**
     * 处理窗口大小变化
     */
    onWindowResize() {
        // 2D模式不需要特殊处理窗口大小变化
    }
    
    /**
     * 显示加载消息
     */
    showLoadingMessage() {
        this.clearMessages();
        const loadingElement = document.createElement('div');
        loadingElement.className = 'skin-loading';
        loadingElement.textContent = '加载玩家皮肤中...';
        this.container.appendChild(loadingElement);
    }
    
    /**
     * 显示错误消息
     */
    showErrorMessage(message) {
        this.clearMessages();
        const errorElement = document.createElement('div');
        errorElement.className = 'skin-error';
        errorElement.textContent = message || '加载皮肤时出错';
        this.container.appendChild(errorElement);
    }
    
    /**
     * 清除所有消息元素
     */
    clearMessages() {
        const messages = this.container.querySelectorAll('.skin-loading, .skin-error');
        messages.forEach(msg => {
            try {
                this.container.removeChild(msg);
            } catch (e) {
                console.warn('清除消息元素失败:', e);
            }
        });
    }
    
    /**
     * 隐藏加载消息
     */
    hideLoadingMessage() {
        this.clearMessages();
    }
}

// 页面加载完成后初始化皮肤查看器
document.addEventListener('DOMContentLoaded', () => {
    // 创建全局皮肤查看器实例
    console.log('初始化皮肤查看器...');
    window.skinViewer = new SkinViewer('skinViewer');
}); 