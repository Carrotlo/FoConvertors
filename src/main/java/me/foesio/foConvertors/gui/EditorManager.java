package me.foesio.foConvertors.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import me.foesio.core.FoCoreContext;
import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.DialogService;
import me.foesio.core.dialog.FallbackDialogService;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiSlots;
import me.foesio.core.gui.GuiTitles;
import me.foesio.core.item.FoItemStacks;
import me.foesio.core.message.FoStyle;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.text.FoText;
import me.foesio.foConvertors.convertor.ConvertorItemFactory;
import me.foesio.foConvertors.convertor.ConvertorRecipe;
import me.foesio.foConvertors.convertor.ConvertorRegistry;
import me.foesio.foConvertors.convertor.ConvertorTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class EditorManager implements Listener {
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_-]{3,32}$");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^\\d{1,4}$");
    private static final GuiButtonConfig BUTTONS = GuiButtonConfig.defaults();

    private static final int SLOT_INFO = 4;
    private static final int SLOT_INPUT_ITEM = 19;
    private static final int SLOT_INPUT_AMOUNT = 28;
    private static final int SLOT_OUTPUT_ITEM = 25;
    private static final int SLOT_OUTPUT_AMOUNT = 34;
    private static final int SLOT_DISPLAY_ITEM = 20;
    private static final int SLOT_RECIPES = 24;
    private static final int SLOT_DEFAULT_ENABLED = 31;
    private static final int SLOT_DELETE = 48;
    private static final int SLOT_BACK = GuiSlots.bottomMiddleSlot(6);
    private static final int SLOT_CREATE = 45;
    private static final int SLOT_DECOR_LEFT = 21;
    private static final int SLOT_DECOR_RIGHT = 23;
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL = 15;

    private final JavaPlugin plugin;
    private final ConvertorRegistry registry;
    private final ConvertorItemFactory itemFactory;
    private final FoMessageService messages;
    private final Supplier<FoCoreContext> coreSupplier;

    private final Set<UUID> awaitingCreateId = ConcurrentHashMap.newKeySet();
    private final Map<UUID, AmountInputState> awaitingAmountInput = new ConcurrentHashMap<>();
    private final Set<UUID> warnedNativeDialogFallback = ConcurrentHashMap.newKeySet();

    public EditorManager(
            JavaPlugin plugin,
            ConvertorRegistry registry,
            ConvertorItemFactory itemFactory,
            FoMessageService messages,
            Supplier<FoCoreContext> coreSupplier
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.itemFactory = itemFactory;
        this.messages = messages;
        this.coreSupplier = coreSupplier;
    }

    public void openList(Player player) {
        String title = GuiTitles.format("Convertors Editor");

        List<ConvertorTemplate> templates = new ArrayList<>(registry.all());
        templates.sort((left, right) -> left.getId().compareToIgnoreCase(right.getId()));

        Map<Integer, String> slotToId = new LinkedHashMap<>();
        GuiHolder holder = GuiHolder.list(slotToId);
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        int slot = 0;
        for (ConvertorTemplate template : templates) {
            if (slot >= 45) {
                break;
            }

            slotToId.put(slot, template.getId());
            inventory.setItem(slot, buildTemplateIcon(template));
            slot++;
        }

        fillBottomRow(inventory);

        inventory.setItem(SLOT_CREATE, createMenuItem(
                Material.LIME_DYE,
                "{theme}Create Convertor",
                List.of(
                        "{white}Click to create a new convertor template.",
                        "{white}You will enter the template id in chat."
                )
        ));

        player.openInventory(inventory);
    }

    public void openEditor(Player player, String templateId) {
        ConvertorTemplate template = registry.get(templateId);
        if (template == null) {
            messages.send(player, "editor.not-found", Map.of("id", templateId));
            openList(player);
            return;
        }

        String title = GuiTitles.format("Convertor: " + template.getId());
        Inventory inventory = Bukkit.createInventory(GuiHolder.template(template.getId()), 54, title);

        decorateTemplateEditor(inventory);

        inventory.setItem(SLOT_DISPLAY_ITEM, previewItem(
                template.getDisplayItem(),
                "{theme}Convertor Display Item",
                List.of(
                        "{white}Controls the given convertor item look.",
                        "{white}Put an item on your cursor and click to set."
                )
        ));

        inventory.setItem(SLOT_RECIPES, createMenuItem(
                Material.CRAFTING_TABLE,
                "{theme}Recipes",
                List.of(
                        "{white}Configured recipes: {theme}" + template.getRecipes().size(),
                        "{white}Click to edit input and output recipes."
                )
        ));

        inventory.setItem(SLOT_DEFAULT_ENABLED, createMenuItem(
                template.isDefaultEnabled() ? Material.LIME_DYE : Material.RED_DYE,
                template.isDefaultEnabled() ? "{success}Default: Enabled" : "{bad}Default: Disabled",
                List.of("{white}Click to toggle default state for given items.")
        ));

        inventory.setItem(SLOT_DELETE, createMenuItem(
                Material.LAVA_BUCKET,
                "{bad}Delete Convertor",
                List.of(
                        "{white}Click to open delete confirmation.",
                        "{white}This removes the template from disk."
                )
        ));

        inventory.setItem(SLOT_BACK, BUTTONS.back());

        player.openInventory(inventory);
    }

    private void openRecipeList(Player player, String templateId) {
        ConvertorTemplate template = registry.get(templateId);
        if (template == null) {
            messages.send(player, "editor.not-found", Map.of("id", templateId));
            openList(player);
            return;
        }

        String title = GuiTitles.format("Recipes: " + template.getId());
        Map<Integer, String> slotToRecipeId = new LinkedHashMap<>();
        Inventory inventory = Bukkit.createInventory(GuiHolder.recipeList(template.getId(), slotToRecipeId), 54, title);

        int slot = 0;
        for (ConvertorRecipe recipe : template.getRecipes()) {
            if (slot >= 45) {
                break;
            }

            slotToRecipeId.put(slot, recipe.getId());
            inventory.setItem(slot, buildRecipeIcon(recipe));
            slot++;
        }

        fillBottomRow(inventory);
        inventory.setItem(SLOT_CREATE, createMenuItem(
                Material.LIME_DYE,
                "{theme}Add Recipe",
                List.of("{white}Create a new input/output recipe.")
        ));
        inventory.setItem(SLOT_BACK, BUTTONS.back());

        player.openInventory(inventory);
    }

    private void openRecipeEditor(Player player, String templateId, String recipeId) {
        ConvertorTemplate template = registry.get(templateId);
        if (template == null) {
            messages.send(player, "editor.not-found", Map.of("id", templateId));
            openList(player);
            return;
        }

        ConvertorRecipe recipe = template.getRecipe(recipeId);
        if (recipe == null) {
            messages.send(player, "editor.recipe-not-found", Map.of("recipe", recipeId));
            openRecipeList(player, templateId);
            return;
        }

        String title = GuiTitles.format("Recipe: " + recipe.getId());
        Inventory inventory = Bukkit.createInventory(GuiHolder.recipe(template.getId(), recipe.getId()), 54, title);

        decorateRecipeEditor(inventory, template, recipe);

        inventory.setItem(SLOT_INPUT_ITEM, previewItem(
                recipe.getInputItem(),
                "{theme}Input Item",
                List.of(
                        "{white}Current amount: {theme}" + recipe.getInputAmount(),
                        "{white}Put an item on your cursor and click to set."
                )
        ));

        inventory.setItem(SLOT_INPUT_AMOUNT, createAmountButton(
                "{theme}Input Amount",
                recipe.getInputAmount()
        ));

        inventory.setItem(SLOT_OUTPUT_ITEM, previewItem(
                recipe.getOutputItem(),
                "{theme}Output Item",
                List.of(
                        "{white}Current amount: {theme}" + recipe.getOutputAmount(),
                        "{white}Put an item on your cursor and click to set."
                )
        ));

        inventory.setItem(SLOT_OUTPUT_AMOUNT, createAmountButton(
                "{theme}Output Amount",
                recipe.getOutputAmount()
        ));

        inventory.setItem(SLOT_DELETE, createMenuItem(
                Material.LAVA_BUCKET,
                "{bad}Delete Recipe",
                List.of("{white}Click to open delete confirmation.")
        ));

        inventory.setItem(SLOT_BACK, BUTTONS.back());

        player.openInventory(inventory);
    }

    private void openConfirmDeleteTemplate(Player player, String templateId) {
        ConvertorTemplate template = registry.get(templateId);
        if (template == null) {
            messages.send(player, "editor.not-found", Map.of("id", templateId));
            openList(player);
            return;
        }

        String title = GuiTitles.format("Delete Convertor");
        Inventory inventory = Bukkit.createInventory(GuiHolder.confirmTemplate(template.getId()), 27, title);
        fillAll(inventory);
        inventory.setItem(SLOT_CONFIRM, createMenuItem(
                Material.LIME_DYE,
                "{success}Confirm",
                List.of("{white}Delete convertor {theme}" + template.getId() + "{white}.")
        ));
        inventory.setItem(SLOT_CANCEL, createMenuItem(
                Material.RED_WOOL,
                "{bad}Cancel",
                List.of("{white}Return to convertor settings.")
        ));
        player.openInventory(inventory);
    }

    private void openConfirmDeleteRecipe(Player player, String templateId, String recipeId) {
        ConvertorTemplate template = registry.get(templateId);
        if (template == null) {
            messages.send(player, "editor.not-found", Map.of("id", templateId));
            openList(player);
            return;
        }

        ConvertorRecipe recipe = template.getRecipe(recipeId);
        if (recipe == null) {
            messages.send(player, "editor.recipe-not-found", Map.of("recipe", recipeId));
            openRecipeList(player, templateId);
            return;
        }

        String title = GuiTitles.format("Delete Recipe");
        Inventory inventory = Bukkit.createInventory(GuiHolder.confirmRecipe(template.getId(), recipe.getId()), 27, title);
        fillAll(inventory);
        inventory.setItem(SLOT_CONFIRM, createMenuItem(
                Material.LIME_DYE,
                "{success}Confirm",
                List.of("{white}Delete recipe {theme}" + recipe.getId() + "{white}.")
        ));
        inventory.setItem(SLOT_CANCEL, createMenuItem(
                Material.RED_WOOL,
                "{bad}Cancel",
                List.of("{white}Return to recipe settings.")
        ));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        GuiHolder holder = getHolder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }

        if (event.getClickedInventory() == null) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < topSize) {
            event.setCancelled(true);
            switch (holder.type) {
                case LIST -> handleListClick(player, event.getSlot(), holder);
                case TEMPLATE -> handleTemplateClick(player, event, holder.templateId);
                case RECIPE_LIST -> handleRecipeListClick(player, event.getSlot(), holder);
                case RECIPE -> handleRecipeClick(player, event, holder.templateId, holder.recipeId);
                case CONFIRM_DELETE_TEMPLATE -> handleConfirmTemplateClick(player, event.getSlot(), holder.templateId);
                case CONFIRM_DELETE_RECIPE -> handleConfirmRecipeClick(player, event.getSlot(), holder.templateId, holder.recipeId);
            }
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        GuiHolder holder = getHolder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String rawInput = event.getMessage().trim();
        String normalizedInput = rawInput.toLowerCase(Locale.ROOT);

        if (awaitingCreateId.contains(uuid)) {
            event.setCancelled(true);

            if (normalizedInput.equals("cancel")) {
                awaitingCreateId.remove(uuid);
                runForPlayer(event.getPlayer(), () -> messages.send(event.getPlayer(), "editor.create-cancelled"));
                return;
            }

            if (!ID_PATTERN.matcher(normalizedInput).matches()) {
                runForPlayer(event.getPlayer(), () -> messages.send(event.getPlayer(), "editor.invalid-id"));
                return;
            }

            if (registry.get(normalizedInput) != null) {
                runForPlayer(event.getPlayer(), () -> messages.send(event.getPlayer(), "editor.already-exists", Map.of("id", normalizedInput)));
                return;
            }

            awaitingCreateId.remove(uuid);
            runForPlayer(event.getPlayer(), () -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    return;
                }

                ConvertorTemplate created = ConvertorTemplate.createDefault(normalizedInput);
                registry.save(created);
                messages.send(player, "editor.created", Map.of("id", normalizedInput));
                openEditor(player, normalizedInput);
            });
            return;
        }

        AmountInputState amountState = awaitingAmountInput.get(uuid);
        if (amountState == null) {
            return;
        }

        event.setCancelled(true);
        if (normalizedInput.equals("cancel")) {
            awaitingAmountInput.remove(uuid);
            runForPlayer(event.getPlayer(), () -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    return;
                }
                messages.send(player, "editor.amount-cancelled");
                openRecipeEditor(player, amountState.templateId, amountState.recipeId);
            });
            return;
        }

        if (!AMOUNT_PATTERN.matcher(normalizedInput).matches()) {
            runForPlayer(event.getPlayer(), () -> messages.send(event.getPlayer(), "editor.amount-invalid"));
            return;
        }

        int parsedAmount = Integer.parseInt(normalizedInput);
        if (parsedAmount <= 0 || parsedAmount > 6400) {
            runForPlayer(event.getPlayer(), () -> messages.send(event.getPlayer(), "editor.amount-invalid"));
            return;
        }

        awaitingAmountInput.remove(uuid);
        runForPlayer(event.getPlayer(), () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                return;
            }

            ConvertorTemplate template = registry.get(amountState.templateId);
            if (template == null) {
                messages.send(player, "editor.not-found", Map.of("id", amountState.templateId));
                openList(player);
                return;
            }

            ConvertorRecipe recipe = template.getRecipe(amountState.recipeId);
            if (recipe == null) {
                messages.send(player, "editor.recipe-not-found", Map.of("recipe", amountState.recipeId));
                openRecipeList(player, amountState.templateId);
                return;
            }

            if (amountState.type == AmountType.INPUT) {
                recipe.setInputAmount(parsedAmount);
                messages.send(player, "editor.input-amount-set", Map.of("amount", String.valueOf(parsedAmount), "recipe", recipe.getId()));
            } else {
                recipe.setOutputAmount(parsedAmount);
                messages.send(player, "editor.output-amount-set", Map.of("amount", String.valueOf(parsedAmount), "recipe", recipe.getId()));
            }

            registry.save(template);
            openRecipeEditor(player, amountState.templateId, amountState.recipeId);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        awaitingCreateId.remove(uuid);
        awaitingAmountInput.remove(uuid);
        warnedNativeDialogFallback.remove(uuid);
    }

    private void handleListClick(Player player, int slot, GuiHolder holder) {
        if (slot == SLOT_CREATE) {
            openCreateIdInput(player);
            return;
        }

        String templateId = holder.slotToTemplateId.get(slot);
        if (templateId != null) {
            openEditor(player, templateId);
        }
    }

    private void handleTemplateClick(Player player, InventoryClickEvent event, String templateId) {
        ConvertorTemplate template = registry.get(templateId);
        if (template == null) {
            messages.send(player, "editor.not-found", Map.of("id", templateId));
            openList(player);
            return;
        }

        int slot = event.getSlot();
        ItemStack cursor = event.getCursor();

        if (slot == SLOT_DISPLAY_ITEM) {
            if (!isRealItem(cursor)) {
                messages.send(player, "editor.cursor-required");
                return;
            }
            template.setDisplayItem(cursor);
            registry.save(template);
            messages.send(player, "editor.display-set", Map.of("id", template.getId()));
            openEditor(player, template.getId());
            return;
        }

        if (slot == SLOT_RECIPES) {
            openRecipeList(player, template.getId());
            return;
        }

        if (slot == SLOT_DEFAULT_ENABLED) {
            template.setDefaultEnabled(!template.isDefaultEnabled());
            registry.save(template);
            openEditor(player, template.getId());
            return;
        }

        if (slot == SLOT_DELETE) {
            openConfirmDeleteTemplate(player, template.getId());
            return;
        }

        if (slot == SLOT_BACK) {
            awaitingAmountInput.remove(player.getUniqueId());
            openList(player);
            return;
        }

    }

    private void handleRecipeListClick(Player player, int slot, GuiHolder holder) {
        ConvertorTemplate template = registry.get(holder.templateId);
        if (template == null) {
            messages.send(player, "editor.not-found", Map.of("id", holder.templateId));
            openList(player);
            return;
        }

        if (slot == SLOT_CREATE) {
            ConvertorRecipe recipe = ConvertorRecipe.createDefault(template.nextRecipeId());
            template.addRecipe(recipe);
            registry.save(template);
            messages.send(player, "editor.recipe-created", Map.of("recipe", recipe.getId()));
            openRecipeEditor(player, template.getId(), recipe.getId());
            return;
        }

        if (slot == SLOT_BACK) {
            openEditor(player, template.getId());
            return;
        }

        String recipeId = holder.slotToRecipeId.get(slot);
        if (recipeId != null) {
            openRecipeEditor(player, template.getId(), recipeId);
        }
    }

    private void handleRecipeClick(Player player, InventoryClickEvent event, String templateId, String recipeId) {
        ConvertorTemplate template = registry.get(templateId);
        if (template == null) {
            messages.send(player, "editor.not-found", Map.of("id", templateId));
            openList(player);
            return;
        }

        ConvertorRecipe recipe = template.getRecipe(recipeId);
        if (recipe == null) {
            messages.send(player, "editor.recipe-not-found", Map.of("recipe", recipeId));
            openRecipeList(player, templateId);
            return;
        }

        int slot = event.getSlot();
        ItemStack cursor = event.getCursor();

        if (slot == SLOT_INPUT_ITEM) {
            if (!isRealItem(cursor)) {
                messages.send(player, "editor.cursor-required");
                return;
            }
            recipe.setInputItem(cursor);
            recipe.setInputAmount(cursor.getAmount());
            registry.save(template);
            messages.send(player, "editor.input-set", Map.of("id", template.getId(), "recipe", recipe.getId()));
            openRecipeEditor(player, template.getId(), recipe.getId());
            return;
        }

        if (slot == SLOT_OUTPUT_ITEM) {
            if (!isRealItem(cursor)) {
                messages.send(player, "editor.cursor-required");
                return;
            }
            recipe.setOutputItem(cursor);
            recipe.setOutputAmount(cursor.getAmount());
            registry.save(template);
            messages.send(player, "editor.output-set", Map.of("id", template.getId(), "recipe", recipe.getId()));
            openRecipeEditor(player, template.getId(), recipe.getId());
            return;
        }

        if (slot == SLOT_INPUT_AMOUNT) {
            openAmountInput(player, template, recipe, AmountType.INPUT);
            return;
        }

        if (slot == SLOT_OUTPUT_AMOUNT) {
            openAmountInput(player, template, recipe, AmountType.OUTPUT);
            return;
        }

        if (slot == SLOT_DELETE) {
            if (template.getRecipes().size() <= 1) {
                messages.send(player, "editor.recipe-delete-last");
                return;
            }
            openConfirmDeleteRecipe(player, template.getId(), recipe.getId());
            return;
        }

        if (slot == SLOT_BACK) {
            awaitingAmountInput.remove(player.getUniqueId());
            openRecipeList(player, template.getId());
            return;
        }

    }

    private void handleConfirmTemplateClick(Player player, int slot, String templateId) {
        if (slot == SLOT_CONFIRM) {
            registry.delete(templateId);
            messages.send(player, "editor.deleted", Map.of("id", templateId));
            openList(player);
            return;
        }

        if (slot == SLOT_CANCEL) {
            openEditor(player, templateId);
        }
    }

    private void handleConfirmRecipeClick(Player player, int slot, String templateId, String recipeId) {
        ConvertorTemplate template = registry.get(templateId);
        if (template == null) {
            messages.send(player, "editor.not-found", Map.of("id", templateId));
            openList(player);
            return;
        }

        if (slot == SLOT_CONFIRM) {
            if (!template.removeRecipe(recipeId)) {
                messages.send(player, "editor.recipe-delete-last");
                openRecipeEditor(player, template.getId(), recipeId);
                return;
            }
            registry.save(template);
            messages.send(player, "editor.recipe-deleted", Map.of("recipe", recipeId));
            openRecipeList(player, template.getId());
            return;
        }

        if (slot == SLOT_CANCEL) {
            openRecipeEditor(player, template.getId(), recipeId);
        }
    }

    private void openCreateIdInput(Player player) {
        awaitingAmountInput.remove(player.getUniqueId());
        TextDialogRequest request = new TextDialogRequest(
                FoStyle.THEME + "Create Convertor",
                List.of(
                        FoStyle.MUTED + "Enter the id for the new convertor.",
                        FoStyle.MUTED + "Expected input: " + FoStyle.WHITE + "3-32 chars: a-z, 0-9, _, -"
                ),
                FoStyle.WHITE + "Convertor ID",
                "",
                "3-32 chars: a-z, 0-9, _, -",
                DialogButton.confirm("Create", "", 100),
                DialogButton.cancel("Cancel", "", 100),
                300,
                300,
                32,
                true,
                false,
                false
        );
        openEditorTextInput(
                player,
                request,
                input -> handleNativeCreateIdInput(player, input),
                () -> {
                    messages.send(player, "editor.create-cancelled");
                    openList(player);
                },
                () -> openCreateIdChatFallback(player)
        );
    }

    private void openCreateIdChatFallback(Player player) {
        awaitingAmountInput.remove(player.getUniqueId());
        awaitingCreateId.add(player.getUniqueId());
        player.closeInventory();
        messages.send(player, "editor.enter-id");
    }

    private void handleNativeCreateIdInput(Player player, String input) {
        String normalizedInput = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (normalizedInput.equals("cancel")) {
            messages.send(player, "editor.create-cancelled");
            openList(player);
            return;
        }

        if (!ID_PATTERN.matcher(normalizedInput).matches()) {
            messages.send(player, "editor.invalid-id");
            openList(player);
            return;
        }

        if (registry.get(normalizedInput) != null) {
            messages.send(player, "editor.already-exists", Map.of("id", normalizedInput));
            openList(player);
            return;
        }

        ConvertorTemplate created = ConvertorTemplate.createDefault(normalizedInput);
        registry.save(created);
        messages.send(player, "editor.created", Map.of("id", normalizedInput));
        openEditor(player, normalizedInput);
    }

    private void openAmountInput(Player player, ConvertorTemplate template, ConvertorRecipe recipe, AmountType type) {
        awaitingCreateId.remove(player.getUniqueId());
        AmountInputState state = new AmountInputState(template.getId(), recipe.getId(), type);
        int current = type == AmountType.INPUT ? recipe.getInputAmount() : recipe.getOutputAmount();
        String typeLabel = type == AmountType.INPUT ? "Input" : "Output";
        Map<String, String> placeholders = Map.of(
                "id", template.getId(),
                "recipe", recipe.getId(),
                "current", String.valueOf(current),
                "type", typeLabel
        );
        TextDialogRequest request = new TextDialogRequest(
                FoStyle.THEME + typeLabel + " Amount",
                List.of(
                        FoStyle.MUTED + "Convertor: " + FoStyle.THEME + template.getId(),
                        FoStyle.MUTED + "Recipe: " + FoStyle.THEME + recipe.getId(),
                        FoStyle.MUTED + "Current value: " + FoStyle.THEME + current,
                        FoStyle.MUTED + "Expected input: " + FoStyle.WHITE + "1-6400"
                ),
                FoStyle.WHITE + "Amount",
                String.valueOf(current),
                "1-6400",
                DialogButton.save("Save", "", 100),
                DialogButton.cancel("Cancel", "", 100),
                300,
                300,
                4,
                true,
                false,
                false
        );
        openEditorTextInput(
                player,
                request,
                input -> handleNativeAmountInput(player, state, input),
                () -> {
                    messages.send(player, "editor.amount-cancelled");
                    openRecipeEditor(player, state.templateId, state.recipeId);
                },
                () -> openAmountChatFallback(player, state, placeholders)
        );
    }

    private void openAmountChatFallback(Player player, AmountInputState state, Map<String, String> placeholders) {
        awaitingAmountInput.put(player.getUniqueId(), state);
        player.closeInventory();
        messages.send(
                player,
                state.type == AmountType.INPUT ? "editor.enter-input-amount" : "editor.enter-output-amount",
                placeholders
        );
    }

    private void handleNativeAmountInput(Player player, AmountInputState state, String input) {
        String normalizedInput = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (normalizedInput.equals("cancel")) {
            messages.send(player, "editor.amount-cancelled");
            openRecipeEditor(player, state.templateId, state.recipeId);
            return;
        }

        if (!AMOUNT_PATTERN.matcher(normalizedInput).matches()) {
            messages.send(player, "editor.amount-invalid");
            openRecipeEditor(player, state.templateId, state.recipeId);
            return;
        }

        int parsedAmount = Integer.parseInt(normalizedInput);
        if (parsedAmount <= 0 || parsedAmount > 6400) {
            messages.send(player, "editor.amount-invalid");
            openRecipeEditor(player, state.templateId, state.recipeId);
            return;
        }

        ConvertorTemplate template = registry.get(state.templateId);
        if (template == null) {
            messages.send(player, "editor.not-found", Map.of("id", state.templateId));
            openList(player);
            return;
        }

        ConvertorRecipe recipe = template.getRecipe(state.recipeId);
        if (recipe == null) {
            messages.send(player, "editor.recipe-not-found", Map.of("recipe", state.recipeId));
            openRecipeList(player, state.templateId);
            return;
        }

        if (state.type == AmountType.INPUT) {
            recipe.setInputAmount(parsedAmount);
            messages.send(player, "editor.input-amount-set", Map.of("amount", String.valueOf(parsedAmount), "recipe", recipe.getId()));
        } else {
            recipe.setOutputAmount(parsedAmount);
            messages.send(player, "editor.output-amount-set", Map.of("amount", String.valueOf(parsedAmount), "recipe", recipe.getId()));
        }

        registry.save(template);
        openRecipeEditor(player, state.templateId, state.recipeId);
    }

    private void openEditorTextInput(
            Player player,
            TextDialogRequest request,
            Consumer<String> onSubmit,
            Runnable onCancel,
            Runnable chatFallback
    ) {
        FoCoreContext core = coreContext();
        DialogService fallback = new FallbackDialogService(
                core.nativeDialogs(),
                (target, noticeRequest, onClose) -> run(onClose),
                (target, confirmationRequest, onConfirm, fallbackCancel) -> run(fallbackCancel),
                (target, textRequest, fallbackSubmit, fallbackCancel) -> {
                    warnPlayerFallback(target);
                    chatFallback.run();
                }
        );
        DialogService dialogs = core.createDialogService(fallback);
        EditorDialogInputs.openTextFromInventory(
                plugin,
                core.inventoryCloseSuppressor(),
                dialogs,
                player,
                request,
                onSubmit,
                onCancel
        );
    }

    private FoCoreContext coreContext() {
        FoCoreContext core = coreSupplier.get();
        if (core == null) {
            throw new IllegalStateException("FoPluginCore context is not initialized.");
        }
        return core;
    }

    private void warnPlayerFallback(Player player) {
        NativeDialogSupport support = coreContext().nativeDialogs();
        if (!support.configEnabled() || support.canUseNativeDialogs() || !support.warnOnFallback()) {
            return;
        }
        if (!player.hasPermission("foconvertors.admin")) {
            return;
        }
        if (warnedNativeDialogFallback.add(player.getUniqueId())) {
            messages.send(player, "native-dialogs-fallback");
        }
    }

    private void run(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }

    private void runForPlayer(Player player, Runnable runnable) {
        coreContext().scheduler().runForPlayer(player, runnable);
    }

    private ItemStack buildTemplateIcon(ConvertorTemplate template) {
        ItemStack icon = itemFactory.createConvertorItem(template, template.isDefaultEnabled());
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.renderTemplate("{theme}" + template.getId(), Collections.emptyMap()));
            List<String> lore = new ArrayList<>();
            lore.add(messages.renderTemplate("{white}Recipes: {theme}" + template.getRecipes().size(), Collections.emptyMap()));
            int previewCount = Math.min(3, template.getRecipes().size());
            for (int index = 0; index < previewCount; index++) {
                lore.add(messages.renderTemplate("{muted}- " + formatRecipeLine(template.getRecipes().get(index)), Collections.emptyMap()));
            }
            if (template.getRecipes().size() > previewCount) {
                lore.add(messages.renderTemplate("{muted}- +" + (template.getRecipes().size() - previewCount) + " more", Collections.emptyMap()));
            }
            lore.add(messages.renderTemplate("{white}Default: " + (template.isDefaultEnabled() ? "{success}Enabled" : "{bad}Disabled"), Collections.emptyMap()));
            lore.add(messages.renderTemplate("{white}Click to edit this convertor.", Collections.emptyMap()));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack buildRecipeIcon(ConvertorRecipe recipe) {
        ItemStack icon = recipe.getInputItem();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.renderTemplate("{theme}" + recipe.getId(), Collections.emptyMap()));
            meta.setLore(List.of(
                    messages.renderTemplate("{white}Input: {theme}" + recipe.getInputAmount() + "x " + recipe.getInputItem().getType().name(), Collections.emptyMap()),
                    messages.renderTemplate("{white}Output: {theme}" + recipe.getOutputAmount() + "x " + recipe.getOutputItem().getType().name(), Collections.emptyMap()),
                    messages.renderTemplate("{white}Click to edit this recipe.", Collections.emptyMap())
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack previewItem(ItemStack base, String name, List<String> lore) {
        ItemStack item = FoItemStacks.cloneItem(base);
        if (item.getType().isAir()) {
            item.setType(Material.CHEST_MINECART);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (!meta.hasDisplayName()) {
                meta.setDisplayName(messages.renderTemplate(name, Collections.emptyMap()));
            }
            List<String> existingLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            List<String> colorizedLore = new ArrayList<>(existingLore);
            if (!colorizedLore.isEmpty()) {
                colorizedLore.add(messages.renderTemplate("{white} ", Collections.emptyMap()));
            }
            for (String line : lore) {
                colorizedLore.add(messages.renderTemplate(line, Collections.emptyMap()));
            }
            colorizedLore.add(messages.renderTemplate("{white}Name: " + getDisplayNameText(meta), Collections.emptyMap()));
            colorizedLore.add(messages.renderTemplate("{white}Lore lines: {theme}" + getLoreCount(meta), Collections.emptyMap()));
            if (meta.hasLore()) {
                List<String> originalLore = meta.getLore();
                int maxPreview = Math.min(3, originalLore.size());
                for (int i = 0; i < maxPreview; i++) {
                    colorizedLore.add(messages.renderTemplate("{muted}- " + cleanText(originalLore.get(i)), Collections.emptyMap()));
                }
            }
            meta.setLore(colorizedLore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createAmountButton(String name, int amount) {
        return createMenuItem(
                Material.PAPER,
                name,
                List.of(
                        "{white}Current value: {theme}" + amount,
                        "{white}Click to type a new amount in chat.",
                        "{white}Type {theme}cancel {white}to abort."
                )
        );
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.renderTemplate(name, Collections.emptyMap()));
            List<String> colorizedLore = new ArrayList<>();
            for (String line : lore) {
                colorizedLore.add(messages.renderTemplate(line, Collections.emptyMap()));
            }
            meta.setLore(colorizedLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static GuiHolder getHolder(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof GuiHolder guiHolder) {
            return guiHolder;
        }
        return null;
    }

    private static boolean isRealItem(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    private void decorateTemplateEditor(Inventory inventory) {
        fillBorders(inventory);
    }

    private void decorateRecipeEditor(Inventory inventory, ConvertorTemplate template, ConvertorRecipe recipe) {
        fillBorders(inventory);
        inventory.setItem(SLOT_DECOR_LEFT, createMenuItem(Material.LIME_STAINED_GLASS_PANE, "{theme}Input", List.of("{white}Set input item and amount.")));
        inventory.setItem(SLOT_DECOR_RIGHT, createMenuItem(Material.RED_STAINED_GLASS_PANE, "{theme}Output", List.of("{white}Set output item and amount.")));
        ItemStack inputItem = recipe.getInputItem();
        ItemStack outputItem = recipe.getOutputItem();
        inventory.setItem(SLOT_INFO, createMenuItem(
                Material.BOOK,
                "{theme}Recipe: " + recipe.getId(),
                List.of(
                        "{white}Convertor: {theme}" + template.getId(),
                        "{white}Input: {theme}" + inputItem.getType().name() + " x" + recipe.getInputAmount(),
                        "{muted}Name: " + getDisplayNameText(inputItem.getItemMeta()),
                        "{muted}Lore lines: " + getLoreCount(inputItem.getItemMeta()),
                        "{white}Output: {theme}" + outputItem.getType().name() + " x" + recipe.getOutputAmount(),
                        "{muted}Name: " + getDisplayNameText(outputItem.getItemMeta()),
                        "{muted}Lore lines: " + getLoreCount(outputItem.getItemMeta())
                )
        ));
    }

    private static void fillBottomRow(Inventory inventory) {
        for (int slot = 45; slot <= 53; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, EditorItemFactory.filler());
            }
        }
    }

    private static void fillBorders(Inventory inventory) {
        for (int slot = 0; slot <= 8; slot++) {
            inventory.setItem(slot, EditorItemFactory.filler());
        }
        for (int row = 1; row < 6; row++) {
            inventory.setItem(row * 9, EditorItemFactory.filler());
            inventory.setItem(row * 9 + 8, EditorItemFactory.filler());
        }
        fillBottomRow(inventory);
    }

    private static void fillAll(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, EditorItemFactory.filler());
        }
    }

    private static String formatRecipeLine(ConvertorRecipe recipe) {
        return recipe.getInputAmount() + "x "
                + recipe.getInputItem().getType().name()
                + " -> "
                + recipe.getOutputAmount() + "x "
                + recipe.getOutputItem().getType().name();
    }

    private static String getDisplayNameText(ItemMeta meta) {
        if (meta == null || !meta.hasDisplayName()) {
            return "{muted}none";
        }
        return "{theme}" + cleanText(meta.getDisplayName());
    }

    private static int getLoreCount(ItemMeta meta) {
        if (meta == null || !meta.hasLore()) {
            return 0;
        }
        return meta.getLore().size();
    }

    private static String cleanText(String input) {
        if (input == null || input.isBlank()) {
            return "none";
        }
        return FoText.stripColor(input);
    }

    private static final class GuiHolder implements InventoryHolder {
        private final SessionType type;
        private final String templateId;
        private final String recipeId;
        private final Map<Integer, String> slotToTemplateId;
        private final Map<Integer, String> slotToRecipeId;

        private GuiHolder(
                SessionType type,
                String templateId,
                String recipeId,
                Map<Integer, String> slotToTemplateId,
                Map<Integer, String> slotToRecipeId
        ) {
            this.type = type;
            this.templateId = templateId;
            this.recipeId = recipeId;
            this.slotToTemplateId = slotToTemplateId;
            this.slotToRecipeId = slotToRecipeId;
        }

        private static GuiHolder list(Map<Integer, String> slotToTemplateId) {
            return new GuiHolder(SessionType.LIST, null, null, slotToTemplateId, Collections.emptyMap());
        }

        private static GuiHolder template(String templateId) {
            return new GuiHolder(SessionType.TEMPLATE, templateId, null, Collections.emptyMap(), Collections.emptyMap());
        }

        private static GuiHolder recipeList(String templateId, Map<Integer, String> slotToRecipeId) {
            return new GuiHolder(SessionType.RECIPE_LIST, templateId, null, Collections.emptyMap(), slotToRecipeId);
        }

        private static GuiHolder recipe(String templateId, String recipeId) {
            return new GuiHolder(SessionType.RECIPE, templateId, recipeId, Collections.emptyMap(), Collections.emptyMap());
        }

        private static GuiHolder confirmTemplate(String templateId) {
            return new GuiHolder(SessionType.CONFIRM_DELETE_TEMPLATE, templateId, null, Collections.emptyMap(), Collections.emptyMap());
        }

        private static GuiHolder confirmRecipe(String templateId, String recipeId) {
            return new GuiHolder(SessionType.CONFIRM_DELETE_RECIPE, templateId, recipeId, Collections.emptyMap(), Collections.emptyMap());
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private enum SessionType {
        LIST,
        TEMPLATE,
        RECIPE_LIST,
        RECIPE,
        CONFIRM_DELETE_TEMPLATE,
        CONFIRM_DELETE_RECIPE
    }

    private enum AmountType {
        INPUT,
        OUTPUT
    }

    private record AmountInputState(String templateId, String recipeId, AmountType type) {
    }
}
