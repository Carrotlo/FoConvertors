package me.foesio.foConvertors.convertor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConvertorRegistry {
    private final ConvertorStorage storage;
    private final Map<String, ConvertorTemplate> templates = new LinkedHashMap<>();

    public ConvertorRegistry(ConvertorStorage storage) {
        this.storage = storage;
    }

    public void load() {
        templates.clear();
        Map<String, ConvertorTemplate> loaded = storage.loadAll();
        List<ConvertorTemplate> sorted = new ArrayList<>(loaded.values());
        sorted.sort(Comparator.comparing(ConvertorTemplate::getId));
        for (ConvertorTemplate template : sorted) {
            templates.put(template.getId().toLowerCase(Locale.ROOT), template);
        }
    }

    public void save(ConvertorTemplate template) {
        String id = template.getId().toLowerCase(Locale.ROOT);
        templates.put(id, template);
        storage.save(template);
    }

    public boolean delete(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        templates.remove(normalized);
        return storage.delete(normalized);
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

    public void reload() {
        load();
    }
}
