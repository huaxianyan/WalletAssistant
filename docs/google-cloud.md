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

计划使用 Google Wallet Android SDK 的未签名 Pass 流程。应用通过包名和 APK 签名证书完成调用方验证，不在 APK 中保存 Service Account 私钥，也不建设自有签名服务。

接入前仍需完成 Google Wallet Issuer 注册、Android 应用凭据登记和 Publishing Access 申请。
