# Google Cloud 配置

Google Cloud 项目：

```text
项目名称：WalletAssistant
项目 ID：walletassistantqlbb10jcvuvmbat
```

## Gmail API 清理

应用不再接入 Gmail API，也不请求 Google 用户数据 Scope。Google Cloud 项目继续用于 Google Wallet，不得删除整个项目。

完成代码迁移后，在 Google Cloud Console 中：

1. 从 Google Auth Platform 的 Data Access 页面删除 `https://www.googleapis.com/auth/gmail.readonly`；
2. 禁用 Gmail API；
3. 删除仅用于 Gmail 授权的 Debug 和 Release Android OAuth Client；
4. 回复审核邮件，请求关闭当前 Gmail Scope 验证；
5. 保留 Google Wallet Issuer、包名和 APK 签名配置。

应用改由用户自行配置 TLS IMAP。邮箱地址和专用密码或授权码加密保存在 Android 设备上，不经过 Google Cloud 项目或开发者运营的服务器。

## Google Wallet

Google Wallet Issuer：

```text
Issuer ID：3388000000023177100
Publishing Access：已批准
```

Google Wallet Business Console 的 `Additional features → App Permissions` 已登记相同包名及 Debug、Release SHA-1。

应用使用 Google Wallet Android SDK 的未签名 Pass 流程，通过包名和 APK 签名证书验证调用方。Issuer Owner 邮箱由本机 `wallet.properties` 注入，不提交到 GitHub；公开 APK 中仍可提取该字段，因此应使用 Issuer 管理账号而非秘密凭据。

真机已经验证 Generic Pass 首次添加成功；再次提交同一 Object ID 时，Google Wallet 会识别为已添加，不创建重复卡片。Publishing Access 已批准，正式卡片不再显示测试标记。
