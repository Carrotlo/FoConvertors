package me.foesio.foConvertors.convertor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.foesio.core.inventory.InventoryDepositResultMode;
import me.foesio.core.inventory.InventoryDepositService;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.core.sound.FoSoundService;
import me.foesio.foConvertors.config.PluginSettings;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class ConversionEngine {
    private static final long PROCESSED_SOUND_COOLDOWN_MILLIS = 250L;

    private final ConvertorRegistry registry;
    private final ConvertorItemFactory itemFactory;
    private final PluginSettings settings;
    private final InventoryDepositService inventoryDeposits;
    private final FoSoundService sounds;
    private final ConcurrentHashMap<UUID, Long> lastProcessedSoundMillis = new ConcurrentHashMap<>();

    public ConversionEngine(
            ConvertorRegistry registry,
            ConvertorItemFactory itemFactory,
            PluginSettings settings,
            InventoryDepositService inventoryDeposits,
            FoSoundService sounds
    ) {
        this.registry = registry;
        this.itemFactory = itemFactory;
        this.settings = settings;
        this.inventoryDeposits = Objects.requireNonNull(inventoryDeposits, "inventoryDeposits");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
    }

    public int processPlayer(Player player) {
        Set<ConvertorTemplate> activeTemplates = resolveActiveTemplates(player);
        if (activeTemplates.isEmpty()) {
            return 0;
        }

        PlayerInventory inventory = player.getInventory();
        int maxConversions = settings.getMaxConversionsPerPass();
        int conversions = 0;
        List<QueuedOutput> queuedOutputs = new ArrayList<>(Math.min(maxConversions, 36));

        outer:
        for (ConvertorTemplate template : activeTemplates) {
            for (ConvertorRecipe recipe : template.getRecipes()) {
                ItemStack requiredInput = recipe.getInputItem();
                ItemStack resultOutput = recipe.getOutputItem();

                for (int slot = 0; slot <= 35; slot++) {
                    if (conversions >= maxConversions) {
                        break outer;
                    }

                    ItemStack current = inventory.getItem(slot);
                    if (current == null || current.getType().isAir()) {
                        continue;
                    }

                    if (!matchesStrictMeta(current, requiredInput)) {
                        continue;
                    }

                    if (itemFactory.isConvertorItem(current)) {
                        continue;
                    }

                    int possibleTimes = current.getAmount() / recipe.getInputAmount();
                    if (possibleTimes <= 0) {
                        continue;
                    }

                    int allowedTimes = Math.min(possibleTimes, maxConversions - conversions);
                    if (allowedTimes <= 0) {
                        continue;
                    }

                    int removeAmount = allowedTimes * recipe.getInputAmount();
                    int remaining = current.getAmount() - removeAmount;

                    if (remaining <= 0) {
                        inventory.setItem(slot, null);
                    } else {
                        current.setAmount(remaining);
                        inventory.setItem(slot, current);
                    }

                    int outputAmount = allowedTimes * recipe.getOutputAmount();
                    queuedOutputs.add(new QueuedOutput(resultOutput, outputAmount));
                    conversions += allowedTimes;
                }
            }
        }

        for (QueuedOutput queuedOutput : queuedOutputs) {
            giveOrDrop(player, queuedOutput.outputItem(), queuedOutput.amount());
        }

        if (conversions > 0) {
            playProcessedSound(player);
        }

        return conversions;
    }

    public void forgetPlayer(UUID playerId) {
        if (playerId != null) {
            lastProcessedSoundMillis.remove(playerId);
        }
    }

    private void playProcessedSound(Player player) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        long last = lastProcessedSoundMillis.getOrDefault(playerId, 0L);
        if (now - last < PROCESSED_SOUND_COOLDOWN_MILLIS) {
            return;
        }

        lastProcessedSoundMillis.put(playerId, now);
        sounds.playWithPitchVariation(player, "convertor.processed", 0.08f);
    }

    private Set<ConvertorTemplate> resolveActiveTemplates(Player player) {
        PlayerInventory inventory = player.getInventory();
        Set<ConvertorTemplate> templates = null;

        if (settings.isRequireOffhandConvertor()) {
            templates = addActiveTemplate(templates, inventory.getItemInOffHand());
        } else {
            for (ItemStack stack : inventory.getContents()) {
                templates = addActiveTemplate(templates, stack);
            }

            templates = addActiveTemplate(templates, inventory.getItemInOffHand());
        }

        return templates == null ? Set.of() : templates;
    }

    private Set<ConvertorTemplate> addActiveTemplate(Set<ConvertorTemplate> templates, ItemStack stack) {
        ConvertorItemFactory.ConvertorItemData data = itemFactory.readConvertorData(stack);
        if (data == null || !data.enabled() || data.templateId() == null) {
            return templates;
        }

        ConvertorTemplate template = registry.get(data.templateId());
        if (template == null) {
            return templates;
        }

        if (templates == null) {
            templates = new LinkedHashSet<>();
        }
        templates.add(template);
        return templates;
    }

    private static boolean matchesStrictMeta(ItemStack stack, ItemStack requiredTemplateItem) {
        if (stack == null || requiredTemplateItem == null) {
            return false;
        }

        if (stack.getType() != requiredTemplateItem.getType()) {
            return false;
        }

        return stack.isSimilar(requiredTemplateItem);
    }

    private void giveOrDrop(Player player, ItemStack outputItem, int amount) {
        inventoryDeposits.deposit(
                player,
                ItemStackBatches.split(outputItem, amount),
                OverflowPolicy.DROP_OVERFLOW,
                InventoryDepositResultMode.BASIC
        );
    }

    private record QueuedOutput(ItemStack outputItem, int amount) {
    }
}
