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

## 2026-07-21：PosFaceDemo 领导验收 UI/UX 整理

### 范围

- 本次只进行领导验收演示所需的 UI/UX 层级整理。
- 首页突出 PoC 名称、离线本地处理说明、授权状态、模型状态、测试用户数量和“进入人脸识别”主入口。
- Debuggable 构建中保留设备环境诊断、授权检查、在线激活和模型初始化入口，并放入可展开“调试工具”区域。
- 摄像头页突出主提示、质量状态、RGB 活体状态、识别状态、测试用户数量、注册/删除按钮和本地隐私提示。
- 不新增网络、云同步、支付、身份证核验、多用户管理、人脸图片保存、NIR/Depth 或后台能力。

### 未改变内容

- 百度授权流程、授权文件处理和模型初始化流程未变。
- Camera ID、640x480 输入、direction=270、mirror=0、预览方向和约 300ms 抽帧未变。
- 人脸框坐标映射、检测背压、质量阈值、RGB 活体阈值、注册门控、10 秒注册超时、SQLite 表结构、FaceSearch 阈值和 `score > 80` 匹配公式未变。
- 百度 AAR、模型文件、Manifest、本地授权资源和官方 Demo 未修改。

### 领导演示推荐流程

1. 打开首页，展示百度授权、模型状态和测试用户数量。
2. 点击“进入人脸识别”。
3. 正常面对摄像头，展示质量通过、RGB 活体通过和已识别测试用户。
4. 使用非真人测试材料，展示活体检测未通过。
5. 使用未注册测试人员，展示未识别到已注册用户。
6. 展示人脸过远、偏头或遮挡时的主提示。
7. 如时间允许，展示注册测试用户和删除测试用户。
8. 强调所有处理在设备本地完成、不保存人脸照片、测试特征可通过删除按钮清除。

### 演示中不得展示

- 测试序列号、授权密钥、设备指纹、数据库内容、特征值或内部日志。

## 2026-07-21：PosFaceDemo 本地多用户人脸库

### 范围

- 将单个固定测试用户升级为本地多用户测试人脸库。
- 不保存人脸照片；仅在 App 私有 SQLite 中保存本地测试别名、feature BLOB 和注册时间。
- 测试用户显示名自动生成为“测试用户 1”、“测试用户 2”、“测试用户 3”。
- 新增“人脸库”页面，可查看测试用户数量、测试用户列表、注册时间，并支持单独删除和删除全部。
- 摄像头页注册按钮改为“注册新用户”，管理入口改为“管理人脸库”。

### 数据库迁移策略

- 旧版本 v1 表结构：`id`、`test_user_id`、`feature`、`created_at`。
- 新版本 v2 通过 `ALTER TABLE` 原地增加 `user_key` 和 `display_name`，不 drop 表、不重建表、不清空特征。
- 旧固定用户 `test-user-001` 保留其 feature，并映射为非身份化显示名“测试用户 1”。
- 新注册用户写入新记录，不再覆盖固定用户。

### FaceSearch 同步

- 页面进入、注册成功、单独删除、删除全部后刷新或清理 FaceSearch 内存库。
- 单独删除只删除 SQLite 对应记录，随后完整重载 FaceSearch，不影响其他测试用户。
- 删除全部会清空 SQLite 测试用户并清理 FaceSearch 内存库。

### 不变内容

- Camera、640x480、direction=270、mirror=0、抽帧频率、人脸框映射、质量阈值、RGB 活体阈值、注册 10 秒超时、FaceSearch 阈值 80、匹配公式 `score > 80`、识别冷却 2 秒均未改变。
- 授权逻辑、模型初始化参数、百度 AAR、模型文件和官方 Demo 未改变。

## 2026-07-21：摄像头页 UI 压缩与识别欢迎文案保持

### 范围

- 首页调试工具按钮统一高度和垂直间距。
- 摄像头页顶部移除标题和“离线”标签，仅保留 48dp 触控区域的返回箭头按钮。
- 底部信息卡片压缩 padding 和行距，隐藏重复的检测标题行。
- 相似度改为仅在识别成功时显示，非匹配状态不占位。
- 识别成功后主提示保持“欢迎您，测试用户 N”，后续 CHECKING/NOT_RUN 中间状态不覆盖欢迎文案。

### 欢迎文案清除条件

- 无人脸、模型/检测异常、质量失败、人脸过远、RGB 活体失败、识别 NOT_MATCHED、无测试用户、识别异常、页面暂停或释放摄像头时清除最近欢迎文案。

### 不变内容

- 未修改 Camera、预览尺寸和方向、Overlay 映射、质量阈值、RGB 活体阈值、FaceSearch 阈值、识别冷却、注册超时、SQLite schema、授权、模型初始化、AAR、模型文件或百度官方 Demo。

## 2026-07-21：PosFaceDemo DX8000 Demo Ready 冻结

### 最终验收范围

