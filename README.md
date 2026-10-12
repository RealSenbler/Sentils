# Sentils

Client-side helper features for Hypixel SkyBlock, for Fabric.

## Requirements

- Minecraft 26.2 with Fabric Loader 0.19.3 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

Put all three jars in your `mods` folder. Sentils will not start without the other two.

## Using it

Open the settings with `/sentils`.

| Command | What it opens |
|---|---|
| `/sentils` | Settings |
| `/sentils notifications` | Chat title triggers |
| `/sentils keybinds` | Command keybinds |
| `/sentils hud` | Move and resize HUD elements |
| `/lab` or `/sentils lab` | Efficiency Lab |

## Features

- **Chat notifications**: show a title on screen when a chat message contains a trigger.
- **Waypoints**: a waypoint, with an optional tracer, for coordinates shared in chat.
- **Command keybinds**: send a command or chat message with one key press.
- **Safari**: sparkling creature alerts, a shard tracker, bell waypoints, and an optional party
  message naming your hotspot biome (off unless you turn it on).
- **Mining**: Metal Detector solver and outlines on Automatons you can see.
- **Dungeons**: highlight for cracked stone bricks.
- **Cold**: a cold timer, and an option to hide the frost overlay.
- **Efficiency Lab**: time a session of farming, mining, fishing, foraging, slayer or dungeons,
  see what it earned per hour, and compare sessions and setups.

The features only run on Hypixel, and in single-player worlds for trying them out.

## Building

`./gradlew build` with a Java 25 JDK. The jar is written to `build/libs`.

## Licence

Sentils is copyright Senbler, all rights reserved; see `LICENSE.txt`. The libraries packed
inside the jar keep their own licences, listed in `THIRD-PARTY-NOTICES.txt`.

Use any mod on Hypixel at your own risk.
