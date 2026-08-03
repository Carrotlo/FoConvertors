package me.foesio.foConvertors.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.foesio.core.message.FoMessageService;
import me.foesio.foConvertors.config.PluginSettings;
import me.foesio.foConvertors.convertor.ConvertorItemFactory;
import me.foesio.foConvertors.convertor.ConvertorItemFactory.ConvertorItemData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class ConvertorToggleListener implements Listener {
    private final ConvertorItemFactory itemFactory;
    private final PluginSettings settings;
    private final FoMessageService messages;
    private final AutoConvertListener autoConvertListener;
    private final Map<UUID, Long> lastToggleMillis = new ConcurrentHashMap<>();

    public ConvertorToggleListener(
            ConvertorItemFactory itemFactory,
            PluginSettings settings,
            FoMessageService messages,
            AutoConvertListener autoConvertListener
    ) {
        this.itemFactory = itemFactory;
        this.settings = settings;
        this.messages = messages;
        this.autoConvertListener = autoConvertListener;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (isLeftClick(action) && !settings.getToggleClickMode().allowsLeftClick()) {
            return;
        }

        if (isRightClick(action) && !settings.getToggleClickMode().allowsRightClick()) {
            return;
        }

        if (!isLeftClick(action) && !isRightClick(action)) {
            return;
        }

        if (toggle(event.getPlayer(), event.getHand(), event.getItem(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING || !settings.getToggleClickMode().allowsLeftClick()) {
            return;
        }

        Player player = event.getPlayer();
        Long lastToggle = lastToggleMillis.get(player.getUniqueId());
        if (lastToggle != null && System.currentTimeMillis() - lastToggle < 50L) {
            return;
        }

        if (toggle(player, EquipmentSlot.HAND, player.getInventory().getItemInMainHand(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastToggleMillis.remove(event.getPlayer().getUniqueId());
    }

    private boolean toggle(Player player, EquipmentSlot hand, ItemStack item, boolean sendCooldown) {
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) {
            return false;
        }

        ConvertorItemData data = itemFactory.readConvertorData(item);
        if (data == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMs = settings.getToggleCooldownMs();
        long lastToggle = lastToggleMillis.getOrDefault(uuid, 0L);
        if (cooldownMs > 0L && now - lastToggle < cooldownMs) {
            if (sendCooldown && settings.isSendToggleMessage()) {
                long remaining = Math.max(1L, cooldownMs - (now - lastToggle));
                messages.send(player, "toggle.cooldown", Map.of("remaining", String.valueOf(remaining)));
            }
            return true;
        }

        boolean nowEnabled = !data.enabled();
        ItemStack rebuilt = itemFactory.rebuildWithState(item, data.templateId(), nowEnabled);
        lastToggleMillis.put(uuid, now);

        if (hand == EquipmentSlot.HAND) {
            player.getInventory().setItemInMainHand(rebuilt);
        } else {
            player.getInventory().setItemInOffHand(rebuilt);
        }

        if (settings.isSendToggleMessage()) {
            messages.send(
                    player,
                    nowEnabled ? "toggle.enabled" : "toggle.disabled",
                    Map.of("id", data.templateId() == null ? "unknown" : data.templateId())
            );
        }

        if (nowEnabled) {
            autoConvertListener.requestProcess(player);
        }

        return true;
    }

    private static boolean isLeftClick(Action action) {
        return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    }

    private static boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }
}
