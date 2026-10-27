# Cobblemon Ranger

A NeoForge 1.21.1 add-on for [Cobblemon](https://cobblemon.com) that brings the Capture Styler from
*Pokémon Ranger: Guardian Signs* to Minecraft.

## Features

- **Capture Stylers** in all 16 dye colours. Craft one from a glass pane, two iron ingots, redstone and a dye
  (pane on top, iron either side of the redstone, dye underneath). Combine a styler with a dye to recolour it
  without losing upgrades.
- **Capture minigame.** Right-click a wild Pokémon with a styler to open the capture screen. Hold left click and
  draw loops around the Pokémon to fill its friendship gauge. If the Pokémon, or one of its attacks, touches the
  line, the line breaks and your styler loses energy. Fill the gauge to complete the capture. Attacks depend on
  the Pokémon's primary type (charges, projectile bursts, shockwaves, and eruptions), and rarer, higher-level
  Pokémon are faster, attack more often and need more loops.
- **Partner Pokémon.** A captured Pokémon joins your party as a temporary partner so it can battle with you. It
  returns to the wild automatically once your next battle is over. You can release partners at any time by
  sneaking and using the styler (on the partner, or in the air to release all of them), or with `/ranger release`.
  `/ranger partners` lists them.
- **Styler energy** is the styler's durability. It recharges slowly while carried and can be topped up with
  redstone at an anvil. Stylers never break; an empty one just can't start a capture.
- **Upgrade modules** (Energy, Line Length, Loop Power) can be found in structure chests (dungeons, mineshafts,
  temples, strongholds, ancient cities, shipwrecks, trial chambers and more). Apply one at a smithing table:
  module in the template slot, styler as the base, and a redstone block, string or amethyst shard as the
  material. Each stat goes up to Lv. 5.

## Building

Requires Java 21. Run `./gradlew build`; the jar ends up in `build/libs`. `./gradlew runClient` starts a dev
client with Cobblemon and Kotlin for Forge loaded.
