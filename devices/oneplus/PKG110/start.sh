#!/system/bin/sh
# cammock 执行面：启动 OnePlus AIDL provider。PortalEX 只调用本脚本。
DIR=${0%/*}
mkdir -p /data/local/tmp/portalex 2>/dev/null || true
exec >>/data/local/tmp/portalex/cammock-start.log 2>&1
echo "=== oneplus start $(date) pid=$$ user=$(id -u) arg=$0 ==="
. "$DIR/cammock_auth.sh" || exit 1
PROVIDER=$(readlink -f "$DIR/provider" 2>/dev/null || echo "$DIR/provider")
[ -x "$PROVIDER" ] || { echo "provider missing: $PROVIDER"; exit 1; }
setprop ctl.stop vendor.camera-provider
# provider 的进程名会被内核截断成 "provider"，killall portal_camera_provider 杀不到；
# 按 /proc/<pid>/exe 精确匹配我们这份二进制，避免旧进程继续占着 camera provider。
for p in /proc/[0-9]*; do
    exe=$(readlink "$p/exe" 2>/dev/null) || continue
    exe=${exe% (deleted)}
    if [ "$exe" = "$PROVIDER" ]; then
        kill -9 "$p" 2>/dev/null || true
    fi
done
sleep 1
PORTAL_META="$DIR/meta.bin" PORTAL_TAGS="$DIR/tags.bin" \
PORTAL_INSTANCE=vendor_qti/0 nohup "$PROVIDER" >/data/local/tmp/portal_provider.log 2>&1 &
setprop ctl.restart cameraserver
echo "cammock provider started"
