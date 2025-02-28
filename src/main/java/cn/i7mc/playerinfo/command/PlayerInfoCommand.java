package cn.i7mc.playerinfo.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import cn.i7mc.playerinfo.PlayerInfo;

/**
 * PlayerInfo命令处理类
 */
public class PlayerInfoCommand implements CommandExecutor {
    
    private final PlayerInfo plugin;
    
    /**
     * 构造函数
     * 
     * @param plugin 插件实例
     */
    public PlayerInfoCommand(PlayerInfo plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showInfo(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload":
                handleReload(sender);
                break;
                
            case "status":
                handleStatus(sender);
                break;
                
            case "version":
                handleVersion(sender);
                break;
                
            case "help":
                showHelp(sender);
                break;
                
            default:
                sender.sendMessage("§c未知的子命令. 使用 /playerinfo help 查看可用命令");
                break;
        }
        
        return true;
    }
    
    /**
     * 处理reload子命令
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("playerinfo.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return;
        }
        
        plugin.reload();
        sender.sendMessage("§a配置已重新加载");
    }
    
    /**
     * 处理status子命令
     */
    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission("playerinfo.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return;
        }
        
        int playerCount = plugin.getPlayerController().getPlayersData().size();
        boolean webServerRunning = plugin.getWebServer() != null;
        
        sender.sendMessage("§6===== PlayerInfo 状态 =====");
        sender.sendMessage("§e插件状态: §a运行中");
        sender.sendMessage("§e当前跟踪的玩家数: §f" + playerCount);
        sender.sendMessage("§eWeb服务器: §f" + (webServerRunning ? "§a运行中" : "§c未运行"));
        
        if (webServerRunning) {
            int port = plugin.getPlugin().getConfig().getInt("web-server.port", 25580);
            boolean external = plugin.getPlugin().getConfig().getBoolean("web-server.allow-external-access", false);
            sender.sendMessage("§eWeb服务器端口: §f" + port);
            sender.sendMessage("§e允许外部访问: §f" + (external ? "是" : "否"));
        }
    }
    
    /**
     * 处理version子命令
     */
    private void handleVersion(CommandSender sender) {
        String version = plugin.getPlugin().getDescription().getVersion();
        sender.sendMessage("§6PlayerInfo 版本: §f" + version);
    }
    
    /**
     * 显示帮助信息
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6===== PlayerInfo 帮助 =====");
        sender.sendMessage("§e/playerinfo §f- 显示插件信息");
        sender.sendMessage("§e/playerinfo reload §f- 重新加载配置");
        sender.sendMessage("§e/playerinfo status §f- 查看插件状态");
        sender.sendMessage("§e/playerinfo version §f- 显示插件版本");
        sender.sendMessage("§e/playerinfo help §f- 显示此帮助信息");
    }
    
    /**
     * 显示插件信息
     */
    private void showInfo(CommandSender sender) {
        String version = plugin.getPlugin().getDescription().getVersion();
        int playerCount = plugin.getPlayerController().getPlayersData().size();
        
        sender.sendMessage("§6===== PlayerInfo 插件 =====");
        sender.sendMessage("§e版本: §f" + version);
        sender.sendMessage("§e当前跟踪的玩家数: §f" + playerCount);
        sender.sendMessage("§e使用 §f/playerinfo help §e查看帮助");
    }
} 