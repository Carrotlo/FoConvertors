package me.foesio.foConvertors.event;

import me.foesio.foConvertors.api.ConvertorItemInfo;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Fired after FoConvertors replaces a held item with its opposite enabled state. */
public final class ConvertorItemToggledEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final EquipmentSlot hand;
    private final ConvertorItemInfo item;
    private final boolean previousEnabled;
    private final ItemStack newItem;

    public ConvertorItemToggledEvent(Player player, EquipmentSlot hand, ConvertorItemInfo item,
            boolean previousEnabled, ItemStack newItem) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.hand = hand;
        this.item = item;
        this.previousEnabled = previousEnabled;
        this.newItem = newItem.clone();
    }

    public Player getPlayer() { return player; }
    public EquipmentSlot getHand() { return hand; }
    public ConvertorItemInfo getItemInfo() { return item; }
    public boolean wasEnabled() { return previousEnabled; }
    public ItemStack getNewItem() { return newItem.clone(); }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
