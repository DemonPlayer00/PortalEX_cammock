# PortalEX cammock

External camera-hook implementations for PortalEX, laid out by brand and
device codename.

```
oneplus/
  PKG110/              OnePlus PKG110 AIDL provider
    cammock.json
    provider/
xiaomi/
  sagit/               Xiaomi MI 6 (sagit) Magisk/APatch HAL module
interface/             shared frame-file protocol
```

Each implementation's `cammock.json` contains:

```json
{
  "name": "OnePlus PKG110 adapter",
  "supportedModels": ["PKG110", "OP5D0B"]
}
```

`supportedModels` is checked against Android `Build.MODEL`, `Build.DEVICE`,
and `Build.PRODUCT` at import time. A mismatch is a warning only.

## OnePlus PKG110

Build:

```bash
cd oneplus/PKG110/provider
cmake -S . -B build -DANDROID_ABI=arm64-v8a
cmake --build build -j
```

Package `portal_camera_provider` as `provider` together with
`cammock.json` and optional `meta.bin`/`tags.bin`.

PortalEX installs the zip in app-private storage and releases the runtime
files to `/data/local/tmp/portalex/cammock/`.

## Xiaomi MI 6 / sagit

Magisk/APatch HAL-overlay implementation. Install the module directory as a
module zip; it replaces the vendor camera HAL at boot. This is a different
integration path from the OnePlus AIDL provider.

## Shared

The selection protocol, PortalEX UI, upload/download, and face calibration
remain in the PortalEX repository.
