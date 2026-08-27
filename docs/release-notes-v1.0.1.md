# 出行 v1.0.1

这是一次文案与发布流程更新，不改变行程数据、邮箱配置、提醒设置或 Google Wallet 卡片。

## 下载与安装

安装文件将在本 Release 的 Assets 中提供：

```text
chuxing-1.0.1.apk
```

系统要求：Android 8.0 或更高版本。

已经安装 `v1.0.0` 的用户可以直接覆盖安装，本地数据会继续保留。

## 更新内容

- 统一应用通知和设置页面中的中文引号、逗号及句子节奏
- 将自动同步通知中的应用名称改为使用直角引号
- 统一 README、维护文档、Release Notes 和网站中的中文排版
- 修复 Release Notes 权限说明的 Markdown 加粗格式
- 新增 GitHub Actions 自动签名发布流程，并在发布前精确核对正式签名证书

## 完整性校验

本 Release 同时提供 `chuxing-1.0.1.apk.sha256`。下载后请核对 Release 正文、`.sha256` 文件和本地计算值一致。

Release 签名证书 SHA-256：

```text
EA:FA:BA:2F:32:9A:58:D0:91:D1:64:16:30:33:9F:2C:C3:E8:1B:B7:2C:4E:3E:04:8F:E5:F0:68:FE:2B:E2:BE
```

## 支持与许可

- 支持：https://chuxing.neko7ina.com/support/
- 问题反馈：https://github.com/huaxianyan/WalletAssistant/issues
- 第三方许可：https://github.com/huaxianyan/WalletAssistant/blob/main/THIRD_PARTY_NOTICES.md

请勿在公开 Issue 中提交邮箱密码、授权码、完整邮件、订单号、身份证件、动态检票二维码或其他敏感信息。
