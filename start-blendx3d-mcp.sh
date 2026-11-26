#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$HOME/BlendX3D"
command -v node >/dev/null || { echo "Install Node.js: pkg install nodejs"; exit 1; }
command -v cloudflared >/dev/null || { echo "Install cloudflared first, then run this script again."; exit 1; }
if ! curl -fsS http://127.0.0.1:3333/health >/dev/null 2>&1; then
  TOKEN_FILE="$HOME/.blendx3d-mcp-token"
  if [ ! -s "$TOKEN_FILE" ]; then openssl rand -hex 24 > "$TOKEN_FILE"; fi
  export BLENDX_TOKEN="$(cat "$TOKEN_FILE")"
  nohup node server.js > "$HOME/blendx3d-mcp.log" 2>&1 &
  sleep 2
fi
echo "Starting Cloudflare tunnel. Keep Termux open."
LOG="$HOME/blendx3d-cloudflared.log"
cloudflared tunnel --url http://127.0.0.1:3333 2>&1 | tee "$LOG" &
TUNNEL_PID=$!
trap 'kill "$TUNNEL_PID" 2>/dev/null || true' EXIT INT TERM
for i in $(seq 1 90); do
  URL=$(grep -Eo 'https://[a-zA-Z0-9.-]+\.trycloudflare\.com' "$LOG" | head -n 1 || true)
  if [ -n "$URL" ]; then
    printf '{"url":"%s"}' "$URL" | curl -fsS -X POST http://127.0.0.1:3333/public-url -H 'content-type: application/json' --data-binary @- >/dev/null
    echo ""
    echo "BlendX3D tunnel ready: $URL"
    echo "In BlendX3D: Window > AI / MCP Connect > Auto-detect Termux URL."
    wait "$TUNNEL_PID"
    exit 0
  fi
  sleep 1
done
echo "Could not detect a Cloudflare URL. Check $LOG"
exit 1
