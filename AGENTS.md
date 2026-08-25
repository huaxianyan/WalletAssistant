# 项目约定

## 产品范围

- Android 应用，面向未来公开使用设计
- 从用户授权的数据源导入行程信息；首个来源为 Gmail，首个解析格式为 12306 购票成功邮件
- 数据来源、内容解析、统一行程模型和 Google Wallet 输出保持独立边界
- Gmail Token、原始邮件、候选邮件和解析结果只在设备端处理与保存，不发送到自建服务端
- Google Wallet 卡片只展示乘车所需的基本信息，不处理、复制或模拟 12306 动态检票二维码
- Google Wallet 是主要提醒渠道；应用通知是用户可选的补充提醒，默认关闭，并支持全局默认与单个行程设置
- 不保证第三方卡片一定显示在系统 At a Glance 区域
- 正式 Android 包名为 `com.neko7ina.wallet.assistant`

## 实现原则

- 首版使用编译期注册的解析器，不建设动态插件系统
- 解析结果必须经过用户确认；缺失信息不猜测
- 中国铁路邮件中的时间使用 `Asia/Shanghai`
- Google Wallet 使用 Android SDK 的未签名 Pass 流程，通过包名和 APK 签名验证，不建设自有签名服务
- 用户确认添加后，最少行程字段由 Android 应用直接提交给 Google Wallet

## 仓库与发布

- GitHub 仓库为 `https://github.com/huaxianyan/WalletAssistant`
- 开发进度同步到该仓库，正式构建通过 GitHub Releases 发布
- Android Release KeyStore 和密码不得进入 Git
- 完整签名恢复包保存在 `\\192.168.7.216\homes\NeKo7inA\dev\Android Signing\WalletAssistant`
