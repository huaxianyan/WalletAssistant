# 第三方许可声明

「出行」使用以下第三方软件和素材。版本以 [`gradle/libs.versions.toml`](gradle/libs.versions.toml) 及对应的 Gradle 依赖解析结果为准。

项目的 GPLv3 许可不替代、不扩展也不重新声明这些第三方组件的许可证或服务条款。允许链接 Google SDK 的附加许可仅见 [`LICENSE-EXCEPTION.md`](LICENSE-EXCEPTION.md)；Google SDK 仍完全受其自身条款约束。

## AndroidX

应用使用 AndroidX Activity、Core、Lifecycle、Jetpack Compose、Material 3 和 Room。

- Copyright：The Android Open Source Project
- License：Apache License 2.0
- 源码：https://cs.android.com/androidx/platform/frameworks/support
- 许可全文：[LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt)

## Kotlin 与 kotlinx.serialization

应用使用 Kotlin 标准库和 kotlinx.serialization。

- Copyright：JetBrains s.r.o. and Kotlin contributors
- License：Apache License 2.0
- 源码：https://github.com/JetBrains/kotlin
- 源码：https://github.com/Kotlin/kotlinx.serialization
- 许可全文：[LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt)

## ML Kit 中文文字识别

应用使用设备端 ML Kit 中文文字识别模型：

```text
com.google.mlkit:text-recognition-chinese:16.0.1
```

该组件受 ML Kit Terms of Service 约束：

https://developers.google.com/ml-kit/terms

## Google Play services

应用使用以下 Google Play services 组件：

```text
com.google.android.gms:play-services-pay:16.5.0
```

这些组件的 Maven 元数据声明适用 Android Software Development Kit License Agreement：

https://developer.android.com/studio/terms.html

## Jakarta Mail Android

应用使用以下组件连接用户配置的 IMAP 邮箱：

```text
com.sun.mail:android-mail:1.6.7
com.sun.mail:android-activation:1.6.7
```

- Copyright：Eclipse Foundation and individual contributors
- License：EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
- 源码：https://github.com/javaee/javamail
- 许可全文：[LICENSES/JavaMail-LICENSE.md](LICENSES/JavaMail-LICENSE.md)
- Notice：[LICENSES/JavaMail-NOTICE.md](LICENSES/JavaMail-NOTICE.md)

## Google Wallet Android codelab 素材

`app/src/main/res/drawable/add_to_google_wallet_button_foreground.xml` 来源于 Google Wallet Android codelab。

- Source：https://github.com/google-wallet/android-codelab
- Copyright 2022 Google Inc.
- License：Apache License 2.0
- 许可全文：[LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt)

## 商标

Android、Gmail、Google、Google Play、Google Wallet 和 ML Kit 是 Google LLC 的商标或服务名称。Kotlin 是 Kotlin Foundation 的商标。

第三方名称仅用于说明兼容性、依赖关系和数据处理边界，不表示第三方对「出行」提供认可、赞助或官方授权。
