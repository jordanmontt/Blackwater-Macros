#!/usr/bin/env bash
# Regenerates every app/web icon from scripts/logo/render.html (headless Chrome).
# Usage: scripts/logo/render-icons.sh   (needs Google Chrome and Python 3 + Pillow)
set -euo pipefail
cd "$(dirname "$0")/../.."
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
PAGE="file://$PWD/scripts/logo/render.html"
OUT="$(mktemp -d)"
RES=android/app/src/main/res

render() { # mode px file
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --default-background-color=00000000 \
    --virtual-time-budget=5000 --window-size="$2,$2" --screenshot="$OUT/$3" "$PAGE?mode=$1&px=$2" >/dev/null 2>&1
}

for d in mdpi:1 hdpi:1.5 xhdpi:2 xxhdpi:3 xxxhdpi:4; do
  n=${d%%:*}; f=${d##*:}
  fg=$(python3 -c "print(int(108*$f))"); lg=$(python3 -c "print(int(48*$f))")
  render fg "$fg" "fg_$n.png"; render mono "$fg" "mono_$n.png"; render legacy "$lg" "legacy_$n.png"
done
render solo 384 logo_plate.png; render solo 1024 logo.png; render solo 512 icon.png
render solo 192 icon-192x192.png; render solo 512 icon-512x512.png; render maskable 512 icon-maskable-512x512.png

python3 - "$OUT" "$RES" <<'PY'
import sys
from PIL import Image
out, res = sys.argv[1] + "/", sys.argv[2] + "/"
def save(src, dst): Image.open(out + src).save(dst, optimize=True)
for n in ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]:
    save(f"fg_{n}.png", res + f"mipmap-{n}/ic_launcher_foreground.png")
    save(f"mono_{n}.png", res + f"mipmap-{n}/ic_launcher_monochrome.png")
    save(f"legacy_{n}.png", res + f"mipmap-{n}/ic_launcher.png")
    save(f"legacy_{n}.png", res + f"mipmap-{n}/ic_launcher_round.png")
save("logo_plate.png", res + "drawable-nodpi/logo_plate.png")
for f in ["logo.png", "icon-192x192.png", "icon-512x512.png", "icon-maskable-512x512.png"]:
    save(f, "public/" + f)
save("icon.png", "src/app/icon.png")
PY
echo "Icons regenerated."
