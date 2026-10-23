# 构建与签名

需要 JDK 17、Android SDK 35、Build Tools 35.0.0。Gradle Wrapper 使用 8.13。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
```

正式包为 `app/build/outputs/apk/release/app-release-unsigned.apk`。项目有意不在 Gradle 文件中保存发布签名配置。维护者使用仓库外的 PKCS12 密钥，通过 Build Tools 的 `zipalign` 和 `apksigner` 签名：

```text
zipalign -p 4 app-release-unsigned.apk app-aligned.apk
apksigner sign --ks <私有目录>/dieka-release.p12 --ks-key-alias dieka-release --ks-pass env:DIEKA_SIGNING_PASSWORD --key-pass env:DIEKA_SIGNING_PASSWORD --out dieka-v0.5.0-release.apk app-aligned.apk
apksigner verify --verbose --print-certs dieka-v0.5.0-release.apk
zipalign -c -p 4 dieka-v0.5.0-release.apk
```

密码仅在签名进程的临时环境变量中使用，使用后清除；不要写入命令参数、源码、日志、Issue 或 GitHub 附件。也可让 `apksigner` 交互询问密码。仓库中的命令示例不包含密钥或密码。

维护者更新时保留包名、别名和签名密钥，增加 `versionCode` 与版本号。私钥丢失后无法正常覆盖更新已安装的官方 APK；请在离线介质另存密钥，并在密码管理器保存密码。克隆本仓库的开发者应使用自己的签名，不应索取官方私钥。

发布前运行现有 JVM 和 Android 测试，在全新专用模拟器额外验证正式签名 APK 的启动、系统身份验证、卡片保存、冷启动读取与旧版并存。测试只使用虚拟卡片，不连接个人卡库。公开版尚无卡库导出迁移功能。

发布 `v0.5.0` 时使用 GitHub **Pre-release**，附正式 APK、`SHA256SUMS.txt` 和中文说明。源码不提交 APK。当前未设置自动发布、Pages 或 Packages。
