# Chunk Boomerits + Economy + AH + Shop + Sell

**Paper / Purpur 1.21.11 — jar `1.8.0`**

## Install
1. Delete ALL old ChunkBoomerits jars from `plugins/`
2. Put `dist/ChunkBoomerits-1.8.0.jar` in `plugins/`
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

## Ban Sword (OP)
```
/banhammer <duration> [player]
```
Gives a **wooden Ban Sword**. Hit a player to ban them (works in creative too).

Durations: `1s` `10s` `30s` `1m` `5m` `1h` `1d` `7d` `1w` `1mo` `1y` `perm`

Examples: `/banhammer 1d` · `/banhammer 5m` · `/banhammer perm`

## Auction / Shop / Economy
```
/ah  /ah sell <price>
/shop  /shopadd <price>
/bal  /pay  /eco  /baltop
```
