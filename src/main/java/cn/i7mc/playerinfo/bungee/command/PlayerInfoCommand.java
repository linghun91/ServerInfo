package cn.i7mc.playerinfo.bungee.command;

import cn.i7mc.playerinfo.bungee.PlayerInfoBungee;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;

/**
 * BungeeCord插件命令处理类
 */
public class PlayerInfoCommand extends Command {
    private final PlayerInfoBungee plugin;
    
    /**
     * 构造函数
     *
     * @param plugin 插件实例
     */
    public PlayerInfoCommand(PlayerInfoBungee plugin) {
        super("playerinfo", "playerinfo.admin", "pinfo", "pi");
        this.plugin = plugin;
    }
    
    /**
     * 命令执行
     */
    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length > 0) {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "reload":
                    handleReload(sender);
                    break;
                    
                case "servers":
                    handleServers(sender);
                    break;
                    
                case "help":
                    showHelp(sender);
                    break;
                    
                default:
                    sender.sendMessage(new TextComponent("§c未知的子命令. 使用 /playerinfo help 查看可用命令"));
                    break;
            }
        } else {
            showInfo(sender);
        }
    }
    
    /**
     * 处理reload子命令
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("playerinfo.admin")) {
            sender.sendMessage(new TextComponent("§c你没有权限执行此命令"));
            return;
        }

        // 调用完整的reload方法，包括重新加载认证系统
        plugin.reload();

        sender.sendMessage(new TextComponent("§a配置已重新加载"));
    }
    
    /**
     * 处理servers子命令，显示已连接的服务器信息
     */
    private void handleServers(CommandSender sender) {
        if (!sender.hasPermission("playerinfo.admin")) {
            sender.sendMessage(new TextComponent("§c你没有权限执行此命令"));
            return;
        }
        
        // 获取已注册的服务器
        sender.sendMessage(new TextComponent("§6===== 已注册的服务器 ====="));
        
        // 遍历proxy中的服务器
        plugin.getProxy().getServers().forEach((name, serverInfo) -> {
            String status = serverInfo.getPlayers().size() > 0 ? "§a在线" : "§c离线或无玩家";
            String playerCount = "§f" + serverInfo.getPlayers().size() + " 玩家";
            
            sender.sendMessage(new TextComponent(String.format("§e%s: %s - %s", 
                name, status, playerCount)));
        });
    }
    
    /**
     * 显示插件信息
     */
    private void showInfo(CommandSender sender) {
        sender.sendMessage(new TextComponent("§6===== PlayerInfo BungeeCord 插件 ====="));
        sender.sendMessage(new TextComponent("§e版本: §f" + plugin.getDescription().getVersion()));
        
        // 获取配置信息
        int port = plugin.getConfig().getInt("web-server.port", 25581);
        boolean allowExternalAccess = plugin.getConfig().getBoolean("web-server.allow-external-access", false);
        
        sender.sendMessage(new TextComponent("§eWeb服务器端口: §f" + port));
        sender.sendMessage(new TextComponent("§e允许外部访问: §f" + (allowExternalAccess ? "是" : "否")));
        sender.sendMessage(new TextComponent("§e使用 §f/playerinfo help §e查看帮助"));
    }
    
    /**
     * 显示帮助信息
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(new TextComponent("§6===== PlayerInfo BungeeCord 帮助 ====="));
        sender.sendMessage(new TextComponent("§e/playerinfo §f- 显示插件信息"));
        sender.sendMessage(new TextComponent("§e/playerinfo reload §f- 重新加载配置"));
        sender.sendMessage(new TextComponent("§e/playerinfo servers §f- 显示已注册的服务器"));
        sender.sendMessage(new TextComponent("§e/playerinfo help §f- 显示此帮助信息"));
        sender.sendMessage(new TextComponent("§e别名: §f/pinfo, /pi"));
    }
} 