package com.github.K4RUNIO.simpleDynamicLight;
//Chest fixes
public class ChestVisualFix {
    private final SimpleDynamicLight plugin;
    
    public ChestVisualFix(SimpleDynamicLight plugin) {
        this.plugin = plugin;
    }
    
    public void handleChestVisuals(Location lightLocation) {
        if (!isNearLargeChest(lightLocation)) return;
        
        // Delay the update to ensure chest renders properly
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateNearbyPlayers(lightLocation);
            updateChestBlocks(lightLocation);
        }, 3L);
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
                    block.getState().update(true, false);
                }
            }
        }
    }
    
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
    
    private boolean isChestBlock(Block block) {
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
            return false;
        }
        return ((Chest)block.getState()).getInventory() instanceof DoubleChestInventory;
    }
}

