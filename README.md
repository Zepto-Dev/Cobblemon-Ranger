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
  heads home (despawns) automatically once your next battle is over. You can release friends at any time by sneaking
  and using the styler in the air, or with `/ranger release`. `/ranger friends` lists them.
- **Poké Assists.** During a capture, every friend (and your Partner Pokémon) shows up in a bar under the arena.
  Click one or press its number to call for help. Every type has its own effect:
  Normal makes the line 50% longer; Fire traps the target in a ring of flames; Water lets you blow a bubble (hold
  still to grow it, flick to launch it) that traps the target for longer the bigger it is; Grass grows a patch of tall
  grass that slows it; Electric recharges the styler; Ice turns your strokes into ice walls for a few seconds and
  freezes the target if you box it in; Ground turns your next loop into quicksand it can't leave; Rock turns your next
  stroke into a boulder wall it can't cross; Flying blows attacks away and pins it in a whirlwind; Bug leaves a sticky
  web; Psychic slows it and its attacks; Ghost sends it cowering in a corner; Dark makes its attacks miss; Poison
  makes the gauge fill by itself; Fairy makes the next loops count double; Dragon gives a big chunk of friendship;
  Steel makes the line unbreakable for a while; and Fighting blocks the next hit and stuns the target.
  Assists that act on the target follow the type chart: super effective ones are 50% stronger, resisted ones weaker,
  and one the target is immune to does nothing. Electric, Normal, Steel and Fairy help you, so they always work.
  A friend heads home after assisting.
- **Partner Pokémon.** Send out one of your own Pokémon and use the styler on it to make it your Partner Pokémon.
  While it is in your party it follows you around (and is sent back out after battles and when you log in), helps
  with field moves without leaving, and gains friendship from captures. Its own Poké Assist is 50% stronger than a
  friend's, but first it has to be charged: every loop you draw around a wild Pokémon adds to its meter, carried over
  between captures, and it is ready at 10. Using it empties the meter; picking a new partner starts from zero.
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
  mission. For testing, operators (or single player with cheats) can set a rank with
  `/ranger setrank <1-10> [players]`.
- **Ranger Signs.** Press G (rebindable) with a styler in your inventory and draw a sign in one stroke to call a
  legendary Pokémon. Signs unlock as your rank goes up and cost 10 styler energy: Celebi (circle, rank 1) makes
  plants grow, Suicune (wave, rank 2) heals you and your party, Entei (triangle, rank 3) cooks and smelts dropped
  items lying nearby (your inventory is left alone), Raikou (lightning bolt, rank 4) makes every wild Pokémon within
  64 blocks glow for a minute and names any shinies, Latios (V, rank 5) flies you home, Groudon (M, rank 6) clears
  the weather, Kyogre (spiral, rank 7) brings rain, Cresselia (crescent, rank 8) ends the night, Darkrai (star,
  rank 9) brings the night, and Rayquaza (S drawn upwards, rank 10) launches you into the sky to glide down. Each has
  its own cooldown. The weather and time signs (Groudon, Kyogre, Cresselia, Darkrai) wait an hour per player, and
  once anyone uses one, nobody in the world can use any of them for 20 minutes (`worldSignPersonalCooldownMinutes`
  and `worldSignSharedCooldownMinutes` in the server config). The legendary only appears for a few seconds and
  can't be caught or battled.
- **Styler energy** is the styler's durability. It recharges slowly while carried and can be topped up with
  redstone at an anvil. Stylers never break; an empty one just can't start a capture.
- **Upgrade modules** (Energy, Line Length, Loop Power) can be found in structure chests (dungeons, mineshafts,
  temples, strongholds, ancient cities, shipwrecks, trial chambers and more). Apply one at a smithing table:
  module in the template slot, styler as the base, and a redstone block, string or amethyst shard as the
  material. Each stat goes up to Lv. 5. The upgrade recipes show up in JEI and EMI under smithing.

## Building

Requires Java 21. Run `./gradlew build`; the jar ends up in `build/libs`. `./gradlew runClient` starts a dev
client with Cobblemon and Kotlin for Forge loaded.
