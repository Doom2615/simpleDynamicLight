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
    private final Map<UUID, Location> playerLightLocations = new HashMap<>();
    private final Map<UUID, Location> itemLightLocations = new HashMap<>();

    public PlayerListener(SimpleDynamicLight plugin) {
        this.plugin = plugin;
        startLightUpdateTask();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();

        if (to == null || from == null || to.getBlock().equals(from.getBlock())) return;

        if (!plugin.isDynamicLightEnabled(player)) {
            removePlayerLight(player);
            return;
        }

        int lightLevel = getMaxLightLevel(player);
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

        int lightLevel = getMaxLightLevel(player);
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
        if (!plugin.isDynamicLightEnabled(player)) return;

        Item droppedItem = event.getItemDrop();
        int lightLevel = getLightLevel(droppedItem.getItemStack());

        if (lightLevel > 0) {
            removePlayerLight(player);
            updateItemLight(droppedItem, lightLevel);
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        int lightLevel = getLightLevel(item.getItemStack());
        if (lightLevel > 0) updateItemLight(item, lightLevel);
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        Item item = event.getItem();
        UUID itemId = item.getUniqueId();
        if (itemLightLocations.containsKey(itemId)) {
            removeItemLight(item);

            if (event.getEntity() instanceof Player player && plugin.isDynamicLightEnabled(player)) {
                int lightLevel = getLightLevel(item.getItemStack());
                if (lightLevel > 0) {
                    updatePlayerLight(player, player.getLocation(), lightLevel);
                }
            }
        }
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event) {
        removeItemLight(event.getEntity());
    }

    private int getLightLevel(ItemStack item) {
        if (item == null) return 0;
        return plugin.getConfig().getInt("light-sources." + item.getType(), 0);
    }

    private int getMaxLightLevel(Player player) {
        PlayerInventory inv = player.getInventory();
        return Math.max(getLightLevel(inv.getItemInMainHand()), getLightLevel(inv.getItemInOffHand()));
    }

    private boolean isSafeToPlaceLight(Block block, Location playerLoc) {
    // Only place in air blocks
    if (!block.getType().isAir()) {
        return false;
    }

    // Check if this position would block a chest the player is interacting with
    if (couldBlockChestInteraction(block, playerLoc)) {
        return false;
    }

    // Check blocks below in a 3x3 area
    for (int x = -1; x <= 1; x++) {
        for (int z = -1; z <= 1; z++) {
            Block below = block.getRelative(x, -1, z);
            if (isChestLike(below.getType())) {
                return false;
            }
        }
    }

    return true;
}

private boolean couldBlockChestInteraction(Block lightBlock, Location playerLoc) {
    // Check if player is looking at a chest nearby
    Block targetBlock = playerLoc.getBlock().getRelative(
        playerLoc.getDirection().getBlockX(),
        playerLoc.getDirection().getBlockY(),
        playerLoc.getDirection().getBlockZ()
    );

    if (isChestLike(targetBlock.getType())) {
        // Don't place light if it would be between player and chest
        return lightBlock.getLocation().distanceSquared(targetBlock.getLocation()) < 4;
    }

    return false;
}

private boolean isChestLike(Material material) {
    return switch (material) {
        case CHEST, TRAPPED_CHEST, ENDER_CHEST, BARREL, SHULKER_BOX -> true;
        default -> false;
    };
}

    private Location findSafeLightLocation(Location origin) {
    Location[] offsets = {
        origin.clone().add(1, 2, 0),
        origin.clone().add(-1, 2, 0),
        origin.clone().add(0, 2, 1),
        origin.clone().add(0, 2, -1),
        origin.clone().add(0, 3, 0),
        origin.clone().add(1, 1, 0),
        origin.clone().add(-1, 1, 0),
        origin.clone().add(0, 1, 1),
        origin.clone().add(0, 1, -1)
    };

    for (Location loc : offsets) {
        Block block = loc.getBlock();
        if (isSafeToPlaceLight(block, origin)) {  // Now passing both parameters
            return loc;
        }
    }
    return null;
    }

    private void updatePlayerLight(Player player, Location location, int lightLevel) {
    UUID playerId = player.getUniqueId();
    Location oldLocation = playerLightLocations.get(playerId);

    if (oldLocation != null) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            oldLocation.getBlock().setType(Material.AIR);
        });
    }

    Bukkit.getScheduler().runTask(plugin, () -> {
        Location lightLoc = findSafeLightLocation(location);
        if (lightLoc == null) return;

        Block block = lightLoc.getBlock();
        block.setType(Material.LIGHT);

        if (block.getBlockData() instanceof Levelled lightData) {
            lightData.setLevel(lightLevel);
            block.setBlockData(lightData);
        }

        playerLightLocations.put(playerId, lightLoc);
    });
    }

    private void removePlayerLight(Player player) {
        UUID id = player.getUniqueId();
        Location loc = playerLightLocations.remove(id);
        if (loc != null) {
            Bukkit.getScheduler().runTask(plugin, () -> loc.getBlock().setType(Material.AIR));
        }
    }

    private void updateItemLight(Item item, int lightLevel) {
    UUID itemId = item.getUniqueId();
    Location oldLoc = itemLightLocations.get(itemId);

    if (oldLoc != null) {
        Bukkit.getScheduler().runTask(plugin, () -> oldLoc.getBlock().setType(Material.AIR));
    }

    Bukkit.getScheduler().runTask(plugin, () -> {
        Location lightLoc = item.getLocation().clone().add(0, 1, 0);
        Block block = lightLoc.getBlock();
        if (!isSafeToPlaceLight(block, item.getLocation())) return;  // Updated call

        block.setType(Material.LIGHT);
        if (block.getBlockData() instanceof Levelled lightData) {
            lightData.setLevel(lightLevel);
            block.setBlockData(lightData);
        }

        itemLightLocations.put(itemId, lightLoc);
    });
    }
    
    private void removeItemLight(Item item) {
        UUID itemId = item.getUniqueId();
        Location loc = itemLightLocations.remove(itemId);
        if (loc != null) {
            Bukkit.getScheduler().runTask(plugin, () -> loc.getBlock().setType(Material.AIR));
        }
    }

    private void startLightUpdateTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Map<UUID, Location> playerLights = new HashMap<>(playerLightLocations);
            for (UUID uuid : playerLights.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !plugin.isDynamicLightEnabled(player)) continue;

                Location current = player.getLocation();
                Location prev = playerLights.get(uuid);
                if (!current.equals(prev)) {
                    int lightLevel = getMaxLightLevel(player);
                    if (lightLevel > 0) updatePlayerLight(player, current, lightLevel);
                }
            }

            Map<UUID, Location> itemLights = new HashMap<>(itemLightLocations);
            for (UUID uuid : itemLights.keySet()) {
                Entity entity = Bukkit.getEntity(uuid);
                if (entity instanceof Item item) {
                    Location current = item.getLocation();
                    Location prev = itemLights.get(uuid);
                    if (!current.equals(prev)) {
                        updateItemLight(item, getLightLevel(item.getItemStack()));
                    }
                }
            }
        }, 0L, 5L);
    }
}
