package me.foesio.foConvertors.event;

import java.util.List;
import me.foesio.foConvertors.api.ConversionEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * One proposed player inventory pass, fired before any input is removed.
 * Cancellation rejects the entire pass. Entries cannot be edited.
 * Runs on the player's owning server execution context.
 */
public final class ConvertorConversionAttemptEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final List<ConversionEntry> entries;
    private boolean cancelled;

    public ConvertorConversionAttemptEvent(Player player, List<ConversionEntry> entries) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.entries = List.copyOf(entries);
    }

    public Player getPlayer() { return player; }
    public List<ConversionEntry> getEntries() { return entries; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
