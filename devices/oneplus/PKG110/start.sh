#!/system/bin/sh
# cammock 执行面：启动 OnePlus AIDL provider。PortalEX 只调用本脚本。
DIR=${0%/*}
mkdir -p /data/local/tmp/portalex 2>/dev/null || true
exec >>/data/local/tmp/portalex/cammock-start.log 2>&1
echo "=== oneplus start $(date) pid=$$ user=$(id -u) arg=$0 ==="
. "$DIR/cammock_auth.sh" || exit 1
PROVIDER="$DIR/provider"
[ -x "$PROVIDER" ] || { echo "provider missing: $PROVIDER"; exit 1; }
setprop ctl.stop vendor.camera-provider
killall portal_camera_provider 2>/dev/null
sleep 1
PORTAL_META="$DIR/meta.bin" PORTAL_TAGS="$DIR/tags.bin" \
PORTAL_INSTANCE=vendor_qti/0 nohup "$PROVIDER" >/data/local/tmp/portal_provider.log 2>&1 &
setprop ctl.restart cameraserver
echo "cammock provider started"
