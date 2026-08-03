package me.foesio.foConvertors.convertor;

import java.util.ArrayList;
import java.util.List;
import me.foesio.core.item.FoItemStacks;
import org.bukkit.inventory.ItemStack;

public final class ItemStackBatches {

    private ItemStackBatches() {
    }

    public static List<ItemStack> split(ItemStack template, int amount) {
        if (template == null || template.getType().isAir() || amount <= 0) {
            return List.of();
        }

        int remaining = amount;
        int maxStackSize = Math.max(1, template.getMaxStackSize());
        int expectedStacks = (int) Math.min(Integer.MAX_VALUE, ((long) amount + maxStackSize - 1L) / maxStackSize);
        List<ItemStack> stacks = new ArrayList<>(expectedStacks);
        while (remaining > 0) {
            int chunk = Math.min(maxStackSize, remaining);
            ItemStack item = FoItemStacks.cloneItem(template);
            item.setAmount(chunk);
            stacks.add(item);
            remaining -= chunk;
        }
        return List.copyOf(stacks);
    }
}
