# Leapfrog

Mod Fabric côté client pour Minecraft 1.21.11. Sur un serveur 26.3 comme DonutSMP, ViaBackwards remplace tout le contenu récent par des blocs et des mobs de la 1.21.11 (un cushion devient un tapis qui flotte, des wool stairs deviennent des quartz stairs...). Leapfrog remet le vrai contenu, uniquement sur ton écran.

Page Modrinth : https://modrinth.com/mod/leapfrog

## Installation

Dans `mods/` : `leapfrog-<version>.jar`, Fabric API, ViaFabric, ViaVersion et ViaBackwards (ViaVersion et ViaBackwards dans la même version).

Les réglages s'ouvrent avec la touche K, ou depuis Mod Menu. Ils sont enregistrés dans `config/leapfrog.json`.

## Ce que le mod restaure

Sur DonutSMP (`donutsmp.net` et ses sous-domaines), le client se connecte en 26.3 tout seul. La liste des serveurs se modifie dans les réglages. Ailleurs, ViaFabric est coupé par défaut.

| 26.3 | Ce que le serveur envoie | Avec Leapfrog |
|---|---|---|
| Cushion | tapis sans gravité nommé « Cushion » | vrai modèle 3D avec sa couleur |
| S'asseoir sur un cushion | assis 0,98 bloc trop haut | hauteur et hitbox de la 26.3 |
| Sons du cushion | sons de laine | vrais sons |
| 153 items | item proche + nom « 26.3 ... » | vrai modèle, texture et nom |
| 115 blocs (5 676 états) | blocs existants | vrais blocs : forme, lumière, eau, sons |
| Bébés animaux | ancien modèle | nouveaux modèles et textures (48 modèles) |
| Sulfur cube | slime | cube translucide avec son cœur |
| Geysers (potent sulfur) | rien | bulles, gaz, panache, sons et poussée vers le haut |
| 151 nouveaux sons | sons de remplacement | vrais sons |
| Textures redessinées | anciennes textures | textures 26.3, désactivables |

Pas encore restaurés : panneaux en poplar (affichés en birch pour garder le texte), musiques 26.x, icônes des nouvelles cartes, et les bébés axolotl, âne, mule et lapin dont le squelette a été refait.

Si tu coupes la connexion 26.3 dans les réglages, le serveur fait la traduction lui-même. Les cushions, items, bébés et sulfur cubes marchent encore, mais les blocs posés restent des remplaçants : ils arrivent sans marqueur, impossible de les retrouver.

## Fichiers du jeu 26.3

Le jar ne contient aucun fichier de Mojang. Au premier lancement, le mod télécharge le client 26.3 chez Mojang (vérifié par SHA-1), garde ce dont il a besoin dans `.minecraft/leapfrog/26.3/` et supprime le reste. Sans connexion, le jeu démarre sans les visuels 26.x et réessaie au lancement suivant.

## Règles de DonutSMP

Les règles (rules.donutsmp.net) interdisent les clients modifiés qui donnent un avantage et les mods de déplacement. Rien sur les mods visuels ni sur ViaFabric. Leapfrog ne montre aucune information cachée et n'envoie rien de plus qu'un client normal. La seule chose qui touche au mouvement est la poussée des geysers, qui fait exactement comme un client 26.3. Le mode sûr la coupe. Dans le doute, demande au support.

## Compiler et tester

```
gradlew build
```

Le jar sort dans `build/libs/`.

Certaines données viennent du jeu 26.3, extraites avec les outils de `tools/` : `future_blocks.json` (BlockDump), `block_sounds.json` (SoundDump), `future_block_mining.json` (MiningDump), `baby_layers.json` (LayerDump).

- `gradlew runClientGameTest` lance un vrai client qui vérifie les cushions, items, sons, geysers, bébés, sulfur cubes et l'interface. Les captures sont dans `build/run/clientGameTest/screenshots/`.
- Contre un serveur 26.3 local (`online-mode=false`, RCON `bttf` sur le port 25598) : `python tools/scene.py`, puis `LEAPFROG_REMOTE=localhost:25599 gradlew runClientGameTest -Pvia_test`.

## Licence

MIT, voir `LICENSE`.
