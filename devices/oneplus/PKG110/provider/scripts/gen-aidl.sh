#!/bin/sh
# Generate the C++/NDK AIDL bindings required by portal_camera_provider.
# Source AIDL is pinned in ../aidl-src; generated files stay in aidl/ and are ignored.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
SRC="$ROOT/aidl-src"
OUT="$ROOT/aidl"

if [ -n "${AIDL:-}" ]; then
    AIDL_BIN=$AIDL
elif [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/build-tools/35.0.0/aidl" ]; then
    AIDL_BIN="$ANDROID_HOME/build-tools/35.0.0/aidl"
elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -x "$ANDROID_SDK_ROOT/build-tools/35.0.0/aidl" ]; then
    AIDL_BIN="$ANDROID_SDK_ROOT/build-tools/35.0.0/aidl"
else
    AIDL_BIN=$(command -v aidl 2>/dev/null || true)
fi
if [ -z "${AIDL_BIN:-}" ] || [ ! -x "$AIDL_BIN" ]; then
    echo "aidl not found; set AIDL=/path/to/aidl or ANDROID_HOME" >&2
    exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/head"

# Android 15 / camera AIDL: keep this in sync with the pinned aidl-src tree.
# Use build-tools 35.x: newer aidl emits asBinderReference(), which the pinned
# NDK 27 AIDL headers do not provide.
find "$SRC" -type f -name '*.aidl' | sort > "$OUT/.sources"
# shellcheck disable=SC2046
"$AIDL_BIN" --lang=ndk --structured --stability=vintf --version 2 \
    -I "$SRC" -o "$OUT/gen" -h "$OUT/head" $(cat "$OUT/.sources")
rm -f "$OUT/.sources"
echo "generated AIDL: $OUT/head and $OUT/gen"
