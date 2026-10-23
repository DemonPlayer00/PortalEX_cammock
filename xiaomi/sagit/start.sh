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
