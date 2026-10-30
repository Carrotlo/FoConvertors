package me.foesio.foConvertors;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foConvertors.command.FoConvertorsCommand;
import me.foesio.foConvertors.api.FoConvertorsApi;
import me.foesio.foConvertors.internal.DefaultFoConvertorsApi;
import me.foesio.foConvertors.config.PluginSettings;
import me.foesio.foConvertors.convertor.ConversionEngine;
import me.foesio.foConvertors.convertor.ConvertorItemFactory;
import me.foesio.foConvertors.convertor.ConvertorRegistry;
import me.foesio.foConvertors.convertor.ConvertorStorage;
import me.foesio.foConvertors.gui.EditorManager;
import me.foesio.foConvertors.integration.FoAutoCollectBridge;
import me.foesio.foConvertors.listener.AutoConvertListener;
import me.foesio.foConvertors.listener.ConvertorToggleListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.HandlerList;

public final class FoConvertors extends JavaPlugin {
    private static final int BSTATS_PLUGIN_ID = 33112;
    private static final String MODRINTH_PROJECT_ID = "HPe2sBGS";
    private static final Pattern LEGACY_MESSAGE_TOKEN = Pattern.compile("<([A-Za-z0-9_-]+)>");

    private FoCoreContext core;
    private FoMessageService messages;
    private FoSoundService sounds;
    private FoAdminSounds adminSounds;
    private FoEditorSounds editorSounds;
    private UpdateNoticeService updates;
    private FoConvertorsApi publicApi;
    private boolean removeLegacyMessageConfigPaths;

    @Override
    public void onEnable() {
        PluginSettings settings = new PluginSettings(this);
        settings.reload();

        this.messages = FoMessageService.load(this, messageMigrations());
        removeLegacyMessageConfigPaths();
        refreshCoreContext(true);
        migrateSprites();

        ConvertorStorage storage = new ConvertorStorage(this);
        storage.ensureFolder();

        ConvertorRegistry registry = new ConvertorRegistry(storage);
        registry.load();

        ConvertorItemFactory itemFactory = new ConvertorItemFactory(this, registry, messages, settings);
        ConversionEngine conversionEngine = new ConversionEngine(
                registry,
                itemFactory,
                settings,
                coreContext().inventoryDeposits(),
                sounds
        );

        EditorManager editorManager = new EditorManager(this, registry, itemFactory, messages, this::coreContext, editorSounds);
        AutoConvertListener autoConvertListener = new AutoConvertListener(this::coreContext, conversionEngine);
        ConvertorToggleListener toggleListener = new ConvertorToggleListener(itemFactory, settings, messages, autoConvertListener, sounds);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(editorManager, this);
        pluginManager.registerEvents(autoConvertListener, this);
        pluginManager.registerEvents(toggleListener, this);
        new FoAutoCollectBridge(this, autoConvertListener).register();

        FoReloadRegistry reloads = FoReloadRegistry.create()
                .add("config", settings::reload)
                .add("dialog foundation", () -> refreshCoreContext(false))
                .add("sounds", () -> sounds.reload())
                .addMessages(messages)
                .add("convertor item messages", itemFactory::reloadLoreTemplates)
                .add("convertor templates", registry::reload);

        FoConvertorsCommand commandHandler = new FoConvertorsCommand(
                messages,
                registry,
                itemFactory,
                editorManager,
                reloads,
                this::updates,
                coreContext().inventoryDeposits(),
                adminSounds,
                editorSounds,
                sounds
        );

        PluginCommand command = getCommand("foconvertors");
        if (command == null) {
            getLogger().severe("Command foconvertors missing from plugin.yml");
            return;
        }

        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        publicApi = new DefaultFoConvertorsApi(this, registry, itemFactory, settings, autoConvertListener);
        getServer().getServicesManager().register(FoConvertorsApi.class, publicApi, this, ServicePriority.Normal);

        getLogger().info("FoConvertors enabled with " + registry.ids().size() + " loaded convertor templates.");
    }

