# ESP32 1-Chunk Minecraft Server

Run a **local Minecraft Java 1.21.8** world on an ESP32 for **one player**, with **view distance = 1 chunk**.

Built on [bareiron](https://github.com/p2r3/bareiron) (GPL-3.0) — a minimal Minecraft protocol server written for tiny devices.

| Setting | Value |
|---|---|
| Minecraft | Java Edition **1.21.8** (vanilla client) |
| Players | **1** (`MAX_PLAYERS`) |
| View distance | **0** → only the chunk you stand in |
| Default Wi‑Fi | **Station mode** — ESP32 joins your home Wi‑Fi |
| Join address | ESP32’s LAN IP (printed on serial), port **25565** |

Works when your PC is on **Ethernet**: ESP32 joins the same router over Wi‑Fi; you connect Minecraft to the ESP32’s IP on the LAN.

---

## What you need

1. An **ESP32** board (classic / S3 / C3)
2. A **Wi‑Fi router** the ESP32 can join (2.4 GHz — classic ESP32 has no 5 GHz)
3. USB cable + [PlatformIO](https://platformio.org/) (VS Code extension or CLI)
4. A PC (Ethernet or Wi‑Fi) with **Minecraft Java 1.21.8** (vanilla)

No Arduino sketch — this uses **ESP-IDF** only.

---

## Quick start (ESP32 on your Wi‑Fi)

### 1. Put your Wi‑Fi credentials in

Edit `include/globals.h`:

```c
#define WIFI_SSID "YOUR_WIFI_SSID"
#define WIFI_PASS "YOUR_WIFI_PASSWORD"
```

Replace with your real SSID and password. Leave `#define WIFI_SOFTAP` commented out (station mode).

### 2. Flash the firmware

```bash
pio run -e esp32dev -t upload
pio device monitor
```

Use `-e esp32-s3` or `-e esp32-c3` if that matches your board (`platformio.ini`).

### 3. Read the ESP32’s IP from serial

When it joins Wi‑Fi you should see something like:

```text
Got IP 192.168.1.42, starting server on port 25565...
Server listening on port 25565...
```

That IP is what you join. (Yours will differ.)

### 4. Join from Minecraft (PC can be Ethernet)

1. Launch **Minecraft Java 1.21.8** (vanilla)
2. Multiplayer → Add Server
3. Address: **`<ESP32-IP>`** (e.g. `192.168.1.42`)
4. Join — one chunk of world around you

PC and ESP32 just need to be on the **same LAN** (same router). Ethernet on the PC is fine.

---

## Optional: SoftAP hotspot instead

If you want the ESP32 to broadcast its own Wi‑Fi (and your PC can join it):

```c
#define WIFI_SOFTAP
#define WIFI_SSID "ESP32-MC"
#define WIFI_PASS "minecraft"
```

Then join Minecraft at `192.168.4.1:25565`. This needs a Wi‑Fi client on the PC — skip SoftAP if you only have Ethernet.

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
| `WIFI_SOFTAP` | off | Off = join your Wi‑Fi; on = ESP hotspot |
| `WIFI_SSID` / `WIFI_PASS` | placeholders | **Set these before flashing** |

After edits: `pio run -e esp32dev -t upload`.

Check current config anytime:

```bash
./scripts/show-config.sh
```

---

## Limits (honest)

This is **not** a full vanilla server. Expect:

- Simplified terrain / crafting / items
- No redstone complexity, limited mobs
- Walking far loads the *next* single chunk (still one at a time)
- Vanilla client only — avoid Fabric/Forge for the client
- ESP32-C3 is the tightest; classic ESP32 / S3 are more comfortable
- Classic ESP32 is **2.4 GHz Wi‑Fi only**

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
