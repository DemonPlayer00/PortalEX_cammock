#!/system/bin/sh
# 显式停止：撤销 overlay，重启 vendor provider，回到真实相机。
HALDIR=/vendor/lib/hw
umount "$HALDIR" 2>/dev/null
setprop ctl.stop vendor.camera-provider-2-4
sleep 1
setprop ctl.start vendor.camera-provider-2-4
setprop ctl.restart cameraserver
echo "MI6 cammock stopped; real camera restored"
