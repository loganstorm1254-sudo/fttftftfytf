# Chunk Boomerits + Economy + AH + Shop + Sell

**Paper / Purpur 1.21.11 — jar `1.7.0`**

## Install
1. Delete ALL old ChunkBoomerits jars from `plugins/`
2. Put `dist/ChunkBoomerits-1.7.0.jar` in `plugins/`
3. **Fully restart** (not `/reload`)
4. Keep `plugins/ChunkBoomerits/` when updating (shop/AH/economy/sell prices)

## Sell (DonutSMP-style)
```
/sell            # sell item in hand
/sell all        # sell all sellable items in inventory
/sell price      # check fixed price of held item
```
Prices are fixed per material in `plugins/ChunkBoomerits/sell-prices.yml` (same item = same price always).

## Auction / Shop / Economy
```
/ah  /ah sell <price>
/shop  /shopadd <price>
/bal  /pay  /eco  /baltop
```
