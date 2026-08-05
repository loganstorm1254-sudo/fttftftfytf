# ESP32 1-Chunk Minecraft Server

Run a **local Minecraft Java 1.21.8** world on an ESP32 for **one player**, with **view distance = 1 chunk**.

Built on [bareiron](https://github.com/p2r3/bareiron) (GPL-3.0) — a minimal Minecraft protocol server written for tiny devices.

| Setting | Value |
|---|---|
| Target board | **ESP32-C3 SuperMini** (default) |
| Minecraft | Java Edition **1.21.8** (vanilla client) |
| Players | **1** (`MAX_PLAYERS`) |
| View distance | **0** → only the chunk you stand in |
| Default Wi‑Fi | **Station mode** — ESP32 joins your home Wi‑Fi |
| Join address | ESP32’s LAN IP (printed on serial), port **25565** |

Works when your PC is on **Ethernet**: ESP32 joins the same router over Wi‑Fi; you connect Minecraft to the ESP32’s IP on the LAN.

---

## What you need

1. An **ESP32-C3 SuperMini** (or other ESP32 / S3 / C3)
2. A **Wi‑Fi router** the ESP32 can join (**2.4 GHz** — C3 has no 5 GHz)
3. USB-C cable + [PlatformIO](https://platformio.org/) (VS Code extension or CLI)
4. A PC (Ethernet or Wi‑Fi) with **Minecraft Java 1.21.8** (vanilla)

No Arduino sketch — this uses **ESP-IDF** only.

---

## Install PlatformIO (Windows)

`pio` is not built into Windows. Pick **one** option:

### Option A — VS Code (easiest)

1. Install [VS Code](https://code.visualstudio.com/)
2. Extensions → search **PlatformIO IDE** → Install
3. File → Open Folder → this project folder
4. Wait for PlatformIO to finish installing (bottom status bar)
5. Use the PlatformIO toolbar: **Build** (✓) then **Upload** (→), or open a **PlatformIO** terminal and run the `pio` commands below

### Option B — CLI in Command Prompt

1. Install [Python 3](https://www.python.org/downloads/) — check **“Add python.exe to PATH”**
2. Open a **new** Command Prompt and run:

```bat
pip install -U platformio
```

3. If `pio` still isn’t found, either:

```bat
python -m platformio run -e esp32-c3-supermini -t upload
```

or add this to your user **PATH**, then open a new Command Prompt:

```text
C:\Users\Logan\.platformio\penv\Scripts
```

(Replace `Logan` if your Windows username differs.)

**ESP32-C3 SuperMini** uses native USB — usually no CP210x/CH340 driver. Windows should show a COM port like `USB Serial Device` / `USB JTAG`.

---

## Quick start (ESP32-C3 SuperMini on your Wi‑Fi)

### 1. Put your Wi‑Fi credentials in

Create `include/wifi_secrets.h` (copy from `wifi_secrets.h.example`):

```c
#define WIFI_SSID "YourRealNetworkName"
#define WIFI_PASS "YourRealPassword"
```

Use your **2.4 GHz** SSID. This file is gitignored so updates won’t wipe it.
Leave `#define WIFI_SOFTAP` commented out in `include/globals.h` (station mode).

### 2. Put the project somewhere with **no spaces** in the path

ESP-IDF fails if the folder path has spaces. Your Downloads copy often looks like:

```text
C:\Users\Logan\Downloads\fttftftfytf-cursor-esp32-one-chunk-mc-c0be (1)\...
                                                         ^^^ space — breaks the build
```

Move/rename it first, for example in Command Prompt:

```bat
mkdir C:\esp32mc
xcopy /E /I "C:\Users\Logan\Downloads\fttftftfytf-cursor-esp32-one-chunk-mc-c0be (1)\fttftftfytf-cursor-esp32-one-chunk-mc-c0be" C:\esp32mc
cd C:\esp32mc
```

(Adjust the source folder name if yours differs.)

### 3. Flash the firmware

Plug in the SuperMini over USB-C, then:

```bat
python -m platformio run -e esp32-c3-supermini -t upload
python -m platformio device monitor
```

Or if `pio` is on PATH:

```bat
pio run -e esp32-c3-supermini -t upload
pio device monitor
```

**If upload fails / no COM port:** hold **BOOT**, tap **RESET**, release **BOOT**, then run upload again.

**If you already built once and then changed flash settings**, clean first:

```bat
python -m platformio run -e esp32-c3-supermini -t fullclean
python -m platformio run -e esp32-c3-supermini -t upload
```

Other boards: `-e esp32dev` or `-e esp32-s3`.

### 4. Read the ESP32’s IP from serial

When it joins Wi‑Fi you should see something like:

```text
Got IP 192.168.1.42, starting server on port 25565...
Server listening on port 25565...
```

That IP is what you join. (Yours will differ.)

### 5. Join from Minecraft (PC can be Ethernet)

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
| `WIFI_SSID` / `WIFI_PASS` | in `wifi_secrets.h` | **Create that file before flashing** |

After edits: `pio run -e esp32-c3-supermini -t upload`.

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
- ESP32-C3 SuperMini is tight on RAM — keep `VIEW_DISTANCE 0` / `MAX_PLAYERS 1`
- C3 is **2.4 GHz Wi‑Fi only**

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
