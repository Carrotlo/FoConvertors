package me.foesio.foConvertors.event;

import java.util.List;
import me.foesio.foConvertors.api.ConversionEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * One completed inventory pass, after inputs were removed and outputs deposited or dropped.
 * Entries report produced amounts, not items currently held. Never fires for no-op passes.
 * Runs on the player's owning server execution context.
 */
public final class ConvertorConversionCompletedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final List<ConversionEntry> entries;

    public ConvertorConversionCompletedEvent(Player player, List<ConversionEntry> entries) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.entries = List.copyOf(entries);
    }

    public Player getPlayer() { return player; }
    public List<ConversionEntry> getEntries() { return entries; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
