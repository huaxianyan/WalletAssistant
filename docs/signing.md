# Android Release 签名

正式包名为 `com.neko7ina.wallet.assistant`。

正式签名证书 SHA-256：

```text
eafaba2f329a58d091d1641630339f2cc3e81bb72c4e3e048fe5f068fe2be2be
```

Release KeyStore 和密码不进入 Git。完整恢复包保存在：

```text
\\192.168.7.216\homes\NeKo7inA\dev\Android Signing\WalletAssistant
```

## 恢复

1. 将 NAS 中 `WalletAssistant` 文件夹完整复制到：

   ```text
   %USERPROFILE%\.android\signing\WalletAssistant
   ```

2. 确认目录包含：

   ```text
   wallet-assistant-release.p12
   signing.properties
   certificate.txt
   RECOVERY.txt
   ```

3. 克隆仓库并执行：

   ```bash
   ./gradlew :app:assembleRelease
   ```

4. 使用 Android SDK 的 `apksigner verify --print-certs` 核对 Release APK 的证书摘要与 `certificate.txt`。

`signing.properties` 和 KeyStore 都属于发布凭据，不得提交到仓库、Issue、Release 附件或构建日志。