    @Override
    public void onDisable() {
        if (publicApi != null) {
            getServer().getServicesManager().unregister(FoConvertorsApi.class, publicApi);
            publicApi = null;
        }
        if (core != null) {
            core.close();
            core = null;
        }
        if (updates != null) {
            HandlerList.unregisterAll(updates);
            updates = null;
        }
    }

    private void refreshCoreContext(boolean startup) {
        if (updates != null) {
            HandlerList.unregisterAll(updates);
            updates = null;
        }
        if (core != null) {
            core.close();
        }

        NativeDialogConfigDefaults.addDefaults(getConfig());
        saveConfig();

        core = FoPluginCore.create(this);
        if (sounds == null) {
            sounds = core.createSounds();
            adminSounds = FoAdminSounds.create(sounds);
            editorSounds = FoEditorSounds.create(sounds);
        }
        core.warnIfNativeDialogsUnavailable();
        core.metrics(BSTATS_PLUGIN_ID);
        if (messages != null) {
            updates = core.createUpdateNotices(messages, MODRINTH_PROJECT_ID, adminSounds);
            if (startup) {
                updates.start();
            } else {
                updates.registerJoinListener();
            }
        }
    }

    public FoSoundService sounds() {
        return sounds;
    }

    public FoAdminSounds adminSounds() {
        return adminSounds;
    }

    public FoEditorSounds editorSounds() {
        return editorSounds;
    }

    public UpdateNoticeService updates() {
        return updates;
    }

    private FoMessageMigrations messageMigrations() {
        return FoMessageMigrations.create()
                .removeExact("command.version", "{prefix}{muted}Author: {theme}{author}{muted} | Version: {theme}{version}")
                .add(this::migrateLegacyMessageFile)
                .build();
    }

    private void migrateSprites() {
        messages.migrateToVersion(core.migrations(), 1, config -> {
            boolean changed = false;
            changed |= FoMessageService.addMissingToken(config, "tokens.prefix", ":repeater:", null);
            changed |= FoMessageService.addMissingToken(config, "command.player-not-found", ":player_head:");
            changed |= FoMessageService.addMissingToken(config, "command.convertor-not-found", ":barrier:");
            changed |= FoMessageService.addMissingToken(config, "command.gave", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "command.received", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "command.reloaded", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "command.reload-failed", ":redstone:");
            changed |= FoMessageService.addMissingToken(config, "editor.created", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "editor.deleted", ":lava_bucket:");
            changed |= FoMessageService.addMissingToken(config, "editor.recipe-created", ":crafting_table:");
            changed |= FoMessageService.addMissingToken(config, "editor.recipe-deleted", ":lava_bucket:");
            changed |= FoMessageService.addMissingToken(config, "editor.input-set", ":paper:");
            changed |= FoMessageService.addMissingToken(config, "editor.output-set", ":paper:");
            return true;
        });
    }

    private boolean migrateLegacyMessageFile(FileConfiguration messagesConfig) {
        boolean changed = false;
        changed |= migrateLegacyMessagesYaml(messagesConfig);
        changed |= migrateLegacyConfigMessageSettings(messagesConfig);
        changed |= repairLiteralMessageDefaults(messagesConfig);
        return changed;
    }

    private boolean migrateLegacyMessagesYaml(FileConfiguration messagesConfig) {
        boolean changed = false;

        if (messagesConfig.isString("prefix")) {
            messagesConfig.set("tokens.prefix", convertLegacyMessageTokens(messagesConfig.getString("prefix")));
            messagesConfig.set("prefix", null);
            changed = true;
        }

        ConfigurationSection colors = messagesConfig.getConfigurationSection("colors");
        if (colors != null) {
            changed |= migrateLegacyColor(colors, messagesConfig, "theme", "theme");
            changed |= migrateLegacyColor(colors, messagesConfig, "muted", "muted");
            changed |= migrateLegacyColor(colors, messagesConfig, "success", "success");
            changed |= migrateLegacyColor(colors, messagesConfig, "success", "good");
            changed |= migrateLegacyColor(colors, messagesConfig, "error", "bad");
            changed |= migrateLegacyColor(colors, messagesConfig, "bad", "bad");
            changed |= migrateLegacyColor(colors, messagesConfig, "white", "white");
            messagesConfig.set("colors", null);
            changed = true;
        }

        ConfigurationSection messages = messagesConfig.getConfigurationSection("messages");
        if (messages != null) {
            for (String key : messages.getKeys(true)) {
                if (!messages.isConfigurationSection(key)) {
                    messagesConfig.set(key, convertLegacyMessageTokens(messages.getString(key, "")));
                    changed = true;
                }
            }
            messagesConfig.set("messages", null);
            changed = true;
        }

        return changed;
    }

