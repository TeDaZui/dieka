# 公开版 v0.5.0 验证记录

验证日期：2026-10-23（Asia/Shanghai）。应用包名 `io.github.tedazui.dieka`，名称“叠卡（公开版）”。

## 构建与现有测试

环境：Windows、JDK 17、Gradle 8.13、AGP 8.11.1、Kotlin 2.1.21、compile/target SDK 35、min SDK 30。专用全新 Android 11 / API 30 / x86_64 Pixel 4 模拟器。

| 检查 | 结果 |
| --- | --- |
| Debug APK、Android 测试 APK、Release APK 构建 | 通过；Release 启用 R8 压缩，正式 Lint 检查通过 |
| JVM 单元测试 | 28 项通过，0 失败、0 错误 |
| Android 基础仪器测试（Debug 构建） | 34 项通过，66.714 秒 |
| 实际 Android Keystore 集成测试（Debug 构建） | 1 项通过，11.045 秒 |
| 正式 APK 签名及 ZIP 对齐验证 | 通过；RSA 3072 位专属签名、APK v3 签名 |

JVM 覆盖：资料校验、AES-GCM、会话策略、排序、回收站快照、到期日期、裁剪坐标、切卡连续进度、应用关联和触觉开关。

Android 测试覆盖：DEMO 卡片浏览、完整卡号复制及其他字段显隐、图片裁剪、排序、回收站、资料编辑、应用选择与跳转返回、界面重建、更新日志、触觉偏好、JSON 兼容。实际 Keystore 测试完成系统凭据验证、加密写入、排序后重读、编辑/移除/撤销/恢复、旧快照兼容、原子写入失败保护及过期清理。

## 正式签名 APK 的独立验收

在同一专用模拟器中移除测试用的公开版 Debug 包后，安装将要发布的正式 APK，再同时安装原自用版 APK。没有连接或操作真实手机。

1. 正式版可以启动；添加卡片触发真实系统凭据验证。
2. 通过界面填写公开虚拟卡号 `4111111111111111`、测试银行 `PublicDemoBank` 和名称 `ReleaseDemo`，保存成功。
3. 强制结束正式版进程，再次启动时显示锁定界面；再次系统验证后，先前保存的卡片可读取。
4. 原自用版 `com.dieka.wallet` 可同时安装并启动，初始没有公开版的卡片。为它建立另一张虚拟测试卡 `OldDemoBank`，冷启动后可以独立读取。
5. 回到正式版，原有 `PublicDemoBank` 卡片仍在，未出现旧版测试卡；系统分配不同应用 UID 和数据目录。

这些步骤验证了实际正式 APK 的启动、系统认证、加密存取和应用隔离。仪器测试使用 Debug 构建；不能把 Debug 仪器测试称为在压缩后的正式 APK 上执行。

## 复现命令

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:assembleRelease
```

以下命令仅用于新建、无个人数据的专用模拟器，所有 adb 命令必须指定该模拟器序列号：

```text
adb -s <专用模拟器> install app/build/outputs/apk/debug/app-debug.apk
adb -s <专用模拟器> install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <专用模拟器> shell am instrument -w -e notClass com.dieka.wallet.VaultOrderDeviceTest io.github.tedazui.dieka.test/androidx.test.runner.AndroidJUnitRunner
```

Keystore 测试先给该模拟器设置测试锁屏 PIN；需要显式启用，且测试检查硬件为 `ranchu` 和卡库不存在：

```text
adb -s <专用模拟器> shell am instrument -w -e class com.dieka.wallet.VaultOrderDeviceTest -e isolatedSortPersistence true -e testPin <测试PIN> io.github.tedazui.dieka.test/androidx.test.runner.AndroidJUnitRunner
```

## 发布文件身份

versionCode 5，versionName 0.5.0，正式 APK 2,747,689 字节。

APK SHA-256：

```text
b21debc80093746816e481cba451af97de1694c42ce3e8c622bf6f052e5f62ad
```

签名证书 SHA-256：

```text
6f8cc0322d6f86de54b4c2666421d201d4954ed9d1e56d07d0c79aa4e80ee958
```

Manifest 使用完整入口类名 `com.dieka.wallet.MainActivity`，不申请联网或读取全部应用权限；公开版应用 ID 与源代码命名空间不同。

## 验证边界

- 没有进行真实手机个人卡库测试，也没有迁移旧版数据。README 中的两张 DEMO 图沿用同版本开发时生成的虚拟卡片截图。
- 本轮仅实测 API 30；Android 13+ 系统复制提示、厂商图片选择器、不同机型、横屏及超大字体尚未全面验证。
- 触觉验证覆盖触发路径和开关，不代表已验证不同硬件的手感。没有承诺真实手机 60/120 fps。
- 尚无数据导出、迁移或自动备份功能；卸载、清除数据或重置设备锁屏可能使卡库无法恢复。
- 尚未进行独立专业安全审计，因此将本版本标记为首次公开测试版。
