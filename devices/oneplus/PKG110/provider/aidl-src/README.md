# Pinned camera AIDL sources

这些 AIDL 接口源固定用于 OnePlus provider 的 NDK C++ 绑定生成。

来源：AOSP `hardware/interfaces/camera`、`hardware/interfaces/common`、
`hardware/interfaces/graphics/common`（Android 15 / camera AIDL v3 兼容集）。

生成方式见 `../scripts/gen-aidl.sh`。生成产物 `../aidl/` 被 `.gitignore` 忽略，
不进入源码仓库。不要直接编辑本目录下的 AIDL 文件；升级目标 Android 版本时整体
替换并重新验证 provider 的 AIDL 方法签名。
