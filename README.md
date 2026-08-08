# Chunk Boomerits + Economy + AH + Shop + Sell

**Paper / Purpur 1.21.11 — jar `1.7.2`**

## Install
1. Delete ALL old ChunkBoomerits jars from `plugins/`
2. Put `dist/ChunkBoomerits-1.7.2.jar` in `plugins/`
3. **Fully restart** (not `/reload`)
4. Keep `plugins/ChunkBoomerits/` when updating (shop/AH/economy/sell prices)

## Sell (DonutSMP-scale prices)
```
/sell            # sell item in hand
/sell all        # sell all sellable items in inventory
/sell price      # check fixed price of held item
/sell fill       # OP — add any missing item/block prices
/sell regenerate # OP — rebuild ALL prices (overwrites customs)
/sell reload     # OP — reload sell-prices.yml
```
DonutSMP-scale base prices (examples): oak log `$300`, sand `$100`, diamond `$1,200`, leather `$10,000`, ancient debris `$1,700,000`. Updating the jar auto-upgrades `sell-prices.yml` to this table.

## Auction / Shop / Economy
```
/ah  /ah sell <price>
/shop  /shopadd <price>
/bal  /pay  /eco  /baltop
```
