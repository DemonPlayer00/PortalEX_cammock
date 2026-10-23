# PortalEX cammock

PortalEX 的外置相机模拟实现集合。PortalEX 主仓库只负责调用、安装和 UI；
实际接管相机栈的 provider / HAL / producer 都在这里，并按品牌和设备隔离。

```text
camera-hal/            通用 JPEG 编解码与 VVideo 源码
devices/
  oneplus/
    PKG110/            OnePlus AIDL provider 实现
  xiaomi/
    sagit/             小米 MI 6 HAL-overlay / root 模块实现
      camera-hal/      MI6 camshim / vcam / GPU 渲染源码
    camera-hal-module/ 旧 root 模块骨架（冻结归档）
producer/              视频解码与共享帧生产者（供 sagit 使用）
interface/             标准帧文件与控制接口协议
build/                 已组装实现的构建工作区
dist/                  可直接导入 PortalEX 的实现 zip
```

## 与 PortalEX 的边界

PortalEX 不包含任何设备专属相机代码。它只持有：

- `CammockManager`：导入、更新、卸载实现；
- 标准脚本调用：`start.sh`、`stop.sh`、`upload.sh`、`download.sh`、`export.sh`；
- 鉴权握手：`auth.token` + 一次性 `auth.nonce`，通过环境变量
  `PORTALEX_CAMMOCK_TOKEN` / `PORTALEX_CAMMOCK_NONCE` 传给脚本；
- app 私有实现目录 `files/cammock/current/`；
- 共享帧文件 `/data/local/tmp/portalex/cammock.frames` 的协议。

设备专属的 provider、HAL、producer、方向/镜像校正、颜色通道修正、root 模块安装
全部留在 cammock 仓库。新增设备只需新增实现包，不应修改 PortalEX 主仓库。

## 实现包格式

实现包是一个 zip，至少包含：

```text
cammock.json
start.sh
stop.sh
```

`cammock.json` 示例：

```json
{
  "name": "Example camera cammock",
  "supportedModels": ["DEVICE_CODENAME"],
  "entry": "provider"
}
```

字段：

| 字段 | 说明 |
|:--|:--|
| `name` | 显示名称 |
| `supportedModels` | `Build.MODEL` / `Build.DEVICE` / `Build.PRODUCT` 匹配列表 |
| `entry` | 设备侧入口文件，例如 `provider` 或 HAL `.so` |
| `type` | 可选；`magisk-module` 表示 root 模块实现 |
| `moduleId` | 可选；root 模块 id |
| `installer` / `uninstaller` | 可选；模块安装/卸载脚本 |
| `videoCorrection` | 可选；该设备实现使用的视频预处理约定 |

`videoCorrection` 由设备实现解释，不跨设备复用。OnePlus PKG110 当前记录：

```json
"videoCorrection": {
  "rotate": "left",
  "mirror": "horizontal",
  "sourceFlipVertical": true,
  "applyOnce": true
}
```

这里描述的是**源视频空间**的变换。源视频先旋转 90° 再进入 provider 时，
显示空间的水平镜像对应源空间的垂直翻转；不要把源空间 `hflip` 直接当作
显示空间水平镜像。

可选接口脚本：

```text
upload.sh     接收 PortalEX 生成的 io/upload/ 快照
download.sh   写出 io/download/routes.json 与视频文件
export.sh     导出 io/export/<videoId>.mp4
```

## 鉴权

PortalEX 导入实现时会重新生成：

```text
files/cammock/current/auth.token
```

每次调用脚本前生成一次性：

```text
files/cammock/current/auth.nonce
```

脚本必须通过 `cammock_auth.sh` 校验两者；未通过鉴权的调用应直接失败。
不要信任实现 zip 内自带的 token，也不要复用 nonce。

## 实现说明

### OnePlus PKG110

```text
devices/oneplus/PKG110/
  provider             AIDL camera provider
  meta.bin / tags.bin  相机元数据
  start.sh             启动 provider、重启 cameraserver
  stop.sh              停止 provider、恢复厂商 provider
  cammock.json          supportedModels + videoCorrection
```

