#!/system/bin/sh
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
echo "MI6 cammock started"
