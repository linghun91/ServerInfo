package cn.i7mc.playerinfo.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import cn.i7mc.playerinfo.Bootstrap;
import org.bukkit.ChatColor;

/**
 * Bukkit插件入口点
 * 该类继承JavaPlugin，由Bukkit加载，然后初始化Bootstrap
 */
public class BukkitPlugin extends JavaPlugin {
    
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