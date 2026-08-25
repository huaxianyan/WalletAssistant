# WalletAssistant

从用户授权的数据源导入并在设备端管理行程信息。当前已实现从 Gmail 主动导入 12306 购票邮件、行程确认、本地持久化、设备端乘车提醒，以及可选的 Google Wallet 导出。

## 当前范围

- `core`：与 Android 无关的统一行程模型和邮件解析器
- `app`：Android/Compose 行程管理界面、Gmail 主动导入和 Room 本地存储
- Gmail Token 和邮件正文只在设备端使用，不发送到自建服务端
- 不处理检票二维码
- Android 应用是行程管理和提醒的主要入口
- Google Wallet 是可选导出目标，使用 Android SDK 的未签名 Pass 流程，不建设自有签名服务
- 首页使用紧凑行程卡片，完整信息和操作集中在行程详情弹窗
- 支持手动归档已出发的行程，并可从归档页恢复
- 添加入口统一为首页右下角按钮；截图识别尚未实现
- 设置页管理新行程默认提醒、Google Wallet 入口和浅色/深色主题
- 应用提醒默认关闭，可设置新行程默认值并为单个行程开关
- 经用户授权精确提醒后，发车前 3 小时显示普通锁屏通知；Android 16 在发车前 30 分钟升级为 Live Update，发车后自动结束

## 本地环境

- JDK 17
- Android SDK 36

```bash
./gradlew :core:test
./gradlew :app:assembleDebug
```

正式 `applicationId` 为 `com.neko7ina.wallet.assistant`。
