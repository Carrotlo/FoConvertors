package me.foesio.foConvertors.api;

import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Supported FoConvertors integration service. Use Bukkit ServicesManager to discover it.
 * Calls involving an item, registry, or player must run on the server/player owning thread.
 * Reacquire the service after plugin disable/re-enable; do not retain mutable Bukkit values.
 */
public interface FoConvertorsApi {
    /** Recognizes FoConvertors metadata, even when its template has since been deleted. */
    Optional<ConvertorItemInfo> inspectItem(ItemStack item);

    /** Creates one item using the template's current default enabled preference. */
    Optional<ItemStack> createItem(String templateId);

    /** Creates one item with an explicit item-local enabled state. */
    Optional<ItemStack> createItem(String templateId, boolean enabled);

    /** IDs of the currently loaded templates, in sorted order. */
    List<String> templateIds();

    /** A detached snapshot of a currently loaded template. */
    Optional<ConvertorTemplateSnapshot> template(String templateId);

    /** Effective IDs from enabled, valid items in the player's permitted inventory slots. */
    List<String> activeTemplateIds(Player player);

    /** Queues a normal conversion pass. True means queued, not that a conversion occurred. */
    boolean requestConversion(Player player);
}
