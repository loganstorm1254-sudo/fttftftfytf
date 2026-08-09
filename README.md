# Chunk Boomerits + Economy + AH + Shop + Sell

**Paper / Purpur 1.21.11 — jar `1.8.1`**

## Install
1. Delete ALL old ChunkBoomerits jars from `plugins/`
2. Put `dist/ChunkBoomerits-1.8.1.jar` in `plugins/`
3. **Fully restart** (not `/reload`)
4. Keep `plugins/ChunkBoomerits/` when updating (shop/AH/economy/sell prices)

## RTP
```
/rtp              # open distance GUI
/rtp 1000         # skip GUI — go ~1000 blocks away
/rtp 5k           # same as 5000
```
GUI options: **500 · 1,000 · 2,500 · 5,000 · 10,000 · 25,000** blocks away from you.

## Sell (DonutSMP-style menu)
```
/sell            # open sell menu — put items in the grid, click green SELL
/sell hand       # sell item in hand
/sell all        # sell all sellable items in inventory
/sell price      # check fixed price of held item
```

**OP shovel** (`/sbshovel`): Admin hub → **Sell Prices** — edit every item's `/sell` price.

## Ban Sword (OP)
```
/banhammer <duration> [player]
```
Wooden Ban Sword — hit a player to ban them (works in creative).  
Durations: `1s` `1m` `1h` `1d` `7d` `perm`

## Auction / Shop / Economy
```
/ah  /ah sell <price>
/shop  /shopadd <price>
/bal  /pay  /eco  /baltop
```
