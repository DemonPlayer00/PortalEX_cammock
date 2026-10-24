#!/system/bin/sh
set -eu
# MI6 实现自己的库位置；app 只提供标准 io/upload 快照。
DIR=${0%/*}
. "$DIR/cammock_auth.sh" || exit 1
SRC="$DIR/io/upload"
DST=/data/local/tmp/portalex/cammock/library
[ -f "$SRC/routes.json" ] || { echo "io/upload/routes.json missing"; exit 1; }
rm -rf "$DST"; mkdir -p "$DST"
cp -f "$SRC/routes.json" "$DST/routes.json"
for f in "$SRC"/*.mp4; do
    [ -f "$f" ] || continue
    cp -f "$f" "$DST/" || { echo "copy failed: $f" >&2; exit 1; }
    src_size=$(stat -c %s "$f")
    dst_size=$(stat -c %s "$DST/${f##*/}")
    [ "$src_size" = "$dst_size" ] || { echo "size mismatch: $f ($src_size != $dst_size)" >&2; exit 1; }
done
sync
chmod -R 644 "$DST"/*
echo "MI6 cammock library stored"
