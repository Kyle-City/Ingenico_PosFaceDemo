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
