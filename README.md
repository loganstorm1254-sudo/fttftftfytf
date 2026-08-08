# Chunk Boomerits + Economy + AH + Shop + Sell

**Paper / Purpur 1.21.11 — jar `1.7.7`**

## Install
1. Delete ALL old ChunkBoomerits jars from `plugins/`
2. Put `dist/ChunkBoomerits-1.7.7.jar` in `plugins/`
3. **Fully restart** (not `/reload`)
4. Keep `plugins/ChunkBoomerits/` when updating (shop/AH/economy/sell prices)

## Sell (DonutSMP-style menu)
```
/sell            # open sell menu — put items in the grid, click green SELL
/sell hand       # sell item in hand
/sell all        # sell all sellable items in inventory
/sell price      # check fixed price of held item
```
4-row GUI: drop items in the empty slots, click the green **SELL** button. Closing returns unsold items.

**OP shovel** (`/sbshovel`): Admin hub → **Sell Prices** — edit every item's `/sell` price.

## Hole Filler (OP)
```
/holefiller          # give the wand
/cbgive holefiller
```
- **Right-click inside a hole/gap** → fills only that cavity up to the surrounding rim (no sky mounds)
- **Sneak + right-click** → switch **HOLE** ↔ **WALL** mode
- **Left-click** → undo last fill

## Auction / Shop / Economy
```
/ah  /ah sell <price>
/shop  /shopadd <price>
/bal  /pay  /eco  /baltop
```
