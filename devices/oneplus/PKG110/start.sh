#!/system/bin/sh
# cammock 执行面：启动 OnePlus AIDL provider。PortalEX 只调用本脚本。
DIR=${0%/*}
mkdir -p /data/local/tmp/portalex 2>/dev/null || true
exec >>/data/local/tmp/portalex/cammock-start.log 2>&1
echo "=== oneplus start $(date) pid=$$ user=$(id -u) arg=$0 ==="
. "$DIR/cammock_auth.sh" || exit 1

# system_server 不能直接 dlopen /data/app 下的 apk_data_file .so（MI6 实测
# “couldn't map segment 1: Permission denied”），复制到可执行的 system_lib_file 路径。
stage_portalsensor() {
    pkg=moe.fuqiuluo.portalex
    apk=$(pm path "$pkg" 2>/dev/null | sed -n 's/^package://p' | head -1)
    [ -n "$apk" ] || return 0
    src="${apk%/base.apk}/lib/arm64/libportalsensor.so"
    [ -f "$src" ] || return 0
    dst=/data/local/tmp/portalex/libportalsensor.so
    mkdir -p /data/local/tmp/portalex 2>/dev/null || true
    # 原子替换：system_server 可能已经 mmap 了旧 inode，不能 cp -f 原地覆盖。
    tmp="$dst.tmp.$$"
    cp -f "$src" "$tmp" || return 0
    chmod 755 "$tmp" 2>/dev/null || true
    chcon u:object_r:system_lib_file:s0 "$tmp" 2>/dev/null || true
    mv -f "$tmp" "$dst" || { rm -f "$tmp"; return 0; }
    echo "portalsensor staged atomically: $dst"
}
stage_portalsensor
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
