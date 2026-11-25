# Leapfrog

Mod Fabric **client** pour Minecraft **1.21.11** : sur un serveur **26.3** (ex. DonutSMP), il affiche et fait entendre le vrai contenu 26.3 à la place des remplaçants que ViaBackwards envoie aux clients 1.21.11.

Réglages (menu en anglais) : touche **K** (ou bouton « Configurer » de Mod Menu). Tout s'enregistre automatiquement dans `config/leapfrog.json`.

## Deux façons de jouer

### 1. Sans connexion 26.3 — le serveur traduit
Connexion normale en 1.21.11 (réglage « Join listed servers as 26.3 » coupé, ou serveur hors liste).

| Contenu 26.3 | Remplaçant envoyé par le serveur | Ce que fait le mod |
|---|---|---|
| Cushion posé | `falling_block` de carpet sans gravité, nommé « Cushion » | vrai modèle 3D, couleur, sans name tag |
| S'asseoir sur un cushion | assis sur un bloc de 0,98 (trop haut) | hauteur et hitbox 26.3 (0,25) |
| Sons du cushion | sons de laine | vrais sons poser / s'asseoir / se lever / casser |
| Items 26.x (153) | item 1.21.11 proche + nom « 26.3 … » | vrai modèle, texture et nom (FR/EN) |
| Poser un bloc 26.x | son du remplaçant | son du vrai bloc |
| Bébés animaux (26.x) | ancien modèle de bébé | **nouveaux modèles et textures 26.x** (48 modèles : vache, cochon, poule, mouton, chat, loup, renard, panda, lama, chèvre, cheval, villageois, zombie, piglin…) |
| Sulfur cube | slime nommé « Sulfur Cube » | vrai cube translucide avec son cœur |
| Textures redessinées en 26.x | — | resource pack intégré « 26.3 redrawn textures » |

Limite : les **blocs posés** arrivent déjà convertis en blocs existants (wool stairs → quartz stairs…) sans marqueur ; le mod ne peut pas les retrouver → mode 2.

### 2. Connexion 26.3 via ViaFabric (par défaut sur DonutSMP)
En plus de tout le mode 1 :
- **115 blocs 26.x restaurés** (5 676 états) : wool/concrete stairs & slabs, poplar, sulfur, cinnabar, potent sulfur, sulfur spike, poplar leaves, golden dandelion, straw bed… avec formes de collision, lumière, eau et **sons** exacts (extraits du jeu 26.3).
- **Couleur des cushions** (perdue par ViaBackwards, récupérée avant traduction).
- **Nouveaux sons 26.x** (151) : bébés animaux, variantes, sulfur cube, note blocks trumpet, poplar leaves, cinnabar…
- **Geysers** (potent sulfur) : bulles, gaz toxique, panache, sons et poussée vers le haut comme un client 26.3.
- **Connexion 26.3 automatique** : uniquement sur `donutsmp.net` / `*.donutsmp.net` (liste modifiable). Sur les autres serveurs, ViaFabric est coupé par défaut (réglage « ViaFabric on other servers »).

Installation : mettre `leapfrog-<version>.jar` et **Fabric API** dans `mods/`. Le mod exige aussi **ViaFabric**, **ViaVersion** et **ViaBackwards** (à télécharger sur Modrinth, ViaVersion et ViaBackwards dans la même version).

Non restauré : panneaux / hanging signs / shelf poplar (restent en birch, pour garder textes et objets), musiques 26.x et disque « bounce », icônes des nouvelles cartes d'exploration, 4 bébés au squelette entièrement refait (axolotl, âne, mule, lapin).

## Anti-cheat et règles (DonutSMP)

- Règles officielles (rules.donutsmp.net) : interdits les clients piratés/modifiés, les **mods de déplacement** et tout avantage injuste. Rien sur les mods visuels ni sur ViaFabric. En cas de doute : demander au support.
- Le mod ne donne aucune information cachée et n'envoie rien de plus qu'un client normal.
- La seule fonction qui agit sur le mouvement est la **poussée des geysers** : elle reproduit le comportement d'un client 26.3 vanilla et n'existe qu'en connexion 26.3. Le **mode sûr** la coupe.
- ViaFabric côté client est un mod de traduction de protocole : à vérifier avec le support si tu veux être certain.

## Compiler

```
gradlew build
```

Jar : `build/libs/leapfrog-<version>.jar`. Les assets 26.3 (textures, modèles, sons) ne sont ni dans le dépôt ni dans le jar : au premier lancement, le mod télécharge le client 26.3 chez Mojang (vérifié par SHA-1), en garde ce qu'il utilise dans `.minecraft/leapfrog/26.3/` et supprime le reste. Il faut une connexion à ce moment-là ; sinon le jeu démarre sans les visuels 26.x et réessaie au lancement suivant.

Données générées depuis le jeu 26.3 (outils dans `tools/`) : `future_blocks.json` (BlockDump), `block_sounds.json` (SoundDump), `baby_layers.json` (LayerDump), `baby_textures.json`, `future_sounds.txt`.

## Tester

- `gradlew runClientGameTest` : client réel ; remplaçants ViaBackwards, sons, assise, geyser, bébés, sulfur cube, captures de l'interface (`build/run/clientGameTest/screenshots/`).
- Bout en bout : serveur 26.3 local (`online-mode=false`, RCON `bttf` sur 25598), `python tools/scene.py`, puis `LEAPFROG_REMOTE=localhost:25599 gradlew runClientGameTest -Pvia_test`.
