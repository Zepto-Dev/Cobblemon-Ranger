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
- **Friend Pokémon.** A captured Pokémon joins your party as a temporary friend so it can battle with you. It
  returns to the wild automatically once your next battle is over. You can release friends at any time by sneaking
  and using the styler in the air, or with `/ranger release`. `/ranger friends` lists them.
- **Poké Assists.** During a capture, every friend (and your Partner Pokémon) shows up in a bar under the arena.
  Click one or press its number to call for help; the effect depends on its type. Fire, Electric and Ice stun the
  target, Grass and Bug slow it, Water washes away its attacks, Psychic, Ghost and Dark stop it attacking, Poison,
  Fairy and Dragon make loops stronger, Flying blows attacks away, and the rest give the friendship gauge a boost.
  A friend goes back to the wild after assisting; the Partner Pokémon can help once per capture and stays.
- **Partner Pokémon.** Send out one of your own Pokémon and use the styler on it to make it your Partner Pokémon.
  While it is in your party it follows you around (and is sent back out after battles and when you log in), makes
  every Poké Assist 50% stronger, helps with field moves without leaving, and gains friendship from captures.
  `/ranger partner` shows it and `/ranger partner clear` removes it.
- **Field Moves.** Use the styler on an obstacle and a friend or your Partner Pokémon clears it: **Cut** fells a whole
  tree or clears leaves, vines and cobwebs (Grass, Bug, Steel or Dark types); **Rock Smash** breaks a 3x3x3 chunk of
  stone, cobblestone, sandstone or gravel (Fighting, Rock, Ground or Steel); **Soak** puts out fires and campfires
  and cools lava into obsidian (Water or Ice); **Surf** on water lets you ride along the surface in the direction
  you look for two minutes (Water). Pokémon that know the move itself can help too. A friend goes home afterwards;
  the Partner Pokémon stays but costs a little styler energy. Block protection from other mods is respected.
- **Styler levels.** Captures and missions give the styler XP. Each level (up to Lv. 20) adds energy, line length
  and loop power on top of the upgrade modules, and levelling up refills the styler.
- **Ranger HQ and missions.** Missions come from Rangers: villagers who work at a **Ranger HQ Terminal** (crafted
  from three glass panes, four iron ingots, redstone and an emerald, or found in the Ranger HQ houses that spawn in
  plains, desert, savanna, snowy and taiga villages). Use a styler on a Ranger to be offered a mission: capture
  Pokémon (of a certain type), clear obstacles with field moves, use Poké Assists, battle alongside friends, or bring
  supplies. Accept it from the chat, then report back to the same Ranger for emeralds, styler XP, Ranger points and
  sometimes an upgrade module. After handing one in you wait a Minecraft day (20 minutes, `missionCooldownMinutes`
  in the server config) before any Ranger offers you another.
  Completed missions level the Ranger up (Novice to Master; 2, 4, 4 and 4 missions per level), and each level unlocks
  harder missions with bigger rewards and new trades (redstone, a styler, then the upgrade modules). The mission
  level is also capped by your rank: rank 1 gets Novice missions, rank 2 Apprentice, rank 4 Journeyman, rank 6
  Expert and rank 8 Master. Ranger points raise your rank (1 to 10), shown at the top left while you hold a styler
  along with your mission. `/ranger mission` and `/ranger rank` show your progress; `/ranger mission abandon` drops a
  mission.
- **Ranger Signs.** Press G (rebindable) with a styler in your inventory and draw a sign in one stroke to call a
  legendary Pokémon. Signs unlock as your rank goes up and cost 10 styler energy: Celebi (circle, rank 1) makes
  plants grow, Suicune (wave, rank 2) heals you and your party, Entei (triangle, rank 3) burns hostile mobs, Raikou
  (lightning bolt, rank 4) strikes them with lightning, Latios (V, rank 5) flies you home, and Groudon (M, rank 6)
  clears the weather. Each has its own cooldown.
- **Styler energy** is the styler's durability. It recharges slowly while carried and can be topped up with
  redstone at an anvil. Stylers never break; an empty one just can't start a capture.
- **Upgrade modules** (Energy, Line Length, Loop Power) can be found in structure chests (dungeons, mineshafts,
  temples, strongholds, ancient cities, shipwrecks, trial chambers and more). Apply one at a smithing table:
  module in the template slot, styler as the base, and a redstone block, string or amethyst shard as the
  material. Each stat goes up to Lv. 5.

## Building

Requires Java 21. Run `./gradlew build`; the jar ends up in `build/libs`. `./gradlew runClient` starts a dev
client with Cobblemon and Kotlin for Forge loaded.
