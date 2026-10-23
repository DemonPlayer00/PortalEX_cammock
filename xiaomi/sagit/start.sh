#!/system/bin/sh
DIR=${0%/*}
. "$DIR/cammock_auth.sh" || exit 1
# MI6 cammock 由 app 显式启动：挂载虚拟 HAL overlay，重启 camera provider。
HALDIR=/vendor/lib/hw
BASE=/cache/portalex-vcam
UPPER=$BASE/upper
WORK=$BASE/work
[ -d "$UPPER" ] || mkdir -p "$UPPER"
[ -d "$WORK" ] || mkdir -p "$WORK"
# 热重载：先摘掉旧的 overlay，确保读到的是真实 HAL，而不是上一轮的 shim。
if mountpoint -q "$HALDIR"; then
    umount "$HALDIR" 2>/dev/null || umount -l "$HALDIR" 2>/dev/null || true
    sleep 1
fi
# 真实 HAL 副本缺失/被错误覆盖（shim 约 300KB，真件约 1MB）时重建。
if [ ! -s "$UPPER/camera.msm8998.orig.so" ] || [ "$(stat -c %s "$UPPER/camera.msm8998.orig.so" 2>/dev/null || echo 0)" -lt 500000 ]; then
    cp -f /vendor/lib/hw/camera.msm8998.so "$UPPER/camera.msm8998.orig.so" || {
        echo "failed to snapshot real HAL"
        exit 1
    }
    [ -f "$DIR/portal.cfg" ] && cp -f "$DIR/portal.cfg" "$UPPER/portal.cfg" 2>/dev/null || true
    chcon u:object_r:vendor_file:s0 "$UPPER/camera.msm8998.orig.so" 2>/dev/null || true
fi
if ! mountpoint -q "$HALDIR" || ! mount | grep -q "overlay.*$HALDIR"; then
    mount -t overlay overlay -o "lowerdir=$HALDIR,upperdir=$UPPER,workdir=$WORK" "$HALDIR" || {
        echo "failed to mount HAL overlay"
        exit 1
    }
fi
setprop ctl.stop vendor.camera-provider-2-4
sleep 1
setprop ctl.start vendor.camera-provider-2-4
setprop ctl.restart cameraserver
# cammock 自己的视频帧生产者（独立子项目 APK，不属于 PortalEX）
PRODUCER_PKG=moe.fuqiuluo.cammockproducer
if [ -f "$DIR/producer.apk" ]; then
    pm install -r "$DIR/producer.apk" >/dev/null 2>&1 || true
    PUID=$(stat -c %u /data/data/$PRODUCER_PKG 2>/dev/null)
    if [ -n "$PUID" ]; then
        mkdir -p /data/data/$PRODUCER_PKG/files
        printf '%s\n' "$PORTALEX_CAMMOCK_TOKEN" > /data/data/$PRODUCER_PKG/files/auth.token
        chown -R "$PUID:$PUID" /data/data/$PRODUCER_PKG/files
        chmod 700 /data/data/$PRODUCER_PKG/files
        chmod 600 /data/data/$PRODUCER_PKG/files/auth.token
        VIDEO=$(ls /data/local/tmp/portalex/cammock/library/*.mp4 2>/dev/null | head -1)
        am force-stop $PRODUCER_PKG 2>/dev/null || true
        if [ -n "$VIDEO" ]; then
            am start -n "$PRODUCER_PKG/.ProducerActivity" --es video "$VIDEO" --es token "$PORTALEX_CAMMOCK_TOKEN" >/dev/null 2>&1 || true
        fi
    fi
fi
for i in $(seq 1 20); do
  dumpsys media.camera 2>/dev/null | grep -q "Number of camera devices: [1-9]" && break
  sleep 0.5
done
echo "MI6 cammock started"
