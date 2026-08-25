# WalletAssistant

从用户授权的数据源导入行程信息，经用户确认后添加到 Google Wallet。当前已实现 12306 购票邮件正文解析、行程确认和本地持久化闭环。

## 当前范围

- `core`：与 Android 无关的统一行程模型和邮件解析器
- `app`：Android/Compose 界面和 Room 本地存储
- 邮件正文只在设备端解析
- 不处理检票二维码
- 应用提醒默认关闭，提醒功能将在导入闭环完成后接入

## 本地环境

- JDK 17
- Android SDK 35

```bash
./gradlew :core:test
./gradlew :app:assembleDebug
```

正式 `applicationId` 为 `com.neko7ina.wallet.assistant`。
