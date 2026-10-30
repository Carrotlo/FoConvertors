package me.foesio.foConvertors.convertor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.foesio.foConvertors.api.ConvertorRecipeSnapshot;
import me.foesio.foConvertors.api.ConvertorTemplateSnapshot;

public final class ConvertorRegistry {
    private final ConvertorStorage storage;
    private volatile Map<String, ConvertorTemplate> templates = Map.of();
    private volatile Map<String, ConvertorTemplateSnapshot> publicSnapshots = Map.of();

    public ConvertorRegistry(ConvertorStorage storage) {
        this.storage = storage;
    }

    public synchronized void load() {
        Map<String, ConvertorTemplate> loaded = storage.loadAll();
        List<ConvertorTemplate> sorted = new ArrayList<>(loaded.values());
        sorted.sort(Comparator.comparing(ConvertorTemplate::getId));
        Map<String, ConvertorTemplate> next = new LinkedHashMap<>();
        for (ConvertorTemplate template : sorted) {
            next.put(template.getId().toLowerCase(Locale.ROOT), template);
        }
        templates = Collections.unmodifiableMap(next);
        refreshPublicSnapshots(next);
    }

    public synchronized void save(ConvertorTemplate template) {
        String id = template.getId().toLowerCase(Locale.ROOT);
        Map<String, ConvertorTemplate> next = new LinkedHashMap<>(templates);
        next.put(id, template);
        templates = Collections.unmodifiableMap(next);
        storage.save(template);
        Map<String, ConvertorTemplateSnapshot> nextSnapshots = new LinkedHashMap<>(publicSnapshots);
        nextSnapshots.put(id, snapshot(template));
        publicSnapshots = Collections.unmodifiableMap(nextSnapshots);
    }

    public synchronized boolean delete(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        Map<String, ConvertorTemplate> next = new LinkedHashMap<>(templates);
        next.remove(normalized);
        templates = Collections.unmodifiableMap(next);
        boolean deleted = storage.delete(normalized);
        Map<String, ConvertorTemplateSnapshot> nextSnapshots = new LinkedHashMap<>(publicSnapshots);
        nextSnapshots.remove(normalized);
        publicSnapshots = Collections.unmodifiableMap(nextSnapshots);
        return deleted;
    }

    public ConvertorTemplate get(String id) {
        if (id == null) {
            return null;
        }

        return templates.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<ConvertorTemplate> all() {
        return new ArrayList<>(templates.values());
    }

    public List<String> ids() {
        return new ArrayList<>(templates.keySet());
    }

    public Map<String, ConvertorTemplateSnapshot> publicSnapshots() {
        return publicSnapshots;
    }

    private void refreshPublicSnapshots(Map<String, ConvertorTemplate> templates) {
        Map<String, ConvertorTemplateSnapshot> snapshots = new LinkedHashMap<>();
        for (ConvertorTemplate template : templates.values()) {
            snapshots.put(template.getId(), snapshot(template));
        }
        publicSnapshots = Collections.unmodifiableMap(snapshots);
    }

    private static ConvertorTemplateSnapshot snapshot(ConvertorTemplate template) {
        List<ConvertorRecipeSnapshot> recipes = template.getRecipes().stream()
                .map(recipe -> new ConvertorRecipeSnapshot(recipe.getId(), recipe.getInputItem(),
                        recipe.getInputAmount(), recipe.getOutputItem(), recipe.getOutputAmount()))
                .toList();
        return new ConvertorTemplateSnapshot(template.getId(), template.getDisplayItem(),
                template.isDefaultEnabled(), recipes);
    }

    public void reload() {
        load();
    }
}
