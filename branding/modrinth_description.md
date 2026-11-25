# Leapfrog

**Play on 26.3 servers like DonutSMP with a 1.21.11 Fabric client, and see the real 26.x content.**

When a 1.21.11 client joins a 26.3 server through ViaBackwards, every new block, item and mob is
replaced by an old stand-in: cushions become floating carpets, wool stairs become quartz stairs,
sulfur cubes become slimes. Leapfrog puts the real thing back, on your screen only.

## What you get

- **Cushions**: real 3D model and colour, correct sitting height and hitbox, real sounds
  (place, sit, get up, break).
- **115 blocks from 26.x** with their exact shapes, light, waterlogging and sounds: wool and
  concrete stairs and slabs, poplar wood and leaves, sulfur, cinnabar, potent sulfur, sulfur
  spikes, golden dandelion, straw bed and more. Placing one shows the right block right away.
- **153 items** with their real model, texture and name (English and French).
- **Baby mobs**: the new 26.x baby models and textures (48 models: cow, pig, chicken, sheep, cat,
  wolf, fox, panda, llama, goat, horse, villager, zombie, piglin...).
- **Sulfur cubes**: the translucent cube with its core, instead of a slime.
- **Geysers** on potent sulfur: bubbles, toxic gas, eruption plume, sounds and the upward push of a
  26.3 client.
- **151 new sounds**: baby animals, mob variants, sulfur cube, trumpet note blocks and more.
- **Redrawn textures** from 26.x, which you can switch off.

Everything can be toggled in the settings screen (**K** key, or the Mod Menu button).

## How it connects

Leapfrog needs **ViaFabric**, **ViaVersion** and **ViaBackwards** (keep ViaVersion and ViaBackwards
on the same version). Your client then joins **DonutSMP** (`donutsmp.net` and its subdomains) as a
26.3 client automatically. You can add other servers to the list. On every other server, ViaFabric
is turned off by default, so your game behaves normally there.

## First launch

Leapfrog does **not** ship any Mojang files. On first launch it downloads the official 26.3 client
from Mojang's servers, checks it, keeps only the textures, models, sounds and names it needs in
`.minecraft/leapfrog/26.3/` (about 12 MB) and deletes the rest. This takes a few seconds during the
loading screen and only happens once. Without an internet connection, the game starts without the
26.x visuals and tries again next launch.

## Fair play

Leapfrog is visual and audio only. It shows no hidden information and sends nothing a normal client
would not send. The only movement-related feature is the geyser push, which copies what a vanilla
26.3 client does and only exists on 26.3 connections; **safe mode** turns it off. Check your server's
rules if in doubt.

## Not restored (yet)

Signs and hanging signs of poplar wood (shown as birch to keep their text), 26.x music discs, new
explorer map icons, and 4 babies whose skeleton was rebuilt from scratch (axolotl, donkey, mule,
rabbit).

---

Client-side only. Not affiliated with Mojang, Microsoft or DonutSMP.
