package me.foesio.foConvertors.api;

import org.bukkit.inventory.ItemStack;

/** A detached view of one configured recipe. */
public record ConvertorRecipeSnapshot(String id, ItemStack inputItem, int inputAmount,
                                     ItemStack outputItem, int outputAmount) {
    public ConvertorRecipeSnapshot {
        inputItem = inputItem.clone();
        outputItem = outputItem.clone();
    }

    @Override public ItemStack inputItem() { return inputItem.clone(); }
    @Override public ItemStack outputItem() { return outputItem.clone(); }
}
