# FoConvertors

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/foconvertors) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/foconvertors/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

An editable system for converting configured items into other rewards.

*Developed by Carrotio.*

## Features

- Defines configurable item convertors with controlled inputs and outputs.
- Provides an in-game editor for creating and maintaining convertors.
- Lets administrators issue converter items through commands.
- Supports configurable messages, sounds, and conversion settings.
- Integrates optionally with FoAutoCollect.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/foconvertors). Check the [versions page](https://modrinth.com/plugin/foconvertors/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** `FoAutoCollect`

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/foconvertors <version|editor|give|reload>` — Main command for FoConvertors Aliases: `/convertors`.

### Permissions

- `foconvertors.admin` — Access to FoConvertors admin commands and editor Default: `op`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

This plugin does not register public PlaceholderAPI placeholders.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.

Main config areas: `settings`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

Use the FoConvertors jar as a `provided` Maven dependency (or `compileOnly` in Gradle). Do not shade it into a consumer. For a local Maven build, install the matching final FoConvertors jar in your local repository, then declare:

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoConvertors</artifactId>
  <version>2.3</version>
  <scope>provided</scope>
</dependency>
```

In a consumer's `plugin.yml`, add `softdepend: [FoConvertors]` for optional integration or `depend: [FoConvertors]` if required. Keep direct references to FoConvertors classes in a separate integration class that is loaded only after confirming FoConvertors is installed and enabled. For Paper `paper-plugin.yml` consumers, declare FoConvertors under `dependencies.server` so FoConvertors loads first and its classes are accessible:

```yaml
dependencies:
  server:
    FoConvertors:
      load: BEFORE
      required: false # true if your plugin cannot run without FoConvertors
      join-classpath: true
```

```java
if (Bukkit.getPluginManager().isPluginEnabled("FoConvertors")) {
    RegisteredServiceProvider<FoConvertorsApi> registration =
            Bukkit.getServicesManager().getRegistration(FoConvertorsApi.class);
    if (registration != null) {
        FoConvertorsApi api = registration.getProvider();
        api.createItem("cobble_to_stone").ifPresent(item -> player.getInventory().addItem(item));
        api.inspectItem(player.getInventory().getItemInMainHand())
                .ifPresent(info -> getLogger().info(info.templateId() + ": " + info.enabled()));
        api.requestConversion(player);
    }
}
```

`createItem(id)` uses the template's current default enabled preference; `createItem(id, enabled)` overrides that for the new item only. Both return one new item or empty if the ID is missing. `inspectItem` recognizes the plugin's item metadata even when its template was deleted; `templateAvailable` tells whether that ID is currently loaded. `templateIds`, `template(id)`, and `activeTemplateIds(player)` return detached data. Active means a valid, enabled item in the slots permitted by the current offhand setting. A queued conversion request may later do nothing because no input matches, a pass is already queued, or a cooldown applies. It is not a synchronous conversion command.

`ConvertorConversionAttemptEvent` fires once for a pass with at least one matching recipe batch, before any input is removed. Cancelling it rejects the entire pass. Its immutable entry list contains template and recipe IDs, number of conversions, and cloned input/output item templates and total amounts. With no attempt listeners, FoConvertors uses its direct conversion path to avoid extra planning work. `ConvertorConversionCompletedEvent` fires once after input removal and output deposit/drop. It reports produced amounts, not what remains in the player's inventory. It never fires for a cancelled or empty pass. `ConvertorItemToggledEvent` fires after FoConvertors replaces a held item with its new enabled state; `wasEnabled()` is the previous state and `getItemInfo().enabled()` is the new state. It excludes cooldown attempts and item creation. Events run in the player's owning execution context. On conventional Bukkit this is the primary thread; on regionized servers it can be a region thread, and `isAsynchronous()` reflects whether Bukkit considers the dispatch thread primary.

```java
@EventHandler
public void onConverted(ConvertorConversionCompletedEvent event) {
    for (ConversionEntry entry : event.getEntries()) {
        getLogger().info(event.getPlayer().getName() + " converted "
                + entry.conversions() + " x " + entry.templateId() + "/" + entry.recipeId());
    }
}
```

No API call grants permissions or bypasses the plugin's conversion rules. Use player item and inventory methods only on the player's owning server execution context. Template snapshots reflect current in-memory configuration; a failed save may not survive restart. Conversion events describe inventory changes in this pass, not durable player-data persistence. Template snapshots are read-only copies; editing them does not change FoConvertors data. The API service is registered after startup and unregistered on disable. A cached service returns empty/false after disable. Reacquire it after a disable/re-enable; optional consumers must handle absence.

### Complete public API method index

Signatures use short type names; import Fo types from this plugin's `api` package and Bukkit types from Bukkit.

#### `FoConvertorsApi`

```java
Optional<ConvertorItemInfo> inspectItem(ItemStack);
Optional<ItemStack> createItem(String);
Optional<ItemStack> createItem(String, boolean);
List<String> templateIds();
Optional<ConvertorTemplateSnapshot> template(String);
List<String> activeTemplateIds(Player);
boolean requestConversion(Player);
```

**Other public contract types:** `ConversionEntry`, `ConvertorItemInfo`, `ConvertorRecipeSnapshot`, `ConvertorTemplateSnapshot`.

### Complete Bukkit event index

- `ConvertorConversionAttemptEvent`.
- `ConvertorConversionCompletedEvent`.
- `ConvertorItemToggledEvent`.

</details>
