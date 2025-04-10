com.github.K4RUNIO.simpleDynamicLight;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleDynamicLight extends JavaPlugin {
    private final Set<UUID> disabledPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private PlayerListener playerListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        
        this.playerListener = new PlayerListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        
        getLogger().info("Optimized Dynamic Light loaded successfully!");
    }

    @Override
    public void onDisable() {
        if (playerListener != null) {
            playerListener.disable();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("dynlight")) return false;
        
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "on":
            case "off":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                    return true;
                }
                handleToggle(player, subCommand.equals("on"));
                break;

            case "reload":
                if (!sender.hasPermission("simpledynamiclight.reload")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded!");
                break;

            default:
                sendUsage(sender);
                break;
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Dynamic Light Commands ===");
        if (sender.hasPermission("simpledynamiclight.reload")) {
            sender.sendMessage(ChatColor.YELLOW + "/dynlight reload - Reload config");
        }
        sender.sendMessage(ChatColor.YELLOW + "/dynlight on - Enable lights");
        sender.sendMessage(ChatColor.YELLOW + "/dynlight off - Disable lights");
    }

    private void handleToggle(Player player, boolean enable) {
        if (enable) {
            disabledPlayers.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Dynamic lights enabled!");
        } else {
            disabledPlayers.add(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "Dynamic lights disabled.");
        }
        playerListener.schedulePlayerUpdate(player);
    }

    public boolean isDynamicLightEnabled(Player player) {
        return !disabledPlayers.contains(player.getUniqueId());
    }
}
