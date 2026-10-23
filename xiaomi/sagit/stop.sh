#!/system/bin/sh
DIR=${0%/*}
. "$DIR/cammock_auth.sh" || exit 1
# 显式停止：撤销 overlay，重启 vendor provider，回到真实相机。
HALDIR=/vendor/lib/hw
setprop ctl.stop vendor.camera-provider-2-4
sleep 1
nsenter -t 1 -m -- umount "$HALDIR" 2>/dev/null || nsenter -t 1 -m -- umount -l "$HALDIR" 2>/dev/null || true
sleep 1
setprop ctl.start vendor.camera-provider-2-4
setprop ctl.restart cameraserver
/system/bin/su -c 'am force-stop moe.fuqiuluo.cammockproducer' >>/data/local/tmp/portalex/cammock-start.log 2>&1 || true
for i in $(seq 1 20); do
  dumpsys media.camera 2>/dev/null | grep -q "Number of camera devices: [1-9]" && break
  sleep 0.5
done
echo "MI6 cammock stopped; real camera restored"
