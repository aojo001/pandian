# GitHub 自动上传约定

仓库：`https://github.com/aojo001/pandian`

## 工作约定

- 每次完成代码修改后先执行必要的编译或测试。
- 验证通过后提交本次相关改动并推送到 GitHub。
- 不上传 `local.properties`、构建缓存、IDE 配置、签名密钥和其他本机敏感文件。
- 普通代码更新推送到 `main`。
- 需要触发手机客户端自动更新时，还必须提高 `versionCode`、`versionName`，并创建带 APK 附件的 GitHub Release。
- 推送或创建 Release 属于外部发布操作；仅发布本项目范围内已经验证的改动。
