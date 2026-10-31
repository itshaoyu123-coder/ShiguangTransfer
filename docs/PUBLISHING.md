# 发布与签名说明

## 当前公开快照

- 仓库：itshaoyu123-coder/ShiguangTransfer
- 版本：0.2.10-alpha，versionCode 13，包名 app.shiguang.transfer
- APK：ShiguangTransfer-0.2.10-alpha.apk
- SHA-256：54bbb5f61ff00a1ee37c7134b7ebb128b47d5e1bb17c12c5729aa5c0f8d9eea8
- 测试证书 SHA-256：25455fa192994ce1911c0d04f9fbc452c7e2e51445f81186241ec548839472de

当前 APK 使用开发测试签名。签名私钥不放入仓库，也不放入发布资料包。发布现成 APK 不要求公开私钥。

## 第一次上传

源码应以目录/文件形式出现在仓库根目录，包含 app、protocol、transport、gradlew、LICENSE、NOTICE 等，而不是仅上传一个源码 ZIP。

当前仓库初次上传是 0.2.10 源码快照。开发历程与版本记录由已有记录整理，不回填、不伪造旧提交。

GitHub 连接必须对 itshaoyu123-coder/ShiguangTransfer 有写权限；只读连接不能创建提交。不要把令牌、密码发到聊天或写入源码。

## 发布 APK

在 Releases 创建预发布版本：

- Tag：v0.2.10-alpha
- Title：拾光传输 0.2.10-alpha
- 选中 pre-release（测试版）
- 版本说明使用 releases/v0.2.10-alpha.md
- 附件为已验证的 APK、可选源码 ZIP 和 SHA256SUMS.txt

发布资料包中的 release-assets 是待上传附件，不必放入源码提交。不要先把同一测试版重新编译后冒充原文件；应核对 SHA-256。

本地签名检查：

```text
apksigner verify --print-certs ShiguangTransfer-0.2.10-alpha.apk
```

## 后续版本

- 当前 release 构建仍引用 debug signingConfig，不能把它误称为正式发行签名。
- 不同电脑/CI 生成的 debug key 可能不同。持续覆盖升级需要固定且安全备份的签名密钥；正式签名配置需要单独完成。
- CI 的 build job 运行针对性检查；仅此仓库 main 分支的检查成功后，release job 发布 releases/assets 中已有的 0.2.10 APK。不是将 CI 新生成的 debug artifact 当作旧安装包发布。后续正式签名流程仍需单独完成。
- 保留现有测试密钥的私人备份，后续不能从公开 APK 恢复签名私钥。更换签名时需要另行制定已有测试用户的迁移方式。
- 任何正式私钥、密码、个人 SDK 路径都不应提交到 GitHub。

## 首次公开前后的验证

0.2.10 最近一次针对性验证为 transport 37 项测试、Release 编译、签名校验和 lint 0 errors / 70 warnings。协议完整套件的 4 个历史失败没有隐藏。上传后的 Linux Actions 尚需实际运行，不能提前标为通过。

Lab 编辑跳转、全部机型、全卡/长时间锁屏仍存在待验证项，发布说明应保持这一边界。

## 首次发布自动化

仓库保留这一份已验证 APK（约 3MB）及 SHA-256，便于 Actions 发布完全相同的测试二进制文件。release job 只对 itshaoyu123-coder/ShiguangTransfer 的 main 推送或手动运行生效，依赖 build 成功；临时 GITHUB_TOKEN 只在该 job 请求 contents:write，不需要个人 token 或私钥。已有同名 Release 时跳过，避免覆盖发布物。
