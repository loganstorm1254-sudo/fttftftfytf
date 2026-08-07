# MineDoom

Play **DOOM** inside Minecraft on a Purpur **1.21.11** server.

Select a wall with the **WorldEdit axe**, place a map screen, then play with your **PC keyboard and mouse** (WASD + look + click) while seated in front of it.

Powered by [PureDOOM](https://github.com/Daivuk/PureDOOM) (real Doom engine) rendering onto item-frame maps.

## Requirements

- Purpur / Paper **1.21.11**
- Java **21**
- [WorldEdit](https://enginehub.org/worldedit) (for placing screens)
- Linux **x86_64** server (ships `libpuredoom.so`)

## Install

1. Build: `./gradlew jar`
2. Copy `build/libs/MineDoom-1.0.0.jar` into `plugins/`
3. Install WorldEdit
4. Restart the server

Optional: drop a full IWAD as `plugins/MineDoom/wads/doom.wad` or `freedoom1.wad` to replace the bundled shareware `doom1.wad`.

## Play

1. `//wand` (or `/doom wand`) — WorldEdit wooden axe  
2. Left-click / right-click two corners of a **flat vertical wall** (one block thick), e.g. **3×2** or **4×3**  
3. `/doom place` — fills the selection with a live Doom map screen  
4. Stand in front → `/doom play`  
5. Use keyboard & mouse:  

| Input | Doom |
|--------|------|
| WASD | Move / strafe |
| Mouse | Look |
| Left click | Fire |
| Right click / Jump | Use / open |
| Sprint | Run |
| Hotbar 1–7 | Weapons |
| Swap hands (F) | Automap |
| `/doom enter` | Enter (menus) |
| `/doom esc` | Escape |
| `/doom stop` | Exit play mode |

## Commands

- `/doom wand` — get WorldEdit axe  
- `/doom place` — create screen from selection  
- `/doom play` — play nearest screen  
- `/doom stop` — stop playing  
- `/doom remove` — remove nearest screen  
- `/doom status` — engine / screen status  

## Notes

- Screen size = selection size in blocks; each block is one 128×128 map tile.  
- Keep screens modest (≤ ~64 maps) for performance.  
- Keyboard/mouse are your normal Minecraft controls while playing — the plugin remaps them into Doom.  
- GPL-2.0 (PureDOOM / Doom source license). Shareware IWAD included; commercial IWADs are not redistributed.
