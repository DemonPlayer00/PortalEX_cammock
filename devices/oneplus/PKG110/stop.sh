#!/system/bin/sh
for p in /proc/[0-9]*; do
    exe=$(readlink "$p/exe" 2>/dev/null) || continue
    case "$exe" in
        */cammock/current/provider|*/cammock/current/provider\ \(deleted\)) kill -9 "$p" 2>/dev/null || true ;;
    esac
done
setprop ctl.start vendor.camera-provider
sleep 4
setprop ctl.restart cameraserver
echo "cammock provider stopped; vendor provider restored"
