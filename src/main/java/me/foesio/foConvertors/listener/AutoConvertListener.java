package me.foesio.foConvertors.listener;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import me.foesio.core.FoCoreContext;
import me.foesio.foConvertors.convertor.ConversionEngine;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public final class AutoConvertListener implements Listener {
    private final Supplier<FoCoreContext> coreSupplier;
    private final ConversionEngine engine;

    private final Set<UUID> inProgress = ConcurrentHashMap.newKeySet();
    private final Set<UUID> queued = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastRunMillis = new ConcurrentHashMap<>();

    public AutoConvertListener(Supplier<FoCoreContext> coreSupplier, ConversionEngine engine) {
        this.coreSupplier = coreSupplier;
        this.engine = engine;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        coreContext().scheduler().runLaterForPlayer(player, () -> requestProcess(player), 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            requestProcess(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            requestProcess(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            requestProcess(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        requestProcess(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        requestProcess(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        requestProcess(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        inProgress.remove(uuid);
        queued.remove(uuid);
        lastRunMillis.remove(uuid);
        engine.forgetPlayer(uuid);
    }

    public void requestProcess(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!queued.add(uuid)) {
            return;
        }

        coreContext().scheduler().runForPlayer(player, () -> {
            try {
                process(player);
            } finally {
                queued.remove(uuid);
            }
        });
    }

    private FoCoreContext coreContext() {
        FoCoreContext core = coreSupplier.get();
        if (core == null) {
            throw new IllegalStateException("FoPluginCore context is not initialized.");
        }
        return core;
    }

    private void process(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();
        long last = lastRunMillis.getOrDefault(uuid, 0L);
        if (now - last < 35L) {
            return;
        }

        if (!inProgress.add(uuid)) {
            return;
        }

        try {
            engine.processPlayer(player);
            lastRunMillis.put(uuid, now);
        } finally {
            inProgress.remove(uuid);
        }
    }
}
