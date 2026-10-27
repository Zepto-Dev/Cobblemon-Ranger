# CurseForge page

Everything needed for the Cobblemon Ranger project page on CurseForge.

| File | Where it goes |
|---|---|
| `summary.txt` | **Summary** field (201 characters) |
| `description.md` | **Description**, with the editor switched to Markdown |
| `description.html` | Same description as HTML, for the WYSIWYG editor's source view |
| `logo.png` | **Project avatar** (400×400) |
| `screenshots/*.png` | **Images** tab (1920×1080) |

The description embeds screenshots from this folder through `raw.githubusercontent.com/.../main/...`, so they
show up once this folder is on `main`. If you'd rather host them on CurseForge, upload them in the Images tab and
swap the URLs.

Suggested settings: categories *Adventure and RPG* and *Addons*, loader NeoForge, game version 1.21.1, and
required dependencies **Cobblemon** and **Kotlin for Forge**.

## Screenshots

| File | Shows |
|---|---|
| `01-capture-minigame.png` | Drawing a loop around a Pikachu in a meadow arena |
| `02-capture-on.png` | The "Capture On!" banner |
| `03-dodge-attacks.png` | A Slowpoke's projectile attack while looping |
| `04-capture-complete.png` | "Capture Complete!" |
| `05-poke-assist.png` | A Charmander Poké Assist stunning a Bulbasaur |
| `06-cave-arena.png` | Capturing a Zubat in the cave arena |
| `07-friend-pokemon.png` | The new friend Pokémon joining the party |
| `08-partner-pokemon.png` | Choosing Charmander as the Partner Pokémon |
| `09-field-move-cut.png` | The partner felling a cherry tree with Cut |
| `10-field-move-surf.png` | Surfing down a river |
| `11-mission-offer.png` | A farmer offering a Ranger mission |
| `12-mission-tracker.png` | Rank and mission shown on the HUD |
| `13-ranger-sign.png` | Drawing the Celebi Sign |
| `14-stylers.png` | The creative tab with all 16 stylers and the upgrade modules |

Screenshots were taken in a dev client with [Toast Control](https://www.curseforge.com/minecraft/mc-mods/toast-control)
(all toasts blocked) and `tutorialStep:none`, so no tutorial or Cobblemon toasts cover the screen. Toast Control
is not a dependency of the mod.
