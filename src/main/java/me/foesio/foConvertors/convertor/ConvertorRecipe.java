package me.foesio.foConvertors.convertor;

import me.foesio.core.item.FoItemStacks;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class ConvertorRecipe {
    private final String id;
    private ItemStack inputItem;
    private int inputAmount;
    private ItemStack outputItem;
    private int outputAmount;

    public ConvertorRecipe(
            String id,
            ItemStack inputItem,
            int inputAmount,
            ItemStack outputItem,
            int outputAmount
    ) {
        this.id = id;
        this.inputItem = sanitizeItem(inputItem, Material.COBBLESTONE);
        this.inputAmount = Math.max(1, inputAmount);
        this.outputItem = sanitizeItem(outputItem, Material.STONE);
        this.outputAmount = Math.max(1, outputAmount);
    }

    public static ConvertorRecipe createDefault(String id) {
        return new ConvertorRecipe(
                id,
                new ItemStack(Material.COBBLESTONE),
                1,
                new ItemStack(Material.STONE),
                1
        );
    }

    public String getId() {
        return id;
    }

    public ItemStack getInputItem() {
        return FoItemStacks.cloneItem(inputItem);
    }

    public void setInputItem(ItemStack inputItem) {
        this.inputItem = sanitizeItem(inputItem, Material.COBBLESTONE);
    }

    public int getInputAmount() {
        return inputAmount;
    }

    public void setInputAmount(int inputAmount) {
        this.inputAmount = Math.max(1, inputAmount);
    }

    public ItemStack getOutputItem() {
        return FoItemStacks.cloneItem(outputItem);
    }

    public void setOutputItem(ItemStack outputItem) {
        this.outputItem = sanitizeItem(outputItem, Material.STONE);
    }

    public int getOutputAmount() {
        return outputAmount;
    }

    public void setOutputAmount(int outputAmount) {
        this.outputAmount = Math.max(1, outputAmount);
    }

    private static ItemStack sanitizeItem(ItemStack itemStack, Material fallback) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return new ItemStack(fallback);
        }

        ItemStack clone = FoItemStacks.cloneItem(itemStack);
        clone.setAmount(1);
        return clone;
    }
}