- App 启动后自动检查本地授权，授权就绪后自动初始化百度人脸模型。
- 首页状态卡、调试工具折叠区、统一调试反馈区域和领导演示 UI 已完成实机验收。
- DX8000 前置摄像头、640x480 输入、算法 direction=270、mirror=0、人脸框映射和约 300ms 抽帧保持稳定。
- 人脸质量门控、`qualityMinFaceWidth=200`、RGB 静默活体、本地注册、本地 SQLite 特征保存、本地 FaceSearch 1:N 识别均通过实机验证。
- 多用户本地人脸库支持自定义本地显示姓名、空值校验、重名校验、单独删除、删除全部、页面重进和 App 进程重启后恢复识别。
- 注册流程包含 1500 ms 准备阶段，之后采集连续 3 个有效帧，有效帧间隔至少 500 ms，只在第 3 个有效帧执行一次特征提取并保存一条记录。
- 注册总超时仍为 10 秒；准备阶段、有效帧采集、特征提取和写库均计入总超时。
- 本地 SQLite 仅保存本地显示名称、内部 key、feature BLOB 和注册时间；不保存人脸照片。
- 自定义显示姓名只用于本地 UI 展示，不作为内部 key，不写入日志，不应填写敏感身份信息。

### 最终实机结果

- 真人 RGB 活体稳定 PASS，分数约 0.956-0.960；非真人受控测试分数约 0.5，RGB 活体阈值为 0.80。
- 注册特征提取返回 `featureSizeReturned=128`，符合官方成功条件。
- 本地 FaceSearch 1:N 识别阈值为 80，匹配规则为 `score > 80`，识别冷却约 2 秒。
- 识别成功主提示可保持欢迎文案，底部识别状态显示“识别：成功（98.1%）”形式。
- 验收期间未出现 `exceptionType`、闪退、SQLite 错误或 native 错误。

### 领导演示推荐流程

1. 打开首页，展示授权、模型和测试用户数量均已就绪。
2. 展开调试工具，展示四个调试入口和统一反馈区域；不展示任何授权值、设备标识或内部日志。
3. 进入“离线人脸识别”，正常面对摄像头，展示质量通过、RGB 活体通过和识别成功欢迎文案。
4. 使用非真人测试材料，展示活体检测未通过。
5. 使用未注册测试人员，展示未识别到已注册用户。
6. 展示人脸过远、偏头或遮挡时的主提示。
7. 如时间允许，展示输入本地显示姓名、注册准备、连续 3 个有效帧采集、注册成功和人脸库列表。
8. 展示单独删除或删除全部测试用户后无法继续识别对应用户。
9. 强调所有处理在设备本地完成，不保存人脸照片，测试特征可本地删除。

### 冻结说明

- 该冻结点用于 DX8000 离线人脸识别 PoC 的领导演示版本。
- 当前实现不是正式生产版本；阈值、安全、合规、数据加密、Release 隔离和完整异常处理仍需后续专项设计。

## 2026-07-21：注册前输入本地显示姓名

### 范围

- 点击“注册新用户”后先弹出姓名输入对话框，用户点击“开始注册”后才创建一次性注册请求。
- 显示姓名最长 20 个 Unicode 字符，提交前执行 `trim()`，空白或空字符串不创建注册请求。
- 注册请求创建时固定 displayName，后续检测帧不再读取输入框。
- 保存成功后 SQLite 写入用户输入的显示姓名和 feature BLOB，识别成功显示“欢迎您，姓名”。
- 人脸库页面继续显示本地显示姓名和注册时间。

### 重名处理

- 注册前通过参数化查询检查 display_name 是否已存在；重名时显示“该姓名已存在，请使用其他姓名”。
- `LocalFaceRepository.createUser()` 写入前再次防御性检查重名，避免 UI 检查与实际写入之间的竞态。
- displayName 仅用于本地 UI 展示，不作为 user_key，不进入日志。

### 不变内容

- 未修改 SQLite schema、数据库版本、v1 到 v2 迁移、user_key 生成策略、test_user_id 兼容写入策略、feature BLOB 存储方式。
- 未修改 Camera、预览尺寸和方向、Overlay 映射、质量阈值、RGB 活体阈值、FaceSearch 阈值、识别冷却、注册超时、授权、模型初始化、AAR、模型文件或百度官方 Demo。

## 2026-07-21：调试反馈与顶部栏对齐微调

### 范围

- 首页调试工具改为四按钮连续按钮组，统一按钮高度和 8dp 间距。
- 四个调试操作共用一个 `debugFeedbackText`，反馈区位于按钮组下方，默认 `GONE`，不再夹在按钮之间。
- 摄像头页和人脸库页顶部栏统一为 56dp 高 FrameLayout，标题相对于整个页面水平居中，返回按钮位于左侧且不推动标题。
- 人脸库页标题改为“本地人脸库”。

### 不变内容

- 未修改 Camera、预览尺寸和方向、Overlay 映射、质量阈值、RGB 活体阈值、FaceSearch 阈值、识别冷却、注册超时、SQLite schema、授权、模型初始化、AAR、模型文件或百度官方 Demo。

## 2026-07-21：验收反馈 UI 微调

### 范围

- 首页“调试工具”按钮改为同一垂直按钮组，四个按钮统一高度和 8dp 间距。
- 摄像头页顶部恢复紧凑栏：返回按钮 + “离线人脸识别”，不再显示单独“离线”徽标。
- 底部识别状态合并相似度，显示为“识别：成功（98.1%）”，不再单独占用相似度行。
- 主提示欢迎文案保持机制继续保留，识别中间状态不覆盖“欢迎您，测试用户 N”。

### 不变内容

- 未修改 Camera、预览尺寸和方向、Overlay 映射、质量阈值、RGB 活体阈值、FaceSearch 阈值、识别冷却、注册超时、SQLite schema、授权、模型初始化、AAR、模型文件或百度官方 Demo。
