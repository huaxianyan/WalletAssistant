# WalletAssistant

从用户授权的数据源导入行程信息，经用户确认后添加到 Google Wallet。当前已实现从 Gmail 主动导入 12306 购票邮件、行程确认、本地持久化、无后端的 Google Wallet 添加流程和设备端乘车提醒。

## 当前范围

- `core`：与 Android 无关的统一行程模型和邮件解析器
- `app`：Android/Compose 界面、Gmail 主动导入和 Room 本地存储
- Gmail Token 和邮件正文只在设备端使用，不发送到自建服务端
- 不处理检票二维码
- 使用 Google Wallet Android SDK 的未签名 Pass 流程，不建设自有签名服务
- 应用提醒默认关闭，可设置新行程默认值并为单个行程开关
- 发车前 3 小时显示普通锁屏通知；Android 16 在发车前 30 分钟升级为 Live Update，发车后自动结束

## 本地环境

- JDK 17
- Android SDK 36

```bash
./gradlew :core:test
./gradlew :app:assembleDebug
```

正式 `applicationId` 为 `com.neko7ina.wallet.assistant`。
