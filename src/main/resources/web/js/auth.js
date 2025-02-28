/**
 * ServerInfo登录验证系统
 * 处理登录表单提交和会话管理
 */

// 页面加载完成后执行
document.addEventListener('DOMContentLoaded', function() {
    // 获取表单元素
    const loginForm = document.getElementById('loginForm');
    const errorMessage = document.getElementById('errorMessage');
    const loadingIndicator = document.getElementById('loading');
    
    // 检查是否有重定向参数
    const urlParams = new URLSearchParams(window.location.search);
    const redirectUrl = urlParams.get('redirect') || '/index.html';
    
    // 检查是否已经登录
    checkSession(function(isLoggedIn) {
        if (isLoggedIn) {
            // 已登录，直接重定向
            window.location.href = redirectUrl;
        }
    });
    
    // 添加表单提交事件监听
    if (loginForm) {
        loginForm.addEventListener('submit', function(event) {
            event.preventDefault();
            
            // 获取表单数据
            const username = document.getElementById('username').value;
            const password = document.getElementById('password').value;
            const rememberMe = document.getElementById('rememberMe').checked;
            
            // 验证表单
            if (!username || !password) {
                showError('用户名和密码不能为空');
                return;
            }
            
            // 显示加载指示器
            showLoading(true);
            
            // 发送登录请求
            login(username, password, rememberMe);
        });
    }
    
    /**
     * 发送登录请求
     * @param {string} username 用户名
     * @param {string} password 密码
     * @param {boolean} rememberMe 是否记住登录状态
     */
    function login(username, password, rememberMe) {
        // 创建请求数据
        const data = {
            username: username,
            password: password,
            rememberMe: rememberMe
        };
        
        // 发送POST请求
        fetch('/api/auth/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify(data)
        })
        .then(response => response.json())
        .then(data => {
            // 隐藏加载指示器
            showLoading(false);
            
            if (data.success) {
                // 登录成功，重定向到目标页面
                window.location.href = redirectUrl;
            } else {
                // 显示错误消息
                showError(data.message || '登录失败，请检查用户名和密码');
            }
        })
        .catch(error => {
            // 隐藏加载指示器
            showLoading(false);
            
            // 显示错误消息
            showError('登录请求失败，请稍后重试');
            console.error('登录请求错误:', error);
        });
    }
    
    /**
     * 检查会话状态
     * @param {Function} callback 回调函数，参数为是否已登录
     */
    function checkSession(callback) {
        // 发送GET请求检查会话状态
        fetch('/api/auth/check', {
            method: 'GET',
            credentials: 'include'
        })
        .then(response => response.json())
        .then(data => {
            callback(data.loggedIn === true);
        })
        .catch(error => {
            console.error('检查会话状态错误:', error);
            callback(false);
        });
    }
    
    /**
     * 显示错误消息
     * @param {string} message 错误消息
     */
    function showError(message) {
        if (errorMessage) {
            errorMessage.textContent = message;
            errorMessage.style.display = 'block';
            
            // 添加抖动效果
            errorMessage.classList.add('shake');
            setTimeout(() => {
                errorMessage.classList.remove('shake');
            }, 500);
        }
    }
    
    /**
     * 显示/隐藏加载指示器
     * @param {boolean} show 是否显示
     */
    function showLoading(show) {
        if (loadingIndicator) {
            loadingIndicator.style.display = show ? 'block' : 'none';
        }
        
        // 禁用/启用表单
        const inputs = loginForm.querySelectorAll('input, button');
        inputs.forEach(input => {
            input.disabled = show;
        });
    }
    
    // 添加输入框聚焦时清除错误消息
    const inputs = document.querySelectorAll('input');
    inputs.forEach(input => {
        input.addEventListener('focus', function() {
            if (errorMessage) {
                errorMessage.textContent = '';
            }
        });
    });
});

// 添加Minecraft风格的音效
function playClickSound() {
    const audio = new Audio('/sounds/click.ogg');
    audio.volume = 0.5;
    audio.play().catch(e => console.log('无法播放音效:', e));
} 