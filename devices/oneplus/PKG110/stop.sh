#!/system/bin/sh
# 与 start.sh 同一套精确匹配；不要全量扫描 /proc（实测 >10s）。
for p in $(pgrep -f '^/data/.*/cammock/current/provider$' 2>/dev/null); do
    kill -9 "$p" 2>/dev/null || true
done
setprop ctl.start vendor.camera-provider
sleep 4
setprop ctl.restart cameraserver
echo "cammock provider stopped; vendor provider restored"
