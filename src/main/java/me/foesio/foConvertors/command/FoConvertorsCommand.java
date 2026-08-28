package me.foesio.foConvertors.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.Supplier;
import me.foesio.core.command.FoAdminArguments;
import me.foesio.core.inventory.InventoryDepositResultMode;
import me.foesio.core.inventory.InventoryDepositService;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foConvertors.convertor.ConvertorItemFactory;
import me.foesio.foConvertors.convertor.ConvertorRegistry;
import me.foesio.foConvertors.convertor.ConvertorTemplate;
import me.foesio.foConvertors.convertor.ItemStackBatches;
import me.foesio.foConvertors.gui.EditorManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class FoConvertorsCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "foconvertors.admin";

    private final FoMessageService messages;
    private final ConvertorRegistry registry;
    private final ConvertorItemFactory itemFactory;
    private final EditorManager editorManager;
    private final FoReloadRegistry reloads;
    private final Supplier<UpdateNoticeService> updates;
    private final InventoryDepositService inventoryDeposits;
    private final FoAdminSounds adminSounds;
    private final FoEditorSounds editorSounds;
    private final FoSoundService sounds;

    public FoConvertorsCommand(
            FoMessageService messages,
            ConvertorRegistry registry,
            ConvertorItemFactory itemFactory,
            EditorManager editorManager,
            FoReloadRegistry reloads,
            Supplier<UpdateNoticeService> updates,
            InventoryDepositService inventoryDeposits,
            FoAdminSounds adminSounds,
            FoEditorSounds editorSounds,
            FoSoundService sounds
    ) {
        this.messages = messages;
        this.registry = registry;
        this.itemFactory = itemFactory;
        this.editorManager = editorManager;
        this.reloads = reloads;
        this.updates = updates;
        this.inventoryDeposits = inventoryDeposits;
        this.adminSounds = adminSounds;
        this.editorSounds = editorSounds;
        this.sounds = sounds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("version")) {
            UpdateNoticeService updateNoticeService = updates.get();
            if (updateNoticeService != null) {
                updateNoticeService.checkAndSendVersion(sender);
            }
            return true;
        }

        if (sub.equals("editor")) {
            if (!hasAdmin(sender)) {
                messages.send(sender, "command.no-permission");
                adminSounds.updateError(sender);
                return true;
            }
            if (!(sender instanceof Player player)) {
                messages.send(sender, "command.player-only");
                return true;
            }
            editorSounds.open(player);
            editorManager.openList(player);
            return true;
        }

        if (sub.equals("give")) {
            if (!hasAdmin(sender)) {
                messages.send(sender, "command.no-permission");
                adminSounds.updateError(sender);
                return true;
            }
            handleGive(sender, args);
            return true;
        }

        if (sub.equals("reload")) {
            if (!hasAdmin(sender)) {
                messages.send(sender, "command.no-permission");
                adminSounds.updateError(sender);
                return true;
            }
            FoReloadResult result = reloads.reload();
            if (!result.successful()) {
                messages.send(sender, "command.reload-failed",
                        "{prefix}{bad}Reload failed at {theme}{step}{bad}. {muted}{error}",
                        Map.of("step", result.failedStep(), "error", result.errorMessage()));
                if (sender instanceof Player player) {
                    adminSounds.reloadError(player);
                }
                return true;
            }
            messages.send(sender, "command.reloaded", "{prefix}{success}Config, messages, and convertor templates reloaded.");
            if (sender instanceof Player player) {
                adminSounds.reload(player);
            }
            return true;
        }

        messages.send(sender, "command.unknown-subcommand", Map.of("sub", sub));
        sendHelp(sender);
        adminSounds.updateError(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("version");
            if (hasAdmin(sender)) {
                subs.add("editor");
                subs.add("give");
                subs.add("reload");
            }
            return filterByPrefix(subs, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give") && hasAdmin(sender)) {
            return filterByPrefix(FoAdminArguments.onlinePlayer().complete(args[1]), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give") && hasAdmin(sender)) {
            return filterByPrefix(registry.ids(), args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("give") && hasAdmin(sender)) {
            return filterByPrefix(List.of("1", "16", "64"), args[3]);
        }

        return Collections.emptyList();
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "command.usage-give");
            adminSounds.updateError(sender);
            return;
        }

        Player target = FoAdminArguments.onlinePlayer().parse(args[1]).orElse(null);
        if (target == null) {
            messages.send(sender, "command.player-not-found", Map.of("player", args[1]));
            adminSounds.updateError(sender);
            return;
        }

        String id = args[2].toLowerCase(Locale.ROOT);
        ConvertorTemplate template = registry.get(id);
        if (template == null) {
            messages.send(sender, "command.convertor-not-found", Map.of("id", id));
            adminSounds.updateError(sender);
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ignored) {
                messages.send(sender, "command.invalid-amount");
                adminSounds.updateError(sender);
                return;
            }
        }

        ItemStack base = itemFactory.createConvertorItem(template, template.isDefaultEnabled());
        giveStackedItems(target, base, amount);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target.getName());
        placeholders.put("id", id);
        placeholders.put("amount", String.valueOf(amount));

        messages.send(sender, "command.gave", placeholders);
        if (!sender.getName().equalsIgnoreCase(target.getName())) {
            messages.send(target, "command.received", placeholders);
        }
        sounds.play(target, "convertor.received");
    }

    private void giveStackedItems(Player player, ItemStack templateItem, int amount) {
        inventoryDeposits.deposit(
                player,
                ItemStackBatches.split(templateItem, amount),
                OverflowPolicy.DROP_OVERFLOW,
                InventoryDepositResultMode.BASIC
        );
    }

    private void sendHelp(CommandSender sender) {
        messages.send(sender, "command.help-header");
        messages.send(sender, "command.help-version");
        if (hasAdmin(sender)) {
            messages.send(sender, "command.help-editor");
            messages.send(sender, "command.help-give");
            messages.send(sender, "command.help-reload");
        }
    }

    private static List<String> filterByPrefix(List<String> input, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return input.stream()
                .filter(entry -> entry.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toList());
    }

    private static boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission(ADMIN_PERMISSION);
    }
}