    private boolean repairLiteralMessageDefaults(FileConfiguration messagesConfig) {
        FileConfiguration defaults = bundledMessageDefaults();
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            if (!messagesConfig.contains(key) || isLiteralMessageKey(key, messagesConfig.getString(key))) {
                messagesConfig.set(key, defaults.get(key));
                changed = true;
            }
        }
        return changed;
    }

    private FileConfiguration bundledMessageDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = getResource("messages.yml")) {
            if (stream == null) {
                return defaults;
            }
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            getLogger().warning("Could not load bundled message defaults: " + exception.getMessage());
        }
        return defaults;
    }

    private static boolean isLiteralMessageKey(String key, String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.equals(key) || trimmed.equals("messages." + key);
    }

    private boolean migrateLegacyConfigMessageSettings(FileConfiguration messagesConfig) {
        FileConfiguration config = getConfig();
        boolean changed = false;

        if (config.isString("prefix")) {
            messagesConfig.set("tokens.prefix", convertLegacyMessageTokens(config.getString("prefix")));
            changed = true;
        }

        ConfigurationSection colors = config.getConfigurationSection("colors");
        if (colors != null) {
            changed |= migrateLegacyColor(colors, messagesConfig, "theme", "theme");
            changed |= migrateLegacyColor(colors, messagesConfig, "muted", "muted");
            changed |= migrateLegacyColor(colors, messagesConfig, "success", "success");
            changed |= migrateLegacyColor(colors, messagesConfig, "success", "good");
            changed |= migrateLegacyColor(colors, messagesConfig, "error", "bad");
            changed |= migrateLegacyColor(colors, messagesConfig, "bad", "bad");
            changed |= migrateLegacyColor(colors, messagesConfig, "white", "white");
        }

        ConfigurationSection legacyMessages = config.getConfigurationSection("messages");
        if (legacyMessages != null) {
            for (String key : legacyMessages.getKeys(true)) {
                if (!legacyMessages.isConfigurationSection(key)) {
                    messagesConfig.set(key, convertLegacyMessageTokens(legacyMessages.getString(key, "")));
                    changed = true;
                }
            }
        }

        if (config.contains("prefix") || config.contains("colors") || config.contains("messages")) {
            removeLegacyMessageConfigPaths = true;
        }
        return changed;
    }

    private boolean migrateLegacyColor(ConfigurationSection source, FileConfiguration target, String sourceKey, String tokenKey) {
        if (!source.isString(sourceKey)) {
            return false;
        }
        target.set("tokens." + tokenKey, source.getString(sourceKey));
        return true;
    }

    private void removeLegacyMessageConfigPaths() {
        if (!removeLegacyMessageConfigPaths) {
            return;
        }
        removeLegacyMessageConfigPaths = false;

        FileConfiguration config = getConfig();
        boolean changed = false;
        changed |= removeConfigPath(config, "prefix");
        changed |= removeConfigPath(config, "colors");
        changed |= removeConfigPath(config, "messages");
        if (changed) {
            saveConfig();
        }
    }

    private boolean removeConfigPath(FileConfiguration config, String path) {
        if (!config.contains(path)) {
            return false;
        }
        config.set(path, null);
        return true;
    }

    private String convertLegacyMessageTokens(String text) {
        if (text == null) {
            return null;
        }

        Matcher matcher = LEGACY_MESSAGE_TOKEN.matcher(text);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token.equals("error")) {
                token = "bad";
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement("{" + token + "}"));
        }
        matcher.appendTail(output);
        return output.toString().replace("{error}", "{bad}");
    }

    private FoCoreContext coreContext() {
        if (core == null) {
            throw new IllegalStateException("FoPluginCore context is not initialized.");
        }
        return core;
    }
}
