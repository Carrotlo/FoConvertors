package me.foesio.foConvertors.api;

/** Item-local state. An enabled item is not necessarily active for a player. */
public record ConvertorItemInfo(String templateId, boolean enabled, boolean templateAvailable) {
}