启动后 `camera devices` 由厂商的 6 个变为 1 个虚拟相机；停止后恢复厂商相机。

### Xiaomi MI 6 / sagit

```text
devices/xiaomi/sagit/
  camera-hal/                              MI6 camshim / vcam / GPU 渲染源码
    CMakeLists.txt
    src/camshim.c
    src/vcam.c
    src/gpu_render.c
  system/vendor/lib/hw/camera.msm8998.so   构建生成的 camshim 载荷（不跟踪）
  producer.apk                             构建生成的帧生产者（不跟踪）
  start.sh / stop.sh                       挂载/卸载 overlay，重启 provider
  portal.cfg                               HAL 参数
```

设备运行时的 `camera.msm8998.orig.so` 是根模块部署时从厂商 HAL 复制出来的副本，
不属于仓库内容。

这是 root 模块形态：PortalEX 把实现包交给 root 管理器安装，由实现的脚本负责
overlay、SELinux、帧文件权限和 producer 生命周期。

MI 6 的方向、UV 顺序、照片镜像等修正只存在于：

```text
devices/xiaomi/sagit/portal.cfg
devices/xiaomi/sagit/camera-hal/src/camshim.c
producer/.../ProducerActivity.java
```

不要把这些字段或行为复制到 OnePlus 实现；两台设备使用不同的相机栈和校正链路。

## 构建与打包

> [!note]
>
> 仓库**只跟踪源码、脚本和配置**。`.so`、`.apk`、`.zip` 等构建产物由本地构建或
> Release Artifact 提供，不提交进 Git；`.gitignore` 已覆盖这些文件。
> 克隆仓库后首次使用需要先按下面的步骤构建载荷。

### OnePlus

```bash
# 组装实现包（provider 为预编译执行体）
cd build/oneplus-PKG110
zip -qr ../../dist/portalex-cammock-oneplus-PKG110-implementation.zip .
```

> [!note]
>
> 当前仓库保存了 OnePlus provider 源码和包结构，但没有提交 AIDL 生成文件与预编译
> `provider` 二进制。`build/oneplus-PKG110/` 中的 provider 是本机构建产物；从干净克隆
> 重新编译 OnePlus provider 需要先补齐 AIDL 生成环境。只做包验证时可直接使用该本地
> 构建产物。

### Xiaomi / producer

```bash
# 1. 构建 producer APK
cd producer
./gradlew :app:assembleDebug

# 2. 构建 camshim（armeabi-v7a）并把产物放入 sagit 载荷目录；
#    具体 NDK/Gradle 入口由 camera-hal 构建环境提供。
#    devices/xiaomi/sagit/camera-hal/src/camshim.c -> camera.msm8998.so

# 3. 组装模块 zip（producer.apk 与 camera.msm8998.so 均为本地构建产物）
cd ../devices/xiaomi/sagit
zip -qr ../../../dist/portalex-cammock-sagit-module.zip . \
  -x 'camera-hal/*' 'README.md' 
```

生成的 zip 通过 PortalEX 的 Camera Mock 页面导入：

```text
重导 / 更新 → 选择 zip → 启动摄像头模拟
```

## 测试检查

启动后：

```bash
dumpsys media.camera | head -7
```

应看到实现声明的虚拟相机数量。停止后应恢复厂商相机数量。

OnePlus 启动/停止日志：

```text
/data/local/tmp/portalex/cammock-start.log
```

脚本失败时先检查：

1. `auth.token` / `auth.nonce` 是否存在且权限正确；
2. provider 是否可执行、架构是否匹配；
3. root 是否可用；
4. `camera devices` 是否在 provider 重启后恢复；
5. SELinux / overlay / 帧文件权限是否阻止了 HAL。

## 安全与使用范围

- 仅用于学习、研究和自己的设备调试；
- 不要用于绕过身份验证、考勤、实名验证或其他侵害他人权益的场景；
- 安装 root 模块前确认设备可恢复，保留原厂镜像与卸载路径；
- 实现包只从可信来源导入；`start.sh` / `stop.sh` 会以 root 执行。
