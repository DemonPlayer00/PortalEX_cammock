#!/usr/bin/env bash
# High-rate preview probe for MI6 cammock: record the system camera, extract frames,
# and report green rows, black frames and horizontal seam metrics.
# Usage: tools/mi6_preview_diag.sh [serial] [seconds]
set -euo pipefail
SERIAL="${1:-48d99b50}"
SECONDS_TO_RECORD="${2:-8}"
WORK="${TMPDIR:-/tmp}/mi6-preview-diag-$$"
mkdir -p "$WORK/frames"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT
DEVICE_MP4="/sdcard/GlaDos_File/preview_diag.mp4"
adb -s "$SERIAL" shell rm -f "$DEVICE_MP4"
echo "recording ${SECONDS_TO_RECORD}s from $SERIAL ..." >&2
# screenrecord exits by itself; keep its stderr visible for failures.
adb -s "$SERIAL" shell screenrecord --size 540x960 --bit-rate 8M \
  --time-limit "$SECONDS_TO_RECORD" "$DEVICE_MP4" >/dev/null
adb -s "$SERIAL" pull "$DEVICE_MP4" "$WORK/preview.mp4" >/dev/null
adb -s "$SERIAL" shell rm -f "$DEVICE_MP4"
ffmpeg -hide_banner -loglevel error -i "$WORK/preview.mp4" -vf fps=30 "$WORK/frames/f_%05d.png"
python3 - "$WORK/frames" <<'PY'
import json, sys
from pathlib import Path
import numpy as np
from PIL import Image

frames = sorted(Path(sys.argv[1]).glob('f_*.png'))
rows = []
for p in frames:
    a = np.asarray(Image.open(p).convert('RGB'))
    # Ignore the status bar; the preview occupies most of the frame.
    h, w, _ = a.shape
    y0, y1 = int(h * 0.06), int(h * 0.64)
    roi = a[y0:y1]
    r, g, b = (roi[:, :, i].astype(np.int16) for i in range(3))
    green = (g > r + 25) & (g > b + 25)
    black = (roi.max(axis=2) < 20)
    row_green = green.mean(axis=1)
    # A horizontal green line is a narrow row with a strong green share.
    green_rows = np.flatnonzero(row_green > 0.35)
    # Seam candidate: unusually large mean adjacent-row difference.
    gray = roi.mean(axis=2)
    seam = np.abs(np.diff(gray, axis=0)).mean(axis=1)
    med = float(np.median(seam)) if seam.size else 0.0
    mx = float(seam.max()) if seam.size else 0.0
    rows.append({
        'frame': p.name,
        'green_frac': float(green.mean()),
        'black_frac': float(black.mean()),
        'green_row_count': int(green_rows.size),
        'green_row_max': int(green_rows.max()) if green_rows.size else -1,
        'seam_max': mx,
        'seam_median': med,
        'seam_ratio': (mx / med) if med > 1e-6 else 0.0,
    })
report = {
    'frames': len(rows),
    'max_green_frac': max((x['green_frac'] for x in rows), default=0),
    'max_black_frac': max((x['black_frac'] for x in rows), default=0),
    'green_line_frames': sum(1 for x in rows if x['green_row_count'] > 0),
    'max_seam_ratio': max((x['seam_ratio'] for x in rows), default=0),
    'worst_green': sorted(rows, key=lambda x: x['green_frac'], reverse=True)[:5],
    'worst_seam': sorted(rows, key=lambda x: x['seam_ratio'], reverse=True)[:5],
}
print(json.dumps(report, indent=2, ensure_ascii=False))
PY
