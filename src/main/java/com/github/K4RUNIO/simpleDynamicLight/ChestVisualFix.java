package com.github.K4RUNIO.simpleDynamicLight;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChestVisualFix {
    private final SimpleDynamicLight plugin;
    private final int checkRadius;
    private final int updateDistance;
    private final ExecutorService executor;
    private final ConcurrentHashMap<Location, Boolean> chestCache;

    public ChestVisualFix(SimpleDynamicLight plugin) {
        this.plugin = plugin;
        this.checkRadius = plugin.getConfig().getInt("chest.check-radius", 2);
        this.updateDistance = plugin.getConfig().getInt("chest.update-distance", 28);
        
        
        int threads = plugin.getConfig().getInt("threading.chest-check-threads", 2);
        this.executor = Executors.newFixedThreadPool(Math.max(1, Math.min(4, threads))); // Clamp between 1-4 threads
        this.chestCache = new ConcurrentHashMap<>();
    }

    public void handleChestVisuals(Location lightLocation) {
        executor.execute(() -> {
            // Async check for nearby chests
            boolean hasChest = isNearLargeChest(lightLocation);
            chestCache.put(lightLocation, hasChest);
            
            if (hasChest) {
                // Schedule sync task for Bukkit API operations
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        updateNearbyPlayers(lightLocation);
                        updateChestBlocks(lightLocation);
                    }
                }.runTaskLater(plugin, 2L);
            }
        });
    }

    private void updateNearbyPlayers(Location location) {
        double updateDistanceSquared = updateDistance * updateDistance;
        Location cachedLoc = location.clone();
        
        Bukkit.getOnlinePlayers().stream()
            .filter(player -> player.getWorld().equals(location.getWorld()))
            .filter(player -> player.getLocation().distanceSquared(cachedLoc) < updateDistanceSquared)
            .forEach(Player::updateInventory);
    }

    private void updateChestBlocks(Location location) {
        Block center = location.getBlock();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = center.getRelative(x, 0, z);
                if (block.getState() instanceof Chest) {
                    block.getState().update(false, false);
                }
            }
        }
    }

    private boolean isNearLargeChest(Location location) {
        // Check cache first
        Boolean cached = chestCache.get(location);
        if (cached != null) {
            return cached;
        }

        Block center = location.getBlock();
        for (int x = -checkRadius; x <= checkRadius; x++) {
            for (int z = -checkRadius; z <= checkRadius; z++) {
                if (x == 0 && z == 0) continue;
                
                Block block = center.getRelative(x, 0, z);
                if (isChestBlock(block)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isChestBlock(Block block) {
        if (block == null) return false;
        
        try {
            return switch (block.getType()) {
                case CHEST, TRAPPED_CHEST -> 
                    ((Chest)block.getState()).getInventory() instanceof DoubleChestInventory;
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }

    public void shutdown() {
        executor.shutdown();
        chestCache.clear();
    }
}
