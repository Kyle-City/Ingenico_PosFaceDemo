# 开发日志

## 2026-07-15：人脸 SDK 供应商方案变更与签名信息检查

### 方案变更

- 人脸 SDK 供应商由百度人脸识别调整为阿里云人脸 SDK。
- 变更原因：公司网络无法访问百度人脸识别 SDK 平台。
- 当前尚未确定具体阿里云人脸产品和 SDK 版本。
- 在提供实际阿里云 SDK、Demo 和对应官方文档前，不生成阿里云 SDK 接入代码，不新增依赖，不编造类名、接口或依赖坐标。
- 项目其他安全边界保持不变：不连接真实支付，不处理真实客户数据，不提交 Secret Key、Token、人脸图片或 KeyStore。

### 当前 Android 基础信息

| 项目 | 当前值 |
| --- | --- |
| Application ID | `com.kyle.posfacedemo` |
| Namespace | `com.kyle.posfacedemo` |
| POS 系统 | Android 10 / API 29 |
| POS ABI | `armeabi-v7a` |

### Debug 签名信息

来源：`./gradlew.bat signingReport`

| 字段 | Debug Variant |
| --- | --- |
| Application ID | `com.kyle.posfacedemo` |
| Keystore 路径 | `C:\Users\jixie\.android\debug.keystore` |
| Alias | `AndroidDebugKey` |
| MD5 | `97:7B:23:CD:04:D0:84:1E:BF:14:72:4F:7E:48:C6:70` |
| SHA1 | `E8:ED:33:3F:61:B4:69:91:AF:99:3F:D5:E7:CB:E3:09:13:45:D3:9A` |
| SHA-256 | `30:BF:14:70:56:AC:E4:8B:DE:2D:A6:82:91:FA:B5:29:B9:3E:FF:F7:CD:E0:B4:52:ED:D2:33:2E:F8:33:58:B8` |

说明：当前使用默认 Debug KeyStore，仅记录证书摘要和路径，不记录 KeyStore 密码。

### 后续检查点

- 正式选择阿里云产品前，需要确认目标产品是否提供 Android 端本地人脸 SDK，且是否支持 Android 10 / API 29 / `armeabi-v7a`。
- 如阿里云方案需要云端 API，应继续坚持 Secret Key 不进入 APK，由服务端或受控环境保管凭据。

## 2026-07-20：百度官方 Demo DX8000 PoC 闭环验证

### 范围

- 目标 Demo：`vendor/baidu-face-sdk/android-8.6/rgb-sdk`。
- 目标设备：Ingenico DX8000，Android 10 / API 29，`armeabi-v7a`。
- 安全边界：不连接真实支付，不处理真实客户数据，不记录或提交 appkey、seckey、设备指纹、人脸图片或 KeyStore。
- 当前工作仅验证百度官方 Demo 在 POS 上的本地授权、摄像头、RGB 单模态活体、注册和本地 1:N 识别闭环。

### 修改过的官方 Demo 文件

| 文件 | 类别 | 说明 |
| --- | --- | --- |
| `vendor/baidu-face-sdk/android-8.6/rgb-sdk/app/src/main/AndroidManifest.xml` | 授权兼容 | 恢复 Liantian 相关组件；移除最终 APK 中的 `READ_PHONE_STATE` 权限请求，避免运行期敏感权限依赖。 |
| `vendor/baidu-face-sdk/android-8.6/rgb-sdk/AuthLibrary/src/main/java/com/example/authlibrary/BdFaceAuth.java` | 授权兼容 / 诊断日志 | 增加授权设备 ID 获取链路诊断日志；日志不输出设备 ID 或指纹。 |
| `vendor/baidu-face-sdk/android-8.6/rgb-sdk/gatelibrary/src/main/java/com/baidu/idl/main/facesdk/activity/gate/FaceUiActivity.java` | 摄像头选择 / 预览方向 / 诊断日志 | 闸机基础预览链路记录相机参数；DX8000 Camera ID 1 时使用已验证的 RGB 预览方向输入 `270`。 |
| `vendor/baidu-face-sdk/android-8.6/rgb-sdk/gatelibrary/src/main/java/com/baidu/idl/main/facesdk/activity/gate/FaceOneToNActivity.java` | 算法方向 / 诊断日志 | 闸机 1:N 检测链路记录方向参数；DX8000 Camera ID 1 时算法 direction 使用 `270`。 |
| `vendor/baidu-face-sdk/android-8.6/rgb-sdk/datalibrary/src/main/java/com/example/datalibrary/gatecamera/CameraPreviewManager.java` | 摄像头选择 / 预览方向 / 诊断日志 | 记录 Camera.open、CameraInfo、预览尺寸、焦点和 FPS 能力；Camera ID 1 且输入方向 `270` 时实际 `setDisplayOrientation=90`。 |
| `vendor/baidu-face-sdk/android-8.6/rgb-sdk/datalibrary/src/main/java/com/example/datalibrary/manager/FaceModel.java` | 诊断日志 | 增加质量失败详细日志，包括姿态、模糊、遮挡、画面尺寸、direction、mirror 等。 |
| `vendor/baidu-face-sdk/android-8.6/rgb-sdk/datalibrary/src/main/java/com/example/datalibrary/manager/FaceSDKManager.java` | 诊断日志 | 增加 `DX8000_PIPELINE_GATE` 管线门控日志，区分 best image、quality、blur、occlusion、gesture、face boundary 对 RGB 活体执行的阻断。 |
| `vendor/baidu-face-sdk/android-8.6/rgb-sdk/gatelibrary/src/main/java/com/baidu/idl/main/facesdk/fragment/DevelopFragment.java` | 诊断日志 | 在开发模式显示通用“请正对摄像头”前记录 UI 层质量失败提示来源。 |
| `vendor/baidu-face-sdk/android-8.6/rgb-sdk/gatelibrary/src/main/java/com/baidu/idl/main/facesdk/fragment/PaymentFragment.java` | 诊断日志 | 在支付模式边界检查失败和通用对齐提示前记录 UI 层日志。 |
| `vendor/baidu-face-sdk/android-8.6/rgb-sdk/registerlibrary/src/main/java/com/baidu/idl/main/facesdk/registerlibrary/user/register/FaceRegisterNewNIRActivity.java` | 摄像头选择 / 预览方向 / 算法方向 / 诊断日志 | 注册页 RGB 链路复用 DX8000 已验证参数：Camera ID 1、预览输入方向 `270`、算法 direction `270`；保留脱敏注册页日志。 |
| `vendor/baidu-face-sdk/android-8.6/rgb-sdk/datalibrary/src/main/java/com/example/datalibrary/gl/view/GlMantleSurfacView.java` | 注册页显示比例 | 仅在注册场景 `mIsRegister == true` 时将预览显示从 centerCrop 改为 fitCenter/contain，避免圆形取景框过度放大；非注册场景保持原逻辑。 |

