package com.github.K4RUNIO.simpleDynamicLight;


import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final SimpleDynamicLight plugin;
    private final Map<Player, Location> playerLightLocations = new HashMap<>();
    private final Map<Item, Location> itemLightLocations = new HashMap<>();

    public PlayerListener(SimpleDynamicLight plugin) {
        this.plugin = plugin;
        startLightUpdateTask();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();

        if (to == null || (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ())) {
            return;
        }

        if (!plugin.isDynamicLightEnabled(player)) {
            removePlayerLight(player);
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack mainHandItem = inventory.getItemInMainHand();
        ItemStack offHandItem = inventory.getItemInOffHand();

        int lightLevel = Math.max(getLightLevel(mainHandItem), getLightLevel(offHandItem));

        if (lightLevel > 0) {
            updatePlayerLight(player, to, lightLevel);
        } else {
            removePlayerLight(player);
        }
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();

        if (!plugin.isDynamicLightEnabled(player)) {
            removePlayerLight(player);
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack mainHandItem = inventory.getItemInMainHand();
        ItemStack offHandItem = inventory.getItemInOffHand();

        int lightLevel = Math.max(getLightLevel(mainHandItem), getLightLevel(offHandItem));

        if (lightLevel > 0) {
            updatePlayerLight(player, player.getLocation(), lightLevel);
        } else {
            removePlayerLight(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removePlayerLight(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (!plugin.isDynamicLightEnabled(player)) {
            return;
        }

        Item droppedItem = event.getItemDrop();
        ItemStack itemStack = droppedItem.getItemStack();
        int lightLevel = getLightLevel(itemStack);

        if (lightLevel > 0) {
            removePlayerLight(player);
            updateItemLight(droppedItem, lightLevel);
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack itemStack = item.getItemStack();
        int lightLevel = getLightLevel(itemStack);

        if (lightLevel > 0) {
            updateItemLight(item, lightLevel);
        }
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (itemLightLocations.containsKey(item)) {
            removeItemLight(item);

            if (event.getEntity() instanceof Player) {
                Player player = (Player) event.getEntity();

                if (!plugin.isDynamicLightEnabled(player)) {
                    return;
                }

                ItemStack itemStack = item.getItemStack();
                int lightLevel = getLightLevel(itemStack);

                if (lightLevel > 0) {
                    updatePlayerLight(player, player.getLocation(), lightLevel);
                }
            }
        }
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event) {
        Item item = event.getEntity();
        if (itemLightLocations.containsKey(item)) {
            removeItemLight(item);
        }
    }

    private int getLightLevel(ItemStack item) {
        if (item == null) return 0;
        String itemType = item.getType().toString();
        return plugin.getConfig().getInt("light-sources." + itemType, 0);
    }

    private boolean isSafeLightLocation(Location location) {
    Block block = location.getBlock();
    
    // Don't place if not air
    if (!block.getType().isAir()) {
        return false;
    }

    // Check adjacent blocks for interactables
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && y == 0 && z == 0) continue; // Skip center block
                
                Material adjacentType = block.getRelative(x, y, z).getType();
                if (isInteractableBlock(adjacentType)) {
                    return false;
                }
            }
        }
    }
    return true;
}

private boolean isInteractableBlock(Material material) {
    return material == Material.CHEST || 
           material == Material.TRAPPED_CHEST ||
           material == Material.ENDER_CHEST ||
           material == Material.BARREL ||
           material == Material.FURNACE ||
           material == Material.BLAST_FURNACE ||
           material == Material.SMOKER ||
           material == Material.DROPPER ||
           material == Material.DISPENSER ||
           material == Material.HOPPER ||
           material == Material.ANVIL ||
           material == Material.CHIPPED_ANVIL ||
           material == Material.DAMAGED_ANVIL ||
           material == Material.ENCHANTING_TABLE ||
           material == Material.BREWING_STAND ||
           material == Material.CRAFTING_TABLE;
}

    private void updatePlayerLight(Player player, Location location, int lightLevel) {
    Location currentLightLocation = playerLightLocations.get(player);

    // Remove previous light
    if (currentLightLocation != null) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Block block = currentLightLocation.getBlock();
            if (block.getType() == Material.LIGHT) {
                block.setType(Material.AIR);
            }
        });
    }

    Bukkit.getScheduler().runTask(plugin, () -> {
        // Player-specific light placement logic
        Location lightLocation = findOptimalPlayerLightLocation(player, location);
        
        if (lightLocation != null && isSafeLightLocation(lightLocation)) {
            Block block = lightLocation.getBlock();
            block.setType(Material.LIGHT);
            Levelled lightData = (Levelled) block.getBlockData();
            lightData.setLevel(lightLevel);
            block.setBlockData(lightData);
            playerLightLocations.put(player, lightLocation);
        } else {
            playerLightLocations.remove(player);
        }
    });
}

