<p align="center">
  <img src="site/assets/icon.svg" width="112" height="112" alt="出行应用图标">
</p>

<h1 align="center">出行</h1>

<p align="center">把散落在邮件和截图里的乘车信息，整理成清晰、可提醒的本地行程。</p>

<p align="center">
  <a href="https://chuxing.neko7ina.com/">应用主页</a> ·
  <a href="https://github.com/huaxianyan/WalletAssistant/releases">下载</a> ·
  <a href="https://chuxing.neko7ina.com/privacy/">隐私政策</a> ·
  <a href="https://chuxing.neko7ina.com/support/">支持</a>
</p>

## 关于「出行」

「出行」是一款 Android 行程管理应用。它从用户主动授权的数据源导入行程，在设备端完成解析、确认、保存和提醒，让乘车信息不再埋在邮件、截图和不同应用中。

当前版本首先支持中国铁路 12306 购票成功邮件。用户可以从 Gmail 主动导入，也可以粘贴邮件正文或选择截图。识别结果必须经过确认，应用不会猜测缺失信息。

```text
Gmail / 粘贴正文 / 截图
            ↓
      设备端解析与确认
            ↓
       本地行程与提醒
            ↓
   可选添加至 Google Wallet
```

## 主要能力

### 从熟悉的来源导入

- <strong>Gmail：</strong>用户主动授权后，查找并读取候选 12306 购票成功邮件；
- <strong>粘贴正文：</strong>无需 Gmail 授权，直接解析复制的购票通知；
- <strong>截图 OCR：</strong>通过 Android 系统照片选择器选择单张截图，使用设备端中文 OCR 读取文字；
- <strong>确认后保存：</strong>先检查文字和行程字段，再决定是否写入本地行程。

### 用一个应用管理行程

- 在首页查看未来行程和乘车关键信息；
- 查看路线、车次、时间、座位、席别、乘车人和订单信息；
- 向左滑动归档，并可从归档页恢复；
- 删除不再需要的本地行程，之后仍可重新导入；
- 可选在未来行程出发后自动归档，不追溯此前已经结束的行程。

### 在需要时提醒

提醒默认关闭，由用户决定是否为新行程或单个行程开启。

- 发车前提醒支持 30 分钟～12 小时，默认 3 小时；
- Android 16 支持发车前 15～60 分钟显示实时状态，默认 30 分钟；
- 实时状态以 5 分钟为调整粒度，可以设置为 20 分钟；
- 「已上车」结束提醒并归档行程；
- 「取消提醒」只结束提醒，不影响独立的自动归档任务；
- 精确提醒使用 Android「闹钟和提醒」权限，但不会在系统时钟中创建闹钟。

### 可选添加至 Google Wallet

用户确认行程后，可以主动将最少乘车信息添加至 Google Wallet Generic Pass。Wallet 卡片显示路线、车次、时间、座位、席别和乘车人，是应用内行程的可选补充，不承担主要提醒职责。

「出行」不会处理、复制或模拟 12306 动态检票二维码，Wallet 卡片也不是车票或乘车凭证。

## 隐私优先

- Gmail OAuth Access Token、原始邮件和候选邮件不发送到开发者运营的服务器；
- 导入截图和 OCR 文字在 Android 设备端处理；
- 应用不申请访问整个照片库，只读取用户本次明确选择的图片；
- 用户确认的行程保存在应用私有的本地数据库中；
- 本地行程不纳入 Android 应用数据云备份；
- 只有用户明确选择添加至 Google Wallet 时，已确认的最少行程字段才会提交给 Google Wallet。

完整说明见[隐私政策](https://chuxing.neko7ina.com/privacy/)。

## 当前支持范围

- Android 8.0 或更高版本；
- 首个数据来源：Gmail；
- 首个内容格式：12306 购票成功邮件；
- 支持邮件正文截图的设备端中文 OCR；
- 其他 12306 页面截图可能缺少形成完整行程所需的信息；
- 不保证第三方 Wallet 卡片显示在系统 At a Glance 区域。

## 安装

正式 APK 只通过 [GitHub Releases](https://github.com/huaxianyan/WalletAssistant/releases) 发布，不通过 Google Play 分发。下载后请核对对应 Release Notes 中公布的 SHA-256。

```text
应用名称：出行
包名：com.neko7ina.wallet.assistant
最低系统：Android 8.0
```

## 开发

项目使用 Kotlin、Jetpack Compose、Room、ML Kit 和 Google Play services，包含两个模块：

- `core`：统一行程模型和与 Android 无关的内容解析；
- `app`：Android 界面、数据导入、本地存储、提醒和 Wallet 输出。

本地构建需要 JDK 17 和 Android SDK 36：

```bash
./gradlew :core:test
./gradlew :app:assembleDebug
```

Google Cloud、Wallet 和 Release 签名使用本机配置，不提交到仓库。维护文档：

- [Google Cloud 与 Wallet 配置](docs/google-cloud.md)
- [Android Release 签名与恢复](docs/signing.md)
- [Cloudflare Pages 部署](docs/cloudflare-pages.md)
- [`v1.0.0` Release Notes 草稿](docs/release-notes-v1.0.0.md)
- [更新记录](CHANGELOG.md)

## 许可证

仓库中由 NeKo7inA 持有版权且未另行标注的源代码、资源和文档，按 [GNU GPL version 3 或任何后续版本](LICENSE)授权。

项目依据 GPLv3 第 7 节提供有限的 [Google SDK 链接例外](LICENSE-EXCEPTION.md)。该例外只允许链接和分发项目使用的 Google 专有 SDK，不改变这些组件自身的许可证或服务条款。

第三方组件和素材继续适用各自条款，详见[第三方许可声明](THIRD_PARTY_NOTICES.md)。

## 独立应用声明

「出行」由 NeKo7inA 独立开发，与中国国家铁路集团有限公司、12306、Google 或其关联机构不存在隶属、合作或官方授权关系。第三方名称仅用于说明数据来源、兼容性和功能边界。
