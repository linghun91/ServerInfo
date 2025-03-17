package cn.i7mc.playerinfo.plugin;

import net.md_5.bungee.api.plugin.Plugin;
import cn.i7mc.playerinfo.Bootstrap;
import net.md_5.bungee.api.ChatColor;

/**
 * BungeeCord插件入口点
 * 该类继承Plugin，由BungeeCord加载，然后初始化Bootstrap
 */
public class BungeePlugin extends Plugin {
    
    @Override
    public void onEnable() {
        getLogger().info(ChatColor.DARK_AQUA + "正在初始化 PlayerInfo Bootstrap...");
        if (Bootstrap.init(this, getDataFolder(), getLogger())) {
            getLogger().info(ChatColor.DARK_AQUA + "PlayerInfo Bootstrap 初始化成功");
        } else {
            getLogger().severe("PlayerInfo Bootstrap 初始化失败");
        }
    }
    
    @Override
    public void onDisable() {
        getLogger().info(ChatColor.DARK_AQUA + "正在关闭 PlayerInfo...");
        Bootstrap.shutdown();
    }
} 