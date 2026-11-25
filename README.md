# Leapfrog

Client-side Fabric mod for Minecraft 1.21.11. On a 26.3 server like DonutSMP, ViaBackwards swaps all the new content for 1.21.11 blocks and mobs (a cushion becomes a floating carpet, wool stairs become quartz stairs...). Leapfrog brings the real content back, on your screen only.

Modrinth: https://modrinth.com/mod/leapfrog

## Installation

Put these in `mods/`: `leapfrog-<version>.jar`, Fabric API, ViaFabric, ViaVersion and ViaBackwards (ViaVersion and ViaBackwards on the same version).

Settings open with the K key, or from Mod Menu. They are saved in `config/leapfrog.json`.

## What it restores

On DonutSMP (`donutsmp.net` and its subdomains) the client joins as 26.3 by itself. The server list can be edited in the settings. Everywhere else, ViaFabric is off by default.

| 26.3 | What the server sends | With Leapfrog |
|---|---|---|
| Cushion | gravity-less carpet named "Cushion" | real 3D model with its colour |
| Sitting on a cushion | seated 0.98 blocks too high | 26.3 height and hitbox |
| Cushion sounds | wool sounds | real sounds |
| 153 items | similar item + "26.3 ..." name | real model, texture and name |
| 115 blocks (5,676 states) | existing blocks | real blocks: shape, light, water, sounds |
| Baby mobs | old model | new models and textures (48 models) |
| Sulfur cube | slime | translucent cube with its core |
| Geysers (potent sulfur) | nothing | bubbles, gas, plume, sounds and the upward push |
| 151 new sounds | stand-in sounds | real sounds |
| Redrawn textures | old textures | 26.3 textures, can be turned off |

Not restored yet: poplar signs (shown as birch to keep their text), 26.x music, the new explorer map icons, and baby axolotls, donkeys, mules and rabbits, whose skeleton was rebuilt.

If you turn off the 26.3 connection in the settings, the server does the translation itself. Cushions, items, babies and sulfur cubes still work, but placed blocks stay as stand-ins: they arrive without any marker, so there is no way to recognise them.

## 26.3 game files

The jar contains no Mojang files. On first launch the mod downloads the 26.3 client from Mojang (checked with SHA-1), keeps what it needs in `.minecraft/leapfrog/26.3/` and deletes the rest. Without a connection the game starts without the 26.x visuals and tries again on the next launch.

## DonutSMP rules

The rules (rules.donutsmp.net) ban modified clients that give an advantage and movement mods. They say nothing about visual mods or ViaFabric. Leapfrog shows no hidden information and sends nothing a normal client wouldn't. The only thing that touches movement is the geyser push, which behaves exactly like a 26.3 client. Safe mode turns it off. If in doubt, ask the staff.

## Building and testing

```
gradlew build
```

The jar ends up in `build/libs/`.

Some data comes from the 26.3 game, extracted with the tools in `tools/`: `future_blocks.json` (BlockDump), `block_sounds.json` (SoundDump), `future_block_mining.json` (MiningDump), `baby_layers.json` (LayerDump).

- `gradlew runClientGameTest` starts a real client that checks cushions, items, sounds, geysers, babies, sulfur cubes and the settings screen. Screenshots go to `build/run/clientGameTest/screenshots/`.
- Against a local 26.3 server (`online-mode=false`, RCON password `bttf` on port 25598): `python tools/scene.py`, then `LEAPFROG_REMOTE=localhost:25599 gradlew runClientGameTest -Pvia_test`.

## License

MIT, see `LICENSE`.
