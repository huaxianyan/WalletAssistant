# Google Cloud 配置

Google Cloud 项目：

```text
项目名称：WalletAssistant
项目 ID：walletassistantqlbb10jcvuvmbat
```

## Gmail

项目已启用 Gmail API，并在 Google Auth Platform 中配置：

```text
https://www.googleapis.com/auth/gmail.readonly
```

Android OAuth 客户端使用相同包名和不同签名证书：

```text
Package name: com.neko7ina.wallet.assistant
Debug SHA-1: 49:01:49:0F:70:2E:4B:1A:53:4F:0E:8F:23:F0:47:11:9C:24:7F:F2
Release SHA-1: 00:D1:EE:12:B1:1F:D0:E3:F1:38:75:12:FB:98:A9:F9:A1:B0:4E:43
```

Gmail Token、原始邮件和解析结果只在 Android 设备上处理，不发送到自建服务端。

## Google Wallet

Google Wallet Issuer：

```text
Issuer ID：3388000000023177100
状态：Demo Mode
```

Google Wallet Business Console 的 `Additional features → App Permissions` 已登记相同包名及 Debug、Release SHA-1。

应用使用 Google Wallet Android SDK 的未签名 Pass 流程，通过包名和 APK 签名证书验证调用方。Issuer Owner 邮箱由本机 `wallet.properties` 注入，不提交到 GitHub；公开 APK 中仍可提取该字段，因此应使用 Issuer 管理账号而非秘密凭据。

真机已经验证 Generic Pass 首次添加成功；再次提交同一 Object ID 时，Google Wallet 会识别为已添加，不创建重复卡片。

正式公开使用前仍需申请 Publishing Access，移除卡片上的 `[仅限测试]` 标记。
