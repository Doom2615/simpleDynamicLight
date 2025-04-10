package com.github.K4RUNIO.simpleDynamicLight;

import com.github.K4RUNIO.simpleDynamicLight.utils.LightUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.*;

public class PlayerListener implements Listener {
    private final SimpleDynamicLight plugin;
    private final Map<UUID, Location> playerLights = new ConcurrentHashMap<>();
    private final Map<UUID, Location> itemLights = new ConcurrentHashMap<>();
    private final Set<UUID> pendingUpdates = ConcurrentHashMap.newKeySet();
    private int taskId = -1;

    public PlayerListener(SimpleDynamicLight plugin) {
        this.plugin = plugin;
        startUpdateTask();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!hasMovedSignificantly(event.getFrom(), event.getTo())) return;
        schedulePlayerUpdate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        schedulePlayerUpdate(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removePlayerLight(event.getPlayer());
        pendingUpdates.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        int lightLevel = getLightLevel(item.getItemStack());
        if (lightLevel > 0) updateItemLight(item, lightLevel);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (itemLights.containsKey(item.getUniqueId())) {
            removeItemLight(item);
            if (event.getEntity() instanceof Player player) {
                schedulePlayerUpdate(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        removeItemLight(event.getEntity());
    }

    public void schedulePlayerUpdate(Player player) {
        if (player != null && plugin.isDynamicLightEnabled(player)) {
            pendingUpdates.add(player.getUniqueId());
        }
    }

    private int getLightLevel(ItemStack item) {
        return item == null ? 0 : plugin.getConfig().getInt("light-sources." + item.getType().name(), 0);
    }

    private int getMaxLightLevel(Player player) {
        PlayerInventory inv = player.getInventory();
        return Math.max(
            getLightLevel(inv.getItemInMainHand()),
            getLightLevel(inv.getItemInOffHand())
        );
    }

    private void updatePlayerLight(Player player, Location location, int lightLevel) {
        UUID playerId = player.getUniqueId();
        Location oldLocation = playerLights.get(playerId);

        if (oldLocation != null) {
            LightUtils.removeLight(oldLocation);
        }

        Location lightLoc = findOptimalLightLocation(location);
        if (lightLoc != null) {
            LightUtils.placeLight(lightLoc, lightLevel);
            playerLights.put(playerId, lightLoc);
        }
    }

    private void removePlayerLight(Player player) {
        Location loc = playerLights.remove(player.getUniqueId());
        LightUtils.removeLight(loc);
    }

    private void updateItemLight(Item item, int lightLevel) {
        UUID itemId = item.getUniqueId();
        Location oldLoc = itemLights.get(itemId);

        if (oldLoc != null) {
            LightUtils.removeLight(oldLoc);
        }

        Location lightLoc = item.getLocation().clone().add(0, 1, 0);
        if (LightUtils.isSafeLocation(lightLoc.getBlock(), item.getLocation())) {
            LightUtils.placeLight(lightLoc, lightLevel);
            itemLights.put(itemId, lightLoc);
        }
    }

    private void removeItemLight(Item item) {
        Location loc = itemLights.remove(item.getUniqueId());
        LightUtils.removeLight(loc);
    }

    private Location findOptimalLightLocation(Location origin) {
        Location[] offsets = {
            origin.clone().add(1, 2, 0),   // Right
            origin.clone().add(-1, 2, 0),  // Left
            origin.clone().add(0, 2, 1),   // Front
            origin.clone().add(0, 2, -1),  // Back
            origin.clone().add(0, 3, 0)    // Above
        };

        for (Location loc : offsets) {
            Block block = loc.getBlock();
            if (LightUtils.isSafeLocation(block, origin)) {
                return loc;
            }
        }
        return null;
    }

    private boolean hasMovedSignificantly(Location from, Location to) {
        return to != null && from != null && (
            from.getWorld() != to.getWorld() ||
            from.getBlockX() != to.getBlockX() ||
            from.getBlockY() != to.getBlockY() ||
            from.getBlockZ() != to.getBlockZ()
        );
    }

    private void startUpdateTask() {
        taskId = new BukkitRunnable() {
            @Override
            public void run() {
                // Process player updates
                for (UUID playerId : pendingUpdates) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null || !plugin.isDynamicLightEnabled(player)) {
                        pendingUpdates.remove(playerId);
                        continue;
                    }

                    int lightLevel = getMaxLightLevel(player);
                    if (lightLevel > 0) {
                        updatePlayerLight(player, player.getLocation(), lightLevel);
                    } else {
                        removePlayerLight(player);
                    }
                }
                pendingUpdates.clear();

                // Process item updates
                Iterator<Map.Entry<UUID, Location>> it = itemLights.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, Location> entry = it.next();
                    Entity entity = Bukkit.getEntity(entry.getKey());
                    
                    if (!(entity instanceof Item item) || !item.isValid()) {
                        LightUtils.removeLight(entry.getValue());
                        it.remove();
                        continue;
                    }

                    if (!item.getLocation().equals(entry.getValue())) {
                        updateItemLight(item, getLightLevel(item.getItemStack()));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 10L).getTaskId(); // Runs every 10 ticks (0.5 seconds)
    }

    public void disable() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        
        // Clean up all lights
        playerLights.values().forEach(LightUtils::removeLight);
        itemLights.values().forEach(LightUtils::removeLight);
        playerLights.clear();
        itemLights.clear();
        pendingUpdates.clear();
    }
            }
