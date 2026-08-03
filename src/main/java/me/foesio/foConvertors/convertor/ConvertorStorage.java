package me.foesio.foConvertors.convertor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import me.foesio.core.config.ResourceFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConvertorStorage {
    private final JavaPlugin plugin;
    private final File convertorFolder;

    public ConvertorStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.convertorFolder = ResourceFiles.dataFile(plugin, "convertors");
    }

    public void ensureFolder() {
        if (!convertorFolder.exists() && !convertorFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create convertors folder at " + convertorFolder.getAbsolutePath());
        }
    }

    public Map<String, ConvertorTemplate> loadAll() {
        ensureFolder();

        File[] files = convertorFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            return Collections.emptyMap();
        }

        Map<String, ConvertorTemplate> templates = new HashMap<>();
        for (File file : files) {
            try {
                YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);

                String id = configuration.getString("id");
                if (id == null || id.isBlank()) {
                    id = file.getName().replace(".yml", "").toLowerCase(Locale.ROOT);
                }

                ItemStack displayItem = safeGetItem(configuration, "display.item", file);
                boolean defaultEnabled = configuration.getBoolean("defaults.enabled", true);

                ConvertorTemplate template = new ConvertorTemplate(
                        id.toLowerCase(Locale.ROOT),
                        loadRecipes(configuration, file),
                        displayItem,
                        defaultEnabled
                );
                templates.put(template.getId(), template);
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger().log(Level.WARNING, "Skipping convertor template file " + file.getName() + " because it could not be loaded.", exception);
            }
        }

        return templates;
    }

    public void save(ConvertorTemplate template) {
        ensureFolder();

        File file = new File(convertorFolder, template.getId().toLowerCase() + ".yml");
        YamlConfiguration configuration = new YamlConfiguration();

        configuration.set("id", template.getId().toLowerCase());
        for (ConvertorRecipe recipe : template.getRecipes()) {
            String recipePath = "recipes." + recipe.getId().toLowerCase(Locale.ROOT);
            configuration.set(recipePath + ".input.item", recipe.getInputItem());
            configuration.set(recipePath + ".input.amount", recipe.getInputAmount());
            configuration.set(recipePath + ".output.item", recipe.getOutputItem());
            configuration.set(recipePath + ".output.amount", recipe.getOutputAmount());
        }
        configuration.set("display.item", template.getDisplayItem());
        configuration.set("defaults.enabled", template.isDefaultEnabled());

        try {
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save convertor template " + template.getId(), exception);
        }
    }

    public boolean delete(String id) {
        File file = new File(convertorFolder, id.toLowerCase() + ".yml");
        return !file.exists() || file.delete();
    }

    private List<ConvertorRecipe> loadRecipes(YamlConfiguration configuration, File file) {
        ConfigurationSection recipesSection = configuration.getConfigurationSection("recipes");
        if (recipesSection == null) {
            return List.of(loadLegacyRecipe(configuration, file));
        }

        List<ConvertorRecipe> recipes = new ArrayList<>();
        for (String recipeId : recipesSection.getKeys(false)) {
            String path = "recipes." + recipeId;
            recipes.add(new ConvertorRecipe(
                    recipeId.toLowerCase(Locale.ROOT),
                    safeGetItem(configuration, path + ".input.item", file),
                    configuration.getInt(path + ".input.amount", 1),
                    safeGetItem(configuration, path + ".output.item", file),
                    configuration.getInt(path + ".output.amount", 1)
            ));
        }

        if (recipes.isEmpty()) {
            recipes.add(loadLegacyRecipe(configuration, file));
        }
        return recipes;
    }

    private ConvertorRecipe loadLegacyRecipe(YamlConfiguration configuration, File file) {
        ItemStack inputItem = safeGetItem(configuration, "input.item", file);
        int inputAmount = Math.max(1, configuration.getInt("input.amount", 1));

        ItemStack outputItem = safeGetItem(configuration, "output.item", file);
        int outputAmount = Math.max(1, configuration.getInt("output.amount", 1));

        return new ConvertorRecipe("default", inputItem, inputAmount, outputItem, outputAmount);
    }

    private ItemStack safeGetItem(YamlConfiguration configuration, String path, File file) {
        try {
            return configuration.getItemStack(path);
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Could not load item at " + path + " in " + file.getName() + ": " + exception.getMessage() + ". Using fallback item.");
            return null;
        }
    }
}
