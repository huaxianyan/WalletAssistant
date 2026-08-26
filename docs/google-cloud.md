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

### Scope 最小权限说明

应用代码只请求：

```text
https://www.googleapis.com/auth/gmail.readonly
```

用户主动发起同步后，应用调用 `users.messages.list`，使用 `from:12306@rails.com.cn` 查询并遍历分页；随后调用 `users.messages.get?format=full` 读取候选邮件正文，在设备端识别购票、改签、候补兑现和退票信息。应用不调用发送、修改标签、删除邮件或修改设置的 Gmail API。

`gmail.metadata` 不能读取邮件正文，而且 `users.messages.list` 的 `q` 参数不能与该 Scope 一起使用，因此无法完成按发件人发现邮件和正文解析。Gmail Add-on 的 `gmail.addons.current.message.readonly` 只向 Google Workspace Add-on 临时开放当前邮件，不适用于独立 Android 应用，也不能分页同步邮箱。`gmail.modify` 和 `mail.google.com` 均比 `gmail.readonly` 权限更大。因此，`gmail.readonly` 是当前自动导入功能可用的最小 Gmail Scope。

Google Cloud Console 的 Data Access 页面只能提交上述一个 Gmail Scope。重新录制审核视频前，应先撤销测试账号对「出行」的既有授权，使视频完整显示 OAuth 同意页；展开「显示所有服务」，保证 Scope 说明清晰可读，并演示购票、改签、候补兑现和退票的完整导入流程。

官方参考：

- [Gmail API Scopes](https://developers.google.com/workspace/gmail/api/auth/scopes)
- [`users.messages.list`](https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/list)
- [`users.messages.get`](https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/get)

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
