# GitHub 自动更新发布说明

更新仓库：`https://github.com/aojo001/pandian`

## App 更新流程

1. App 启动联网检查 GitHub 最新 Release。
2. Release 标签版本高于当前 `versionName` 时弹出更新提示。
3. 用户点击立即更新，App 下载 Release 中的 APK。
4. Android 首次使用时要求授权“允许安装未知应用”。
5. 下载完成后打开系统安装确认页面。

## 发布新版本

每次发布前必须同时修改 `app/build.gradle.kts`：

```kotlin
versionCode = 2
versionName = "1.0.1"
```

要求：

- `versionCode` 必须比手机已安装版本大。
- `versionName` 必须与 GitHub Release 标签对应，例如 `1.0.1` 对应 `v1.0.1`。
- 新 APK 必须与手机中旧 APK 使用同一个签名证书，否则 Android 会拒绝覆盖安装。

构建 APK：

```bash
./gradlew assembleDebug
```

生成位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

在 GitHub 仓库中：

1. 打开 Releases。
2. 点击 Draft a new release。
3. Tag 填写比当前版本高的版本，例如 `v1.0.1`。
4. 上传一个 `.apk` 文件作为 Release 附件。
5. 填写更新说明并发布，不能只创建普通 Git tag。

## 注意事项

- 当前仓库必须保持公开；公开仓库检查更新不需要在 App 中保存 GitHub Token。
- Release 中必须至少有一个文件名以 `.apk` 结尾的附件。
- 普通 `git push` 只更新源代码，不会触发手机客户端更新。
- Android 不允许普通 App 静默安装 APK，因此用户仍需点击系统安装确认。
