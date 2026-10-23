# Xiaomi MI 6 / sagit cammock

MI 6 uses a 32-bit vendor Camera HAL (`/vendor/lib/hw/camera.msm8998.so`),
so it does not use the OnePlus AIDL provider path.

This directory is an APatch/Magisk HAL-overlay module:

- `system/vendor/lib/hw/camera.msm8998.so` — virtual camera HAL payload
  (built locally from `camera-hal/src/camshim.c`; not tracked by Git)
- `producer.apk` — frame producer payload (built from `producer/`; not tracked by Git)
- `post-fs-data.sh` — mounts the HAL overlay
- `service.sh` / `sepolicy.rule` — module lifecycle and policy

`cammock.json` declares:

```json
{
  "type": "magisk-module",
  "supportedModels": ["MI 6", "sagit"]
}
```

Build the payloads first, then install the directory as a module zip through the
device's root manager. The repository only tracks source, scripts and configs.
A reboot is required before the HAL is used. Disable the module and reboot to
restore the stock camera.
