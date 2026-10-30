package me.foesio.foConvertors.api;

import org.bukkit.inventory.ItemStack;

/** One recipe/slot batch within a conversion pass. Items are detached copies. */
public record ConversionEntry(String templateId, String recipeId, int conversions,
                              ItemStack inputItem, int inputAmount,
                              ItemStack outputItem, int outputAmount) {
    public ConversionEntry {
        inputItem = inputItem.clone();
        outputItem = outputItem.clone();
    }

    @Override public ItemStack inputItem() { return inputItem.clone(); }
    @Override public ItemStack outputItem() { return outputItem.clone(); }
}