private Location findOptimalPlayerLightLocation(Player player, Location location) {
    // Try eye level first (for better visual effect)
    Location eyeLevel = location.clone().add(0, 1.6, 0);
    if (eyeLevel.getBlock().getType().isAir()) {
        return eyeLevel;
    }
    
    // Fallback to torso level
    Location torsoLevel = location.clone().add(0, 1.2, 0);
    if (torsoLevel.getBlock().getType().isAir()) {
        return torsoLevel;
    }
    
    return null;
}


    private void removePlayerLight(Player player) {
        Location lightLocation = playerLightLocations.remove(player);
        if (lightLocation != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                lightLocation.getBlock().setType(Material.AIR);
            });
        }
    }

    private void updateItemLight(Item item, int lightLevel) {
    Location itemLocation = item.getLocation();
    Location currentLightLocation = itemLightLocations.get(item);

    // Remove previous light
    if (currentLightLocation != null) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Block block = currentLightLocation.getBlock();
            if (block.getType() == Material.LIGHT) {
                block.setType(Material.AIR);
            }
        });
    }

    Bukkit.getScheduler().runTask(plugin, () -> {
        // Item-specific light placement logic
        Location lightLocation = findOptimalItemLightLocation(itemLocation);
        
        if (lightLocation != null && isSafeLightLocation(lightLocation)) {
            Block block = lightLocation.getBlock();
            block.setType(Material.LIGHT);
            Levelled lightData = (Levelled) block.getBlockData();
            lightData.setLevel(lightLevel);
            block.setBlockData(lightData);
            itemLightLocations.put(item, lightLocation);
        } else {
            itemLightLocations.remove(item);
        }
    });
}

private Location findOptimalItemLightLocation(Location itemLocation) {
    // Small vertical offset for natural appearance
    Location bestLocation = itemLocation.clone().add(0, 0.3, 0);
    
    // Check if item is moving fast (thrown)
    if (item.getVelocity().lengthSquared() > 0.1) {
        // For moving items, place light slightly higher to avoid flickering
        bestLocation.add(0, 0.2, 0);
    }
    
    return bestLocation;
}
    
    private void removeItemLight(Item item) {
    Location lightLocation = itemLightLocations.remove(item);
    if (lightLocation != null) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Smooth light fade-out
            Block block = lightLocation.getBlock();
            if (block.getType() == Material.LIGHT) {
                Levelled lightData = (Levelled)block.getBlockData();
                if (lightData.getLevel() > 5) {
                    lightData.setLevel(5);
                    block.setBlockData(lightData);
                    new BukkitRunnable() {
                        public void run() {
                            if (block.getType() == Material.LIGHT) {
                                block.setType(Material.AIR);
                            }
                        }
                    }.runTaskLater(plugin, 2L);
                } else {
                    block.setType(Material.AIR);
                }
            }
        });
    }
    }

    private void startLightUpdateTask() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Map.Entry<Player, Location> entry : playerLightLocations.entrySet()) {
                Player player = entry.getKey();
                Location currentLightLocation = entry.getValue();
                Location playerLocation = player.getLocation();

                if (!playerLocation.equals(currentLightLocation)) {
                    PlayerInventory inventory = player.getInventory();
                    ItemStack mainHandItem = inventory.getItemInMainHand();
                    ItemStack offHandItem = inventory.getItemInOffHand();

                    int lightLevel = Math.max(getLightLevel(mainHandItem), getLightLevel(offHandItem));

                    if (lightLevel > 0 && plugin.isDynamicLightEnabled(player)) {
                        updatePlayerLight(player, playerLocation, lightLevel);
                    }
                }
            }

            for (Map.Entry<Item, Location> entry : itemLightLocations.entrySet()) {
                Item item = entry.getKey();
                Location currentLightLocation = entry.getValue();
                Location itemLocation = item.getLocation();

                if (!itemLocation.equals(currentLightLocation)) {
                    updateItemLight(item, getLightLevel(item.getItemStack()));
                }
            }
        }, 0L, 5L);
    }
}
