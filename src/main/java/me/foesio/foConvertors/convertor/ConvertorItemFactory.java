package me.foesio.foConvertors.convertor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.foesio.core.item.FoItemStacks;
import me.foesio.core.message.FoMessageService;
import me.foesio.foConvertors.config.PluginSettings;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConvertorItemFactory {
    private final NamespacedKey convertorMarkerKey;
    private final NamespacedKey templateIdKey;
    private final NamespacedKey enabledKey;

    private final ConvertorRegistry registry;
    private final FoMessageService messages;
    private final PluginSettings settings;
    private String enabledStatusLore;
    private String disabledStatusLore;
    private String toggleLore;

    public ConvertorItemFactory(JavaPlugin plugin, ConvertorRegistry registry, FoMessageService messages, PluginSettings settings) {
        this.convertorMarkerKey = new NamespacedKey(plugin, "is_convertor");
        this.templateIdKey = new NamespacedKey(plugin, "convertor_template_id");
        this.enabledKey = new NamespacedKey(plugin, "convertor_enabled");
        this.registry = registry;
        this.messages = messages;
        this.settings = settings;
        reloadLoreTemplates();
    }

    public void reloadLoreTemplates() {
        enabledStatusLore = messages.render(
                "convertor-item.status-enabled",
                "{white}Status: {success}Enabled"
        );
        disabledStatusLore = messages.render(
                "convertor-item.status-disabled",
                "{white}Status: {bad}Disabled"
        );
        toggleLore = messages.render(
                "convertor-item.toggle",
                "{white}Toggle: {theme}{mode} {white}while holding.",
                Map.of("mode", settings.getToggleClickMode().getLabel())
        );
    }

    public ItemStack createConvertorItem(ConvertorTemplate template, boolean enabled) {
        ItemStack baseItem = template.getDisplayItem();
        if (baseItem.getType().isAir()) {
            baseItem = new ItemStack(Material.CHEST_MINECART);
        }

        ItemStack convertorItem = FoItemStacks.cloneItem(baseItem);
        convertorItem.setAmount(1);

        ItemMeta meta = convertorItem.getItemMeta();
        if (meta == null) {
            return convertorItem;
        }

        if (!meta.hasDisplayName()) {
            meta.setDisplayName(messages.renderTemplate("{theme}" + prettifyId(template.getId()), java.util.Collections.emptyMap()));
        }

        List<String> lore = new ArrayList<>();
        if (meta.hasLore()) {
            lore.addAll(meta.getLore());
        }

        lore.add(messages.renderTemplate("{white} ", java.util.Collections.emptyMap()));
        lore.add(enabled ? enabledStatusLore : disabledStatusLore);
        lore.add(toggleLore);

        meta.setLore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(convertorMarkerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(templateIdKey, PersistentDataType.STRING, template.getId().toLowerCase(Locale.ROOT));
        pdc.set(enabledKey, PersistentDataType.BYTE, enabled ? (byte) 1 : (byte) 0);

        convertorItem.setItemMeta(meta);
        return convertorItem;
    }

    public ItemStack rebuildWithState(ItemStack original, boolean enabled) {
        ConvertorItemData data = readConvertorData(original);
        if (data == null) {
            return original;
        }

        return rebuildWithState(original, data.templateId(), enabled);
    }

    public ItemStack rebuildWithState(ItemStack original, String templateId, boolean enabled) {
        if (templateId == null) {
            return original;
        }

        ConvertorTemplate template = registry.get(templateId);
        if (template == null) {
            return original;
        }

        ItemStack rebuilt = createConvertorItem(template, enabled);
        rebuilt.setAmount(original.getAmount());
        return rebuilt;
    }

    public boolean isConvertorItem(ItemStack stack) {
        return readConvertorData(stack) != null;
    }

    public ConvertorItemData readConvertorData(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }

        if (!stack.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(convertorMarkerKey, PersistentDataType.BYTE)) {
            return null;
        }

        String templateId = pdc.get(templateIdKey, PersistentDataType.STRING);
        Byte enabled = pdc.get(enabledKey, PersistentDataType.BYTE);
        return new ConvertorItemData(templateId, enabled != null && enabled == (byte) 1);
    }

    public String getTemplateId(ItemStack stack) {
        ConvertorItemData data = readConvertorData(stack);
        return data == null ? null : data.templateId();
    }

    public boolean isEnabled(ItemStack stack) {
        ConvertorItemData data = readConvertorData(stack);
        return data != null && data.enabled();
    }

    private static String prettifyId(String id) {
        String base = id.replace('_', ' ').replace('-', ' ');
        if (base.isEmpty()) {
            return id;
        }
        return Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }

    public record ConvertorItemData(String templateId, boolean enabled) {
    }
}
