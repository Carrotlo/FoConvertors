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

Compile against the matching jar (`provided` in Maven, `compileOnly` in Gradle); install it locally if needed. Declare `FoConvertors` in your plugin metadata (`softdepend` for an optional integration).

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoConvertors</artifactId>
  <version>2.3</version>
  <scope>provided</scope>
</dependency>
```

```java
import me.foesio.foConvertors.api.FoConvertorsApi;
import me.foesio.foConvertors.event.ConvertorConversionAttemptEvent;

FoConvertorsApi api = Bukkit.getServicesManager().load(FoConvertorsApi.class);
if (api != null) api.templateIds();

@EventHandler
public void onConvertorConversionAttemptEvent(ConvertorConversionAttemptEvent event) {
    Bukkit.getLogger().info(event.getClass().getSimpleName());
}
```

**API methods** (overloads grouped):

- `FoConvertorsApi`: `inspectItem`, `createItem`, `templateIds`, `template`, `activeTemplateIds`, `requestConversion`.

**Other API types:** `ConversionEntry`, `ConvertorItemInfo`, `ConvertorRecipeSnapshot`, `ConvertorTemplateSnapshot`.

**Events:** `ConvertorConversionAttemptEvent`, `ConvertorConversionCompletedEvent`, `ConvertorItemToggledEvent`.

[Browse API source](src/main/java/me/foesio/foConvertors/api) for signatures and event details.

</details>
