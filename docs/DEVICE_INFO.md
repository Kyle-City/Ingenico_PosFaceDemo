# Android POS 设备摸底记录

记录日期：2026-07-15

## 采集来源

- 设备序列号：`LDYF00017178`
- 采集方式：ADB
- 关键命令：`getprop`、`wm size`、`wm density`、`pm list features`、`dumpsys media.camera`
- 说明：同一 ADB 环境中存在 `emulator-5554 offline`，实际采集时已指定 `-s LDYF00017178`。

## 已确认信息

| 字段 | 结果 | 状态 | 来源 |
| --- | --- | --- | --- |
| 制造商 | `ingenico` | 已确认 | `adb shell getprop ro.product.manufacturer` |
| 型号 | `DX8000` | 已确认 | `adb shell getprop ro.product.model` |
| Android 版本 | `10` | 已确认 | `adb shell getprop ro.build.version.release` |
| API Level | `29` | 已确认 | `adb shell getprop ro.build.version.sdk` |
| Primary ABI | `armeabi-v7a` | 已确认 | `adb shell getprop ro.product.cpu.abi` |
| ABI List | `armeabi-v7a,armeabi` | 已确认 | `adb shell getprop ro.product.cpu.abilist` |
| 屏幕尺寸 | `720x1280` | 已确认 | `adb shell wm size` |
| 屏幕密度 | `320 dpi` | 已确认 | `adb shell wm density` |
| 摄像头能力 | `android.hardware.camera`、`android.hardware.camera.any`、`android.hardware.camera.front`、`android.hardware.camera.autofocus`、`android.hardware.camera.flash` | 已确认 | `adb shell pm list features` |
| Camera HAL | `legacy/0 (v2.5 provider)`，设备接口 `device@3.3/legacy/* (v3.3)` | 已确认 | `adb shell dumpsys media.camera` |
| 可见摄像头条目 | `legacy/0`、`legacy/21`、`legacy/24`、`legacy/37`、`legacy/38` | 已确认 | `adb shell dumpsys media.camera` |
| 摄像头朝向 | 输出条目均显示 `Facing: Front` | 已确认，仍需实机预览验证目标摄像头 | `adb shell dumpsys media.camera` |
| Sensor Orientation | 输出条目均显示 `270` | 已确认，仍需实机预览验证旋转与镜像 | `adb shell dumpsys media.camera` |
| Active Array | 多数条目显示 `0 0 640 480` | 已确认，仍需实机预览验证实际预览分辨率 | `adb shell dumpsys media.camera` |

## 仍需检查

| 项目 | 原因 | 最少必要检查 |
| --- | --- | --- |
| 目标前置 RGB 摄像头 ID | `dumpsys media.camera` 显示多个 front 条目，无法仅凭静态信息判断 POS 业务上应使用哪一个。 | 做最小 Camera 预览 Demo，逐个验证可打开的前置 cameraId、画面位置、清晰度和用户视角。 |
| 摄像头方向、镜像、画面比例 | Sensor Orientation 为 `270`，POS 竖屏 720x1280，前摄预览容易出现旋转、镜像或拉伸。 | 在 POS 上实测预览，记录每个 cameraId 的旋转、镜像和可用尺寸。 |
| 是否 RGB+IR | 当前 ADB 输出只能确认普通 camera/front 能力，未确认红外硬件。 | 不推断 IR；如需确认，需设备规格书或厂商接口说明。 |
| APK 安装与签名限制 | ADB 已连接，但尚未安装本 Demo APK 验证。 | 构建最小 APK 后执行 `adb -s LDYF00017178 install -r <apk>`。 |
| 网络能力 | 当前任务未采集公网 HTTPS 连通性。第一阶段本地链路不依赖网络，但后续 License/Sample 或第二阶段可能需要。 | 后续按需执行 `adb -s LDYF00017178 shell ping -c 3 cloud.baidu.com` 或使用测试 HTTPS URL。 |
| 百度 SDK License/ABI 兼容 | SDK 尚未提供，无法核对 `.so` 架构、License 绑定包名和签名。 | 等实际 SDK/Sample 后检查 `jniLibs` 或 AAR 内 `.so` 是否包含 `armeabi-v7a`。 |

## 原生人脸 SDK 基础条件分析

- Android API Level 29，具备运行现代 Android 原生 SDK 的系统基础条件。
- 设备仅确认 `armeabi-v7a,armeabi`，属于 32 位 ARM 环境。后续百度 Android Face SDK 必须提供 `armeabi-v7a` 原生库；如果 SDK 仅提供 `arm64-v8a`，该 POS 无法直接运行对应原生库。
- 系统声明有前置摄像头和自动对焦能力，具备做本地摄像头预览、人脸检测和动作活体的基础前提。
- Camera HAL 显示 legacy provider 和 device@3.3 legacy 条目，说明摄像头适配可能存在厂商实现差异。第一阶段应先完成普通 Camera 预览验证，再接入任何人脸 SDK。
- 多个 camera 条目都显示 Front，存在 cameraId 选择风险。不能假设 camera 0 或某个固定 ID 一定是正确业务前摄。
- Active Array 多数为 `640x480`，摄像头基础画幅可能偏低；质量检测、活体通过率、距离提示和清晰度阈值需要在 POS 实机验证。

## ABI、API Level 与摄像头风险

### ABI 风险

- 当前设备 Primary ABI 为 `armeabi-v7a`，ABI List 为 `armeabi-v7a,armeabi`。
- 风险：如果百度 SDK 或其他本地库不提供 32 位 ARM `.so`，会出现 `UnsatisfiedLinkError` 或无法初始化。
- 应对：拿到实际 SDK 后先检查 AAR/SDK 包内 ABI，再决定是否配置 `abiFilters`。在此之前不修改 ABI 配置。

### API Level 风险

- 当前 API Level 为 29，Android 10。
- 风险较低：系统版本满足常见 Camera、View/XML、ViewModel 和本地 SDK 运行基础。
- 仍需确认：SDK 文档的最低 Android 版本、权限声明、License 绑定方式是否兼容 Android 10。

### 摄像头风险

- 已确认系统具备 `android.hardware.camera.front`。
- 风险：多个 front camera 条目、Sensor Orientation 为 `270`、legacy HAL，可能导致预览旋转、镜像、裁剪比例、人脸框坐标映射异常。
- 应对：下一阶段只做最小前置摄像头预览，记录 cameraId、尺寸、旋转、镜像和生命周期行为，不接入百度 SDK。

## 下一组最少必要 ADB 命令

当前基础字段已足够进入“最小前置摄像头预览”任务。若需要继续补充安装与网络能力，建议只执行：

```powershell
adb -s LDYF00017178 shell getprop ro.serialno
adb -s LDYF00017178 shell settings get global http_proxy
adb -s LDYF00017178 shell date
adb -s LDYF00017178 install -r <path-to-debug-apk>
```
