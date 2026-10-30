package me.foesio.foConvertors.internal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import me.foesio.foConvertors.api.ConvertorItemInfo;
import me.foesio.foConvertors.api.ConvertorTemplateSnapshot;
import me.foesio.foConvertors.api.FoConvertorsApi;
import me.foesio.foConvertors.convertor.ConvertorItemFactory;
import me.foesio.foConvertors.convertor.ConvertorItemFactory.ConvertorItemData;
import me.foesio.foConvertors.convertor.ConvertorRegistry;
import me.foesio.foConvertors.config.PluginSettings;
import me.foesio.foConvertors.listener.AutoConvertListener;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Service implementation; consumers should depend on {@link FoConvertorsApi}. */
public final class DefaultFoConvertorsApi implements FoConvertorsApi {
    private final JavaPlugin plugin;
    private final ConvertorRegistry registry;
    private final ConvertorItemFactory items;
    private final PluginSettings settings;
    private final AutoConvertListener processing;

    public DefaultFoConvertorsApi(JavaPlugin plugin, ConvertorRegistry registry,
            ConvertorItemFactory items, PluginSettings settings, AutoConvertListener processing) {
        this.plugin = plugin;
        this.registry = registry;
        this.items = items;
        this.settings = settings;
        this.processing = processing;
    }

    @Override
    public Optional<ConvertorItemInfo> inspectItem(ItemStack item) {
        if (!plugin.isEnabled()) return Optional.empty();
        ConvertorItemData data = items.readConvertorData(item);
        if (data == null) return Optional.empty();
        String normalizedId = data.templateId() == null ? null : data.templateId().toLowerCase(java.util.Locale.ROOT);
        return Optional.of(new ConvertorItemInfo(data.templateId(), data.enabled(),
                normalizedId != null && registry.publicSnapshots().containsKey(normalizedId)));
    }

    @Override
    public Optional<ItemStack> createItem(String templateId) {
        if (!plugin.isEnabled()) return Optional.empty();
        return template(templateId).map(snapshot -> items.createConvertorItem(
                snapshot.id(), snapshot.displayItem(), snapshot.defaultEnabled()));
    }

    @Override
    public Optional<ItemStack> createItem(String templateId, boolean enabled) {
        if (!plugin.isEnabled()) return Optional.empty();
        return template(templateId).map(snapshot -> items.createConvertorItem(
                snapshot.id(), snapshot.displayItem(), enabled));
    }

    @Override
    public List<String> templateIds() {
        if (!plugin.isEnabled()) return List.of();
        return registry.publicSnapshots().keySet().stream().sorted().toList();
    }

    @Override
    public Optional<ConvertorTemplateSnapshot> template(String templateId) {
        if (!plugin.isEnabled() || templateId == null) return Optional.empty();
        return Optional.ofNullable(registry.publicSnapshots().get(templateId.toLowerCase(java.util.Locale.ROOT)));
    }

    @Override
    public List<String> activeTemplateIds(Player player) {
        if (!plugin.isEnabled() || player == null || !player.isOnline()) return List.of();
        Map<String, ConvertorTemplateSnapshot> available = registry.publicSnapshots();
        Set<String> active = new LinkedHashSet<>();
        if (settings.isRequireOffhandConvertor()) {
            collect(player.getInventory().getItemInOffHand(), available, active);
        } else {
            for (ItemStack stack : player.getInventory().getContents()) collect(stack, available, active);
            collect(player.getInventory().getItemInOffHand(), available, active);
        }
        return List.copyOf(active);
    }

    private void collect(ItemStack stack, Map<String, ConvertorTemplateSnapshot> available, Set<String> active) {
        ConvertorItemData data = items.readConvertorData(stack);
        String id = data == null || data.templateId() == null ? null : data.templateId().toLowerCase(java.util.Locale.ROOT);
        if (data != null && data.enabled() && id != null && available.containsKey(id)) {
            active.add(id);
        }
    }

    @Override
    public boolean requestConversion(Player player) {
        return plugin.isEnabled() && processing.requestProcess(player);
    }
}
