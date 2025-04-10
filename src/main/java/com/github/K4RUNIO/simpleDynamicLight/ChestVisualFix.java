package com.github.K4RUNIO.simpleDynamicLight;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.DoubleChestInventory;

public class ChestVisualFix {
    private final SimpleDynamicLight plugin;
    private final int checkRadius;
    
    public ChestVisualFix(SimpleDynamicLight plugin) {
        this.plugin = plugin;
        this.checkRadius = plugin.getConfig().getInt("chest.check-radius", 3);
    }

    public void handleChestVisuals(Location lightLocation) {
        if (!isNearLargeChest(lightLocation)) return;
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateNearbyPlayers(lightLocation);
            updateChestBlocks(lightLocation);
        }, 2L); // Reduced from 3 ticks to 2
    }
    
    private void updateNearbyPlayers(Location location) {
        int updateDistance = plugin.getConfig().getInt("chest.update-distance", 32);
        double updateDistanceSquared = updateDistance * updateDistance;
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(location.getWorld()) && 
                player.getLocation().distanceSquared(location) < updateDistanceSquared) {
                player.updateInventory();
            }
        }
    }
    
    private void updateChestBlocks(Location location) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block block = location.getBlock().getRelative(x, 0, z);
                if (block.getState() instanceof Chest) {
                    block.getState().update(false, false); // No physics update
                }
            }
        }
    }
    
    private boolean isNearLargeChest(Location location) {
        for (int x = -checkRadius; x <= checkRadius; x++) {
            for (int z = -checkRadius; z <= checkRadius; z++) {
                Block block = location.getBlock().getRelative(x, 0, z);
                if (isChestBlock(block)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean isChestBlock(Block block) {
        if (block == null) return false;
        
        return switch (block.getType()) {
            case CHEST, TRAPPED_CHEST -> 
                ((Chest)block.getState()).getInventory() instanceof DoubleChestInventory;
            default -> false;
        };
    }
}
