#!/usr/bin/env bash
# Helper: print the configured SoftAP / join instructions from globals.h
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
G="$ROOT/include/globals.h"

ssid=$(grep -E '^\s*#define WIFI_SSID' "$G" | head -1 | sed 's/.*"\(.*\)".*/\1/')
pass=$(grep -E '^\s*#define WIFI_PASS' "$G" | head -1 | sed 's/.*"\(.*\)".*/\1/')
port=$(grep -E '^\s*#define PORT' "$G" | awk '{print $3}')
players=$(grep -E '^\s*#define MAX_PLAYERS' "$G" | awk '{print $3}')
view=$(grep -E '^\s*#define VIEW_DISTANCE' "$G" | awk '{print $3}')

if grep -qE '^\s*#define WIFI_SOFTAP' "$G"; then
  mode="SoftAP (ESP32 hotspot)"
  addr="192.168.4.1:${port}"
else
  mode="Station (join your Wi‑Fi)"
  addr="<ESP32-IP>:${port}"
fi

cat <<EOF
ESP32 1-chunk Minecraft — current config
  Players:        ${players}
  View distance:  ${view}  (0 = only the chunk you stand in)
  Wi‑Fi mode:     ${mode}
  SSID / pass:    ${ssid} / ${pass}
  Join address:   ${addr}
  Client:         Minecraft Java 1.21.8 (vanilla)
EOF
