# Chunk Boomerits

**Paper / Purpur 1.21.11 plugin** (also includes a Fabric mod build).

## Paper / Purpur install (your error)

You put a **Fabric** jar in `plugins/`. Paper needs a plugin with `plugin.yml`.

1. Download: `dist/ChunkBoomerits-1.0.0.jar`
2. Put it in your server **`plugins/`** folder (not `mods/`)
3. Restart the server

### Commands (OP)
```
/chunkboomerits
/chunkboomerits 16
/chunkboomerits Steve 16
/give @s chunkboomerits
/give @s chunkboomerits:chunk_boomerits 16
```

Throw the fire-charge-looking item at a chunk to delete it.

> Operator Utilities creative tab only exists on the **Fabric client mod**. On Paper/Purpur use the commands above.

## Fabric install (optional)

`dist/chunk-boomerits-fabric-1.0.0.jar` → client/server **`mods/`** folder with Fabric Loader + Fabric API.

## Build

```bash
# Paper plugin
cd paper-plugin && ./gradlew build

# Fabric mod
./gradlew build
```