说明：`app/src/main/AndroidManifest.xml.bak.liantian` 和 `app/src/main/AndroidManifest.xml.bak.no-read-phone-state` 是调试期间保留的 Manifest 备份，不参与构建逻辑。

### PoC 运行配置

- POS 运行配置文件：`/data/data/com.baidu.idl.face.demo/files/Settings/gateFaceConfig.txt`。
- RGB Camera ID：`1`。
- RGB 单模态：`type=1`。
- 预览方向输入：`270`；`CameraPreviewManager` 对 Camera ID 1 最终显示为 `setDisplayOrientation=90`。
- 算法 direction：`270`。
- PoC blur 阈值：`0.995`，仅用于老旧 POS 验证，不可作为生产参数。

### 已验证结果

- 在线授权成功。
- Camera ID 1 是清晰的前置摄像头。
- 闸机、考勤、支付模式方向正确。
- 主预览实际 `displayOrientation=90`，算法 direction 为 `270`。
- RGB 单模态活体检测可稳定执行并产生得分。
- 质量管线日志确认 blur、遮挡、姿态、边界等阻断原因可定位。
- 人脸注册页面方向正确。
- 人脸注册页面 fitCenter 后缩放、人脸框和头像裁剪正常。
- 测试人脸注册成功。
- 闸机模式可以完成本地 1:N 识别。

### 建议回退点

- 建议在当前状态创建 Git 标签或分支，例如 `baidu-demo-dx8000-poc-validated`，作为“百度官方 Demo 在 DX8000 上完成 PoC 闭环验证”的回退点。
- 若后续集成失败，可回退到该点重新确认授权、Camera ID、方向、注册和 1:N 识别闭环。

### 下一阶段集成 PosFaceDemo 前需要保留的最小 SDK 文件清单

- SDK AAR：`vendor/baidu-face-sdk/android-8.6/rgb-sdk/faceaar/facelibrary-release-8.5-composite-20251124.aar`。
- 模型资产：`vendor/baidu-face-sdk/android-8.6/rgb-sdk/facelibrary/src/main/assets/face-sdk-models/`。
- 授权相关模块或等价代码：`vendor/baidu-face-sdk/android-8.6/rgb-sdk/AuthLibrary/`。
- 核心数据与算法封装参考：`vendor/baidu-face-sdk/android-8.6/rgb-sdk/datalibrary/` 中的 `FaceSDKManager`、`FaceModel`、`BDFaceImageConfig`、`BDFaceCheckConfig`、`BDQualityConfig`、`BDLiveConfig`、`LivenessModel`、`CameraPreviewManager`、`GlMantleSurfacView` 及相关回调/工具类。
- 注册参考链路：`vendor/baidu-face-sdk/android-8.6/rgb-sdk/registerlibrary/` 中的用户注册、特征提取、头像裁剪和本地库写入逻辑。
- 闸机识别参考链路：`vendor/baidu-face-sdk/android-8.6/rgb-sdk/gatelibrary/` 中的 RGB 单模态检测、活体、1:N 检索和 UI 状态处理逻辑。
- 必要 ABI 原生库由 AAR 内部携带；集成时需确认最终 APK 包含 `armeabi-v7a` 相关 `.so`。
- 不应迁移测试人脸图片、临时运行配置、Secret Key、KeyStore、设备指纹或调试备份文件。

