# ESP32 1-Chunk Minecraft Server

Run a **local Minecraft Java 1.21.8** world on an ESP32 for **one player**, with **view distance = 1 chunk**.

Built on [bareiron](https://github.com/p2r3/bareiron) (GPL-3.0) — a minimal Minecraft protocol server written for tiny devices.

| Setting | Value |
|---|---|
| Minecraft | Java Edition **1.21.8** (vanilla client) |
| Players | **1** (`MAX_PLAYERS`) |
| View distance | **0** → only the chunk you stand in |
| Default Wi‑Fi | SoftAP hotspot **`ESP32-MC`** / **`minecraft`** |
| Join address | **`192.168.4.1:25565`** |

---

## What you need

1. An **ESP32** board (classic / S3 / C3)
2. USB cable + [PlatformIO](https://platformio.org/) (VS Code extension or CLI)
3. A PC with **Minecraft Java 1.21.8** (vanilla — Fabric/mods often break)

No Arduino sketch — this uses **ESP-IDF** only.

---

## Quick start (SoftAP = “localhost” on the ESP)

### 1. Flash the firmware

```bash
# from this repo
pio run -e esp32dev -t upload
pio device monitor
```

Use `-e esp32-s3` or `-e esp32-c3` if that matches your board (`platformio.ini`).

### 2. Connect your PC to the ESP32 hotspot

| | |
|---|---|
| SSID | `ESP32-MC` |
| Password | `minecraft` |

Serial monitor should print something like:

```text
SoftAP "ESP32-MC" up. Connect Wi‑Fi, then join 192.168.4.1:25565
Server listening on port 25565...
```

### 3. Join from Minecraft

1. Launch **Minecraft Java 1.21.8** (vanilla)
2. Multiplayer → Add Server
3. Address: **`192.168.4.1`** (port `25565` is default)
4. Join — you get one chunk of world around you

That’s it. Your “localhost” is the ESP32 SoftAP at `192.168.4.1`.

---

## Join an existing Wi‑Fi instead

Edit `include/globals.h`:

```c
// Comment this out to use station mode:
// #define WIFI_SOFTAP
#define WIFI_SSID "your-home-ssid"
#define WIFI_PASS "your-home-password"
```

Rebuild/flash. Watch the serial monitor for the ESP32’s IP, then connect Minecraft to `that-ip:25565`.

---

## Try on your PC first (no ESP required)

Registries are already generated in this repo. On Linux:

```bash
./build.sh          # builds and runs on 0.0.0.0:25565
# or:
gcc src/*.c -O2 -Iinclude -o bareiron && ./bareiron
```

Then join `localhost:25565` with Minecraft 1.21.8.

---

## Tuning (`include/globals.h`)

| Macro | Default here | Notes |
|---|---|---|
| `MAX_PLAYERS` | `1` | Keep at 1 on ESP32 |
| `VIEW_DISTANCE` | `0` | `0` = current chunk only; `1` ≈ 3×3 |
| `MAX_BLOCK_CHANGES` | `4096` | Raised builds need more RAM |
| `GAMEMODE` | `0` | `0` survival, `1` creative |
| `WIFI_SOFTAP` | on | SoftAP vs join your LAN |

After edits: `pio run -e esp32dev -t upload`.

---

## Limits (honest)

This is **not** a full vanilla server. Expect:

- Simplified terrain / crafting / items
- No redstone complexity, limited mobs
- Walking far loads the *next* single chunk (still one at a time)
- Vanilla client only — avoid Fabric/Forge for the client
- ESP32-C3 is the tightest; classic ESP32 / S3 are more comfortable

Full upstream docs: https://github.com/p2r3/bareiron

---

## Regenerating registries (only if you change MC version)

Already done for 1.21.8. If you need to redo:

```bash
# needs Java 21+ and Node
mkdir -p notchian
# put official 1.21.8 server.jar into notchian/
./extract_registries.sh
```

---

## License

Server code is based on **bareiron**, licensed under **GNU GPL v3** — see `LICENSE`.
