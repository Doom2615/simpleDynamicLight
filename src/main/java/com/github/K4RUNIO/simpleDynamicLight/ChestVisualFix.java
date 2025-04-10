package com.github.K4RUNIO.simpleDynamicLight;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.DoubleChestInventory;

public class ChestVisualFix implements Listener {
    private final SimpleDynamicLight plugin;
    
    public ChestVisualFix(SimpleDynamicLight plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles visual updates when lights are placed near chests
     * @param lightLocation The location where light was placed
     */
    public void handleChestVisuals(Location lightLocation) {
        if (!isNearLargeChest(lightLocation)) return;
        
        // Delay the update to ensure proper rendering
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateNearbyPlayers(lightLocation);
            updateChestBlocks(lightLocation);
        }, 3L); // 3 ticks delay
    }
    
    private void updateNearbyPlayers(Location location) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(location.getWorld()) && 
                player.getLocation().distance(location) < 32) {
                player.updateInventory();
            }
        }
    }
    
    private void updateChestBlocks(Location location) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block block = location.getBlock().getRelative(x, 0, z);
                if (block.getState() instanceof Chest) {
                    block.getState().update(true, false); // Force update without physics
                }
            }
        }
    }
    
    /**
     * Checks if a location is near a large chest
     */
    private boolean isNearLargeChest(Location location) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                Block block = location.getBlock().getRelative(x, 0, z);
                if (isChestBlock(block)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Checks if a block is a large chest
     */
    private boolean isChestBlock(Block block) {
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
            return false;
        }
        // Check if it's a double chest
        return ((Chest)block.getState()).getInventory() instanceof DoubleChestInventory;
    }
}