## 2026-07-21：PosFaceDemo 百度检测质量门控 PoC 标定

### 人脸尺寸采样

- 输入帧：640x480 NV21。
- 算法 direction：270。
- 百度检测下限 `detectorMinFaceSize`：60，仅用于 `BDFaceSDKConfig.minFaceSize`，继续作为检测器下限。
- 正常距离 `faceWidth`：约 285-300。
- 较远但可接受 `faceWidth`：约 210-220。
- 明显过远 `faceWidth`：约 115-120。
- PoC 业务质量门槛 `qualityMinFaceWidth`：200。

说明：`qualityMinFaceWidth=200` 是根据 DX8000 老旧固定焦点测试机、640x480 输入和实机 `faceWidth` 数据得到的 PoC 阈值，不是百度官方生产阈值，不能直接作为生产参数。

## 2026-07-21：PosFaceDemo RGB 静默活体与本地测试注册规划

### RGB 静默活体 PoC 结果

- 真人活体分数：约 0.956-0.960。
- 非真人受控测试分数：约 0.5。
- RGB 活体阈值：0.80。
- 单次 RGB 静默活体耗时：约 91-105 ms。
- 以上结果只适用于当前 DX8000 PoC 和有限测试条件，不构成完整安全评估。

### 本地测试注册方案

- 第一版仅保存本地测试 ID 和特征 BLOB。
- 不保存人脸图片、缩略图、NV21、Bitmap、landmarks 或坐标。
- 测试特征属于敏感生物识别数据，必须支持本地删除。
- 当前实现提供删除全部本机测试用户能力，不影响百度授权和模型文件。

### 本地 1:N 识别方案

- 第一版只识别本机 SQLite 中的一个测试用户。
- 页面进入、注册成功、删除全部测试用户后同步刷新 FaceSearch 内存库。
- FaceSearch 加载策略为 `featureClear()` 后从私有 SQLite 读取测试特征，并用 `pushPersonById(localId, feature)` 重建内存库。
- 实时识别复用当前帧 `BDFaceImageInstance` 和 accurate `FaceInfo.landmarks`，不重复建帧、不重复检测。
- 搜索 API 使用 `search(BDFACE_FEATURE_TYPE_LIVE_PHOTO, 80f, 1, featureBuffer)`，匹配规则为 `topScore > 80f`。
- 实时识别 feature buffer 仅用于当次搜索，搜索完成后立即清零。
- 数据库加载到 FaceSearch 的 feature ByteArray 所有权未确认；为避免破坏 SDK 内存库，push 后暂不清零这批数组。
- UI 只显示“测试用户”及相似度/阈值，不显示姓名、测试 ID、特征、图片、landmarks 或坐标。

## 2026-07-21：PosFaceDemo DX8000 离线人脸识别核心 PoC 验收冻结

### 验收范围

- 目标应用：`PosFaceDemo`。
- 目标设备：Ingenico DX8000，Android 10 / API 29，`armeabi-v7a`。
- SDK：百度人脸 SDK 8.6，本地 AAR 与本地模型资产集成到 `PosFaceDemo`。
- 当前范围仅为离线本地测试用户注册、RGB 静默活体、本地 SQLite 特征保存、本地 FaceSearch 1:N 识别。
- 不连接真实支付，不处理真实客户数据，不保存人脸图片，不提交授权密钥、KeyStore、人脸数据库或测试图片。

### 最终实机验收结果

- 用户数为 0 时显示“暂无测试用户”。
- 测试用户注册成功，`featureSizeReturned=128`。
- 重复注册仍只有一个测试用户。
- 注册等待支持 10 秒超时。
- 页面退出能够取消注册请求。
- 正常真人 RGB 活体稳定 PASS，分数约 0.956-0.960。
- 非真人受控测试稳定 FAIL，分数约 0.5。
- RGB 活体阈值为 0.80，单次活体耗时约 91-105 ms。
- 注册后本人本地 1:N 识别 MATCHED，相似度约 98。
- 未注册测试人员 NOT_MATCHED。
- 非真人在活体阶段被拒绝，不执行识别。
- 偏头、遮挡、人脸过远时不执行识别。
- 用户删除后无法继续识别。
- 页面重新进入后仍可识别。
- App 进程重启后仍可从 SQLite 重载并识别。
- 识别冷却约 2 秒。
- 验收期间未出现 `exceptionType`、闪退或 native 错误。
- 当前实现不保存人脸图片，只保存测试 ID 与特征 BLOB。

### 冻结点说明

- 该冻结点代表 DX8000 离线人脸识别核心 PoC 已完成闭环验证。
- 当前测试分数和阈值仅来自有限 PoC 条件，不构成正式安全评估或生产参数建议。
- 后续进入正式化前，需要重新评估授权入口、诊断入口、日志策略、特征数据治理、阈值策略和安全测试覆盖。
