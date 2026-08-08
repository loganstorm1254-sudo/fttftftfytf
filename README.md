# Chunk Boomerits + Economy + AH + Shop + Sell

**Paper / Purpur 1.21.11 — jar `1.7.1`**

## Install
1. Delete ALL old ChunkBoomerits jars from `plugins/`
2. Put `dist/ChunkBoomerits-1.7.1.jar` in `plugins/`
3. **Fully restart** (not `/reload`)
4. Keep `plugins/ChunkBoomerits/` when updating (shop/AH/economy/sell prices)

## Sell (DonutSMP-style)
```
/sell            # sell item in hand
/sell all        # sell all sellable items in inventory
/sell price      # check fixed price of held item
/sell fill       # OP — add any missing item/block prices
/sell regenerate # OP — rebuild ALL prices (overwrites customs)
/sell reload     # OP — reload sell-prices.yml
```
Every item and block in the game gets a fixed price in `plugins/ChunkBoomerits/sell-prices.yml` (same material = same price always). On first load (or `/sell fill`), missing materials are auto-added.

## Auction / Shop / Economy
```
/ah  /ah sell <price>
/shop  /shopadd <price>
/bal  /pay  /eco  /baltop
```
