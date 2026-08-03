package me.foesio.foConvertors.convertor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import me.foesio.core.item.FoItemStacks;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class ConvertorTemplate {
    private final String id;
    private final List<ConvertorRecipe> recipes = new ArrayList<>();
    private final List<ConvertorRecipe> recipesView = Collections.unmodifiableList(recipes);
    private ItemStack displayItem;
    private boolean defaultEnabled;

    public ConvertorTemplate(
            String id,
            List<ConvertorRecipe> recipes,
            ItemStack displayItem,
            boolean defaultEnabled
    ) {
        this.id = id.toLowerCase(Locale.ROOT);
        if (recipes != null) {
            for (ConvertorRecipe recipe : recipes) {
                if (recipe != null) {
                    this.recipes.add(recipe);
                }
            }
        }
        if (this.recipes.isEmpty()) {
            this.recipes.add(ConvertorRecipe.createDefault("default"));
        }
        this.displayItem = sanitizeItem(displayItem, Material.CHEST_MINECART);
        this.defaultEnabled = defaultEnabled;
    }

    public static ConvertorTemplate createDefault(String id) {
        return new ConvertorTemplate(
                id,
                List.of(ConvertorRecipe.createDefault("default")),
                new ItemStack(Material.CHEST_MINECART),
                true
        );
    }

    public String getId() {
        return id;
    }

    public List<ConvertorRecipe> getRecipes() {
        return recipesView;
    }

    public ConvertorRecipe getRecipe(String recipeId) {
        if (recipeId == null) {
            return null;
        }

        String normalized = recipeId.toLowerCase(Locale.ROOT);
        for (ConvertorRecipe recipe : recipes) {
            if (recipe.getId().equalsIgnoreCase(normalized)) {
                return recipe;
            }
        }
        return null;
    }

    public void addRecipe(ConvertorRecipe recipe) {
        if (recipe != null && getRecipe(recipe.getId()) == null) {
            recipes.add(recipe);
        }
    }

    public boolean removeRecipe(String recipeId) {
        if (recipes.size() <= 1 || recipeId == null) {
            return false;
        }
        return recipes.removeIf(recipe -> recipe.getId().equalsIgnoreCase(recipeId));
    }

    public String nextRecipeId() {
        int index = recipes.size() + 1;
        String candidate = "recipe-" + index;
        while (getRecipe(candidate) != null) {
            index++;
            candidate = "recipe-" + index;
        }
        return candidate;
    }

    public ItemStack getInputItem() {
        return firstRecipe().getInputItem();
    }

    public void setInputItem(ItemStack inputItem) {
        firstRecipe().setInputItem(inputItem);
    }

    public int getInputAmount() {
        return firstRecipe().getInputAmount();
    }

    public void setInputAmount(int inputAmount) {
        firstRecipe().setInputAmount(inputAmount);
    }

    public ItemStack getOutputItem() {
        return firstRecipe().getOutputItem();
    }

    public void setOutputItem(ItemStack outputItem) {
        firstRecipe().setOutputItem(outputItem);
    }

    public int getOutputAmount() {
        return firstRecipe().getOutputAmount();
    }

    public void setOutputAmount(int outputAmount) {
        firstRecipe().setOutputAmount(outputAmount);
    }

    public ItemStack getDisplayItem() {
        return FoItemStacks.cloneItem(displayItem);
    }

    public void setDisplayItem(ItemStack displayItem) {
        this.displayItem = sanitizeItem(displayItem, Material.CHEST_MINECART);
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public void setDefaultEnabled(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    private ConvertorRecipe firstRecipe() {
        return recipes.get(0);
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
