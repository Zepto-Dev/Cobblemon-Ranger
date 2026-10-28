![Capture minigame](https://raw.githubusercontent.com/Zepto-Dev/Cobblemon-Ranger/main/curseforge/screenshots/01-capture-minigame.png)

# Cobblemon Ranger

**Cobblemon Ranger** brings the Capture Styler from *Pokémon Ranger: Guardian Signs* to [Cobblemon](https://www.curseforge.com/minecraft/mc-mods/cobblemon). Instead of throwing a Poké Ball, right-click a wild Pokémon with your styler and draw loops around it until it trusts you. Your new friend fights by your side, helps you get past obstacles, and then heads back home.

> Requires **Cobblemon** (1.6.1 or newer) and **Kotlin for Forge** on **NeoForge 1.21.1**. Install it on both the client and the server.

---

## 🎯 The capture minigame

Right-click a wild Pokémon with a styler to open the capture screen. Hold left click and draw loops around the Pokémon to fill its friendship gauge. If the Pokémon, or one of its attacks, touches your line, the line breaks and your styler loses energy.

- Attacks depend on the Pokémon's type: charges, projectile bursts, shockwaves and eruptions.
- Rarer, higher-level Pokémon move faster, attack more often and need more loops.
- The arena matches where the Pokémon is standing: meadows, forests, beaches, snow, caves, the Nether and more.

![Dodging attacks](https://raw.githubusercontent.com/Zepto-Dev/Cobblemon-Ranger/main/curseforge/screenshots/03-dodge-attacks.png)

![Capture Complete](https://raw.githubusercontent.com/Zepto-Dev/Cobblemon-Ranger/main/curseforge/screenshots/04-capture-complete.png)

## 🤝 Friend Pokémon

A captured Pokémon joins your party as a temporary **friend** so it can battle with you. It returns to the wild by itself once your next battle is over. You can also send friends home at any time by sneaking and using the styler in the air, or with `/ranger release`.

## ✨ Poké Assists

During a capture, your friends and your Partner Pokémon appear in a bar under the arena. Click one, or press its number, to call for help. What it does depends on its type:

| Type | Effect |
|---|---|
| Fire, Electric, Ice | Stuns the target |
| Grass, Bug | Slows it down |
| Water | Washes away its attacks |
| Psychic, Ghost, Dark | Stops it attacking |
| Poison, Fairy, Dragon | Makes your loops stronger |
| Flying | Blows its attacks away |
| Everything else | Gives the friendship gauge a boost |

A friend goes back to the wild after it assists. Your Partner Pokémon can help once per capture and stays with you.

![Poké Assist](https://raw.githubusercontent.com/Zepto-Dev/Cobblemon-Ranger/main/curseforge/screenshots/05-poke-assist.png)

## ⭐ Partner Pokémon

Send out one of your own Pokémon and use the styler on it to make it your **Partner Pokémon**. While it's in your party it follows you around, makes every Poké Assist 50% stronger, helps with field moves without leaving, and gains friendship from your captures.

## 🌲 Field Moves

Use the styler on an obstacle and a friend or your partner clears it for you:

- **Cut** fells a whole tree or clears leaves, vines and cobwebs (Grass, Bug, Steel or Dark types).
- **Rock Smash** breaks a 3×3×3 chunk of stone, cobblestone, sandstone or gravel (Fighting, Rock, Ground or Steel).
- **Soak** puts out fires and cools lava into obsidian (Water or Ice).
- **Surf** lets you ride across water for two minutes (Water).

Pokémon that know the move itself can help too. Block protection from other mods is respected.

![Cut](https://raw.githubusercontent.com/Zepto-Dev/Cobblemon-Ranger/main/curseforge/screenshots/09-field-move-cut.png)

![Surf](https://raw.githubusercontent.com/Zepto-Dev/Cobblemon-Ranger/main/curseforge/screenshots/10-field-move-surf.png)

## 📜 Ranger HQ, missions and ranks

Missions come from **Rangers**: villagers who work at a **Ranger HQ Terminal**. Craft one, or find a Ranger HQ house in plains, desert, savanna, snowy and taiga villages. Use a styler on a Ranger to be offered a mission: capture Pokémon (sometimes of a certain type), clear obstacles with field moves, use Poké Assists, battle alongside friends, or bring supplies. Report back for emeralds, styler XP, Ranger points and sometimes an upgrade module.

Every mission you complete levels the Ranger up from **Novice to Master**, unlocking harder missions with bigger rewards and new trades. Your Ranger rank caps how hard a mission you can take, so there's no skipping ahead.

Ranger points raise your **Ranger rank** from 1 to 10. Your rank and current mission show at the top left while you hold a styler.

![Mission offer](https://raw.githubusercontent.com/Zepto-Dev/Cobblemon-Ranger/main/curseforge/screenshots/11-mission-offer.png)

## 🌀 Ranger Signs

Press **G** with a styler in your inventory and draw a sign in one stroke to call a legendary Pokémon for help. New signs unlock as your rank goes up:

| Sign | Shape | Rank | Effect |
|---|---|---|---|
| Celebi | Circle | 1 | Makes nearby plants grow |
| Suicune | Wave | 2 | Heals you and your party |
| Entei | Triangle | 3 | Burns hostile mobs |
| Raikou | Lightning bolt | 4 | Strikes hostile mobs with lightning |
| Latios | V | 5 | Flies you home |
| Groudon | M | 6 | Clears the weather |

Each sign costs 10 styler energy and has its own cooldown.

![Ranger Sign](https://raw.githubusercontent.com/Zepto-Dev/Cobblemon-Ranger/main/curseforge/screenshots/13-ranger-sign.png)

## 🎨 Stylers, energy and upgrades

- **16 colours.** Craft a styler from a glass pane, two iron ingots, redstone and a dye (pane on top, iron either side of the redstone, dye underneath). Combine a styler with a dye to recolour it without losing upgrades.
- **Styler energy** is shown as durability. It recharges slowly while you carry the styler and can be topped up with redstone at an anvil. Stylers never break.
- **Styler levels.** Captures and missions give your styler XP. Each level, up to Lv. 20, adds energy, line length and loop power.
- **Upgrade modules** (Energy, Line Length, Loop Power) are found in structure chests: dungeons, mineshafts, temples, strongholds, ancient cities, shipwrecks, trial chambers and more. Apply one at a smithing table with a redstone block, string or amethyst shard.

![Stylers](https://raw.githubusercontent.com/Zepto-Dev/Cobblemon-Ranger/main/curseforge/screenshots/14-stylers.png)

## ⌨️ Commands

| Command | What it does |
|---|---|
| `/ranger friends` | Lists your friend Pokémon |
| `/ranger release` | Sends your friends home |
| `/ranger partner` | Shows your Partner Pokémon (`clear` removes it) |
| `/ranger mission` | Shows your mission (`abandon` drops it) |
| `/ranger rank` | Shows your rank and Ranger Signs |

---

*Cobblemon Ranger is a fan-made add-on and is not affiliated with Nintendo, Game Freak, The Pokémon Company or the Cobblemon team. All art in this mod is original. Source code: [GitHub](https://github.com/Zepto-Dev/Cobblemon-Ranger) (MIT licence).*
