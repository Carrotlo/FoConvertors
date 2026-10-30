package me.foesio.foConvertors.api;

import java.util.List;
import org.bukkit.inventory.ItemStack;

/** A detached view of a configured template, including its minting default. */
public record ConvertorTemplateSnapshot(String id, ItemStack displayItem, boolean defaultEnabled,
                                       List<ConvertorRecipeSnapshot> recipes) {
    public ConvertorTemplateSnapshot {
        displayItem = displayItem.clone();
        recipes = List.copyOf(recipes);
    }

    @Override public ItemStack displayItem() { return displayItem.clone(); }
}
