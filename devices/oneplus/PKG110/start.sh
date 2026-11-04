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
# LineageOS 上旧的 provider 可能映射的是已被替换的 inode（readlink 末尾带 deleted），
# 只比较完整路径会漏杀。统一按 cammock/current/provider 前缀清理，并等进程真的退出。
kill_portal_provider() {
    # 不要遍历 /proc：该机实测一次全量 readlink 要 13s，单这一步就足以
    # 触发 CammockManager 的 30s 超时。pgrep 精确匹配 cmdline，几十毫秒完成。
    for p in $(pgrep -f '^/data/.*/cammock/current/provider$' 2>/dev/null); do
        kill -9 "$p" 2>/dev/null || true
    done
    sleep 0.3
}
kill_portal_provider
sleep 1
PORTAL_META="$DIR/meta.bin" PORTAL_TAGS="$DIR/tags.bin" \
PORTAL_INSTANCE=vendor_qti/0 nohup "$PROVIDER" >/data/local/tmp/portal_provider.log 2>&1 &
setprop ctl.restart cameraserver
echo "cammock provider started"
