# Chunk Boomerits + Economy + AH + Shop + Sell

**Paper / Purpur 1.21.11 — jar `1.7.4`**

## Install
1. Delete ALL old ChunkBoomerits jars from `plugins/`
2. Put `dist/ChunkBoomerits-1.7.4.jar` in `plugins/`
3. **Fully restart** (not `/reload`)
4. Keep `plugins/ChunkBoomerits/` when updating (shop/AH/economy/sell prices)

## Sell (mid-tier SMP prices)
```
/sell            # sell item in hand
/sell all        # sell all sellable items in inventory
/sell price      # check fixed price of held item
/sell fill       # OP — add any missing item/block prices
/sell regenerate # OP — rebuild ALL prices (overwrites customs)
/sell reload     # OP — reload sell-prices.yml
```
Mid-tier examples: cobble `$1`, oak log `$5`, sand `$2`, iron `$8`, diamond `$100`, ancient debris `$5,000`.

**OP shovel** (`/sbshovel`): Admin hub → **Sell Prices** — browse/search every item, left-click to set price, right-click reset to default, or edit held item.

## Auction / Shop / Economy
```
/ah  /ah sell <price>
/shop  /shopadd <price>
/bal  /pay  /eco  /baltop
```
