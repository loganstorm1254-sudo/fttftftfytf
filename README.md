# Chunk Boomerits + Economy + AH + Shop

**Paper / Purpur 1.21.11 — jar `1.6.1`**

## Install
1. Delete ALL old ChunkBoomerits jars from `plugins/`
2. Put `dist/ChunkBoomerits-1.6.1.jar` in `plugins/`
3. **Fully restart** (not `/reload`)

## Auction
```
/ah                 # or /cbah if /ah is taken
/ah sell <price>    # list item in hand
```

## Shop
```
/shop               # or /cbshop
```
OP add items (no chat needed):
```
# hold a totem, then:
/shopadd 500
```
Or `/sbshovel` → Shop Admin.

## Updating the plugin
Only replace the **`.jar`** in `plugins/`.  
Do **not** delete `plugins/ChunkBoomerits/` — that folder holds:
- `shop.yml` (server shop)
- `auctions.yml` (AH listings)
- `economy.yml` (balances)
- `backups/` (automatic copies on each load)
