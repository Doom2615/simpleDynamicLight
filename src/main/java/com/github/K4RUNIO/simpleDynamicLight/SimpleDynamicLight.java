package com.github.K4RUNIO.simpleDynamicLight;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SimpleDynamicLight extends JavaPlugin {
    private final Set<UUID> disabledPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        getServer().getConsoleSender().sendMessage("[SimpleDynamicLight] §aSimpleDynamicLight plugin loaded successfully!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("dynlight")) {
            if (args.length == 0) {
                if (sender.isOp() || sender.hasPermission("simpledynamiclight.reload")) {
                    sender.sendMessage("§c§l/dynlight reload - Reload plugin");
                    sender.sendMessage("§c§l/dynlight on - Turn on dynamic light");
                    sender.sendMessage("§c§l/dynlight off - Turn off dynamic light");
                } else {
                    sender.sendMessage("§c§l/dynlight on - Turn on dynamic light");
                    sender.sendMessage("§c§l/dynlight off - Turn off dynamic light");
                }
                return true;
            }

            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "on":
                case "off":
                    if (!(sender instanceof Player)) {
                        getServer().getConsoleSender().sendMessage("§cThis command can only be used by players.");
                        return true;
                    }

                    Player player = (Player) sender;
                    if (subCommand.equals("on")) {
                        disabledPlayers.remove(player.getUniqueId());
                        player.sendMessage("§aDynamic lighting enabled.");
                    } else {
                        disabledPlayers.add(player.getUniqueId());
                        player.sendMessage("§cDynamic lighting disabled.");
                    }
                    break;

                case "reload":
                    if (!sender.isOp() && !sender.hasPermission("simpledynamiclight.reload")) {
                        sender.sendMessage("§cYou do not have permission to reload the plugin.");
                        return true;
                    }

                    reloadConfig();
                    sender.sendMessage("SimpleDynamicLight configuration reloaded!");
                    break;

                default:
                    if (sender.isOp() || sender.hasPermission("simpledynamiclight.reload")) {
                        sender.sendMessage("§c§l/dynlight reload - Reload plugin");
                        sender.sendMessage("§c§l/dynlight on - Turn on dynamic light");
                        sender.sendMessage("§c§l/dynlight off - Turn off dynamic light");
                    } else {
                        sender.sendMessage("§c§l/dynlight on - Turn on dynamic light");
                        sender.sendMessage("§c§l/dynlight off - Turn off dynamic light");
                    }
                    break;
            }

            return true;
        }

        return false;
    }

    public boolean isDynamicLightEnabled(Player player) {
        return !disabledPlayers.contains(player.getUniqueId());
    }
}