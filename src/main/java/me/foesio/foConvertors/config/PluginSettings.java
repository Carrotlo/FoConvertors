package me.foesio.foConvertors.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginSettings {
    private final JavaPlugin plugin;

    private volatile boolean sendToggleMessage;
    private volatile boolean requireOffhandConvertor;
    private volatile int maxConversionsPerPass;
    private volatile long toggleCooldownMs;
    private volatile ToggleClickMode toggleClickMode;

    public PluginSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        FileConfiguration config = plugin.getConfig();
        sendToggleMessage = config.getBoolean("settings.send-toggle-message", true);
        requireOffhandConvertor = config.getBoolean("settings.require-offhand-convertor", false);
        maxConversionsPerPass = Math.max(1, config.getInt("settings.max-conversions-per-pass", 64));
        toggleCooldownMs = Math.max(0L, config.getLong("settings.toggle-cooldown-ms", 100L));
        toggleClickMode = ToggleClickMode.fromConfig(config.getString("settings.toggle-click-mode", "both"));
    }

    public boolean isSendToggleMessage() {
        return sendToggleMessage;
    }

    public boolean isRequireOffhandConvertor() {
        return requireOffhandConvertor;
    }

    public int getMaxConversionsPerPass() {
        return maxConversionsPerPass;
    }

    public long getToggleCooldownMs() {
        return toggleCooldownMs;
    }

    public ToggleClickMode getToggleClickMode() {
        return toggleClickMode;
    }

    public enum ToggleClickMode {
        LEFT,
        RIGHT,
        BOTH;

        public boolean allowsLeftClick() {
            return this == LEFT || this == BOTH;
        }

        public boolean allowsRightClick() {
            return this == RIGHT || this == BOTH;
        }

        public String getLabel() {
            return switch (this) {
                case LEFT -> "left-click";
                case RIGHT -> "right-click";
                case BOTH -> "Click";
            };
        }

        private static ToggleClickMode fromConfig(String rawValue) {
            if (rawValue == null) {
                return BOTH;
            }

            return switch (rawValue.trim().toLowerCase()) {
                case "left", "left-click", "left_click" -> LEFT;
                case "right", "right-click", "right_click" -> RIGHT;
                default -> BOTH;
            };
        }
    }
}
