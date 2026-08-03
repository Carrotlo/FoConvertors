package me.foesio.foConvertors.integration;

import java.lang.reflect.Method;
import me.foesio.foConvertors.listener.AutoConvertListener;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class FoAutoCollectBridge implements Listener {
    private static final String PLUGIN_NAME = "FoAutoCollect";
    private static final String EVENT_CLASS_NAME = "me.foesio.foAutoCollect.event.AutoCollectItemsCollectedEvent";

    private final JavaPlugin plugin;
    private final AutoConvertListener autoConvertListener;

    public FoAutoCollectBridge(JavaPlugin plugin, AutoConvertListener autoConvertListener) {
        this.plugin = plugin;
        this.autoConvertListener = autoConvertListener;
    }

    public void register() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        Plugin autoCollect = pluginManager.getPlugin(PLUGIN_NAME);
        if (autoCollect == null || !autoCollect.isEnabled()) {
            return;
        }

        try {
            Class<?> rawEventClass = autoCollect.getClass().getClassLoader().loadClass(EVENT_CLASS_NAME);
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                plugin.getLogger().warning("FoAutoCollect event hook is not a Bukkit event.");
                return;
            }

            Method getPlayer = rawEventClass.getMethod("getPlayer");
            Class<? extends Event> eventClass = rawEventClass.asSubclass(Event.class);
            EventExecutor executor = (listener, event) -> {
                try {
                    Object player = getPlayer.invoke(event);
                    if (player instanceof Player onlinePlayer) {
                        autoConvertListener.requestProcess(onlinePlayer);
                    }
                } catch (ReflectiveOperationException exception) {
                    throw new EventException(exception);
                }
            };

            pluginManager.registerEvent(eventClass, this, EventPriority.MONITOR, executor, plugin, true);
            plugin.getLogger().info("Hooked FoAutoCollect item collection events.");
        } catch (ClassNotFoundException exception) {
            plugin.getLogger().warning("FoAutoCollect is installed but does not expose AutoCollectItemsCollectedEvent.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Could not hook FoAutoCollect: " + exception.getMessage());
        }
    }
}
