# ESP32 1-Chunk Minecraft Server

Run a **local Minecraft Java 1.21.8** world on an ESP32 for **one player**, with **view distance = 1 chunk**.

Built on [bareiron](https://github.com/p2r3/bareiron) (GPL-3.0).

| Setting | Value |
|---|---|
| Target board | **ESP32-C3 SuperMini** (default) |
| Minecraft | Java Edition **1.21.8** (vanilla client) |
| Players | **1** |
| View distance | **0** (only the chunk you stand in) |
| Default Wi‑Fi | SoftAP hotspot **`ESP32-MC`** / **`minecraft`** |
| Join address | **`192.168.4.1`** |

---

## What you need

1. ESP32-C3 SuperMini + USB-C cable
2. [PlatformIO](https://platformio.org/) (or `pip install platformio`)
3. PC with Minecraft Java 1.21.8 **and Wi‑Fi** (built-in or USB Wi‑Fi adapter)

---

## Flash + play (SoftAP)

Project path must have **no spaces** (use `C:\esp32mc`).

```bat
cd C:\esp32mc
python -m platformio run -e esp32-c3-supermini -t upload
python -m platformio device monitor
```

Press **RESET**. Serial should say SoftAP ready.

Then on the PC:

1. Join Wi‑Fi **`ESP32-MC`** / password **`minecraft`**
2. Minecraft → Multiplayer → **`192.168.4.1`**

If upload fails: hold **BOOT**, tap **RESET**, release **BOOT**, upload again.

---

## Optional: join home Wi‑Fi instead

Comment out `#define WIFI_SOFTAP` in `include/globals.h`, add `include/wifi_secrets.h` with your 2.4 GHz SSID/password. Many SuperMinis fail on **WPA2/WPA3** mixed routers; SoftAP avoids that.

---

## License

Based on **bareiron**, **GNU GPL v3** — see `LICENSE`.
