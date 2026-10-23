# PortalEX cammock

PortalEX 的外置相机模拟实现集合。PortalEX 主仓库只负责调用、安装和 UI；
实际接管相机栈的 provider / HAL / producer 都在这里，并按品牌和设备隔离。

```text
oneplus/
  PKG110/              OnePlus AIDL provider 实现
xiaomi/
  sagit/               小米 MI 6 HAL-overlay / root 模块实现
  camera-hal/          HAL shim 与 camshim 源码
  camera-hal-module/   root 模块骨架
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
oneplus/PKG110/
  provider             AIDL camera provider
  meta.bin / tags.bin  相机元数据
  start.sh             启动 provider、重启 cameraserver
  stop.sh              停止 provider、恢复厂商 provider
  cammock.json          supportedModels + videoCorrection
```

启动后 `camera devices` 由厂商的 6 个变为 1 个虚拟相机；停止后恢复厂商相机。

### Xiaomi MI 6 / sagit

```text
xiaomi/sagit/
  system/vendor/lib/hw/camera.msm8998.so   camshim HAL
  camera.msm8998.orig.so                   真实 HAL 副本
  producer.apk                             视频帧生产者
  start.sh / stop.sh                       挂载/卸载 overlay，重启 provider
  portal.cfg                               HAL 参数
```

这是 root 模块形态：PortalEX 把实现包交给 root 管理器安装，由实现的脚本负责
overlay、SELinux、帧文件权限和 producer 生命周期。

MI 6 的方向、UV 顺序、照片镜像等修正只存在于：

```text
xiaomi/sagit/portal.cfg
xiaomi/camera-hal/src/camshim.c
producer/.../ProducerActivity.java
```

不要把这些字段或行为复制到 OnePlus 实现；两台设备使用不同的相机栈和校正链路。

## 构建与打包

### OnePlus

```bash
# 组装实现包（provider 为预编译执行体）
cd build/oneplus-PKG110
zip -qr ../../dist/portalex-cammock-oneplus-PKG110-implementation.zip .
```

### Xiaomi / producer

```bash
cd producer
./gradlew :app:assembleDebug

cd ../xiaomi/sagit
zip -qr ../../dist/portalex-cammock-sagit-module.zip .
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
