# PortalEX cammock

External camera-hook implementation for PortalEX.

## Package format

A release zip imported by PortalEX contains:

```
cammock.json
provider
meta.bin   (optional)
tags.bin   (optional)
```

`cammock.json`:

```json
{
  "name": "OnePlus PKG110 adapter",
  "supportedModels": ["PKG110", "OP5D0B"]
}
```

`supportedModels` is checked against Android `Build.MODEL`, `Build.DEVICE`,
and `Build.PRODUCT` when the zip is imported. A mismatch is a warning only.

## Build

```bash
cd oneplus/provider
cmake -S . -B build -DANDROID_ABI=arm64-v8a
cmake --build build -j
```

The resulting `portal_camera_provider` is packaged as `provider`.

## Runtime

PortalEX installs the zip in app-private storage, then releases the provider
and optional meta/tags to `/data/local/tmp/portalex/cammock/` when camera mock
starts.

This repository only contains the device-specific hook implementation.
system_server selection, PortalEX UI, upload/download, and the OpenCV face
calibration remain in the PortalEX repository.
