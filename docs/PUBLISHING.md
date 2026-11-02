# 发布与签名说明

## 当前快照
- 仓库：itshaoyu123-coder/ShiguangTransfer
- 版本：0.2.18-alpha，versionCode 21，包名 app.shiguang.transfer
- APK：releases/assets/ShiguangTransfer-0.2.18-alpha.apk
- 校验文件：releases/assets/SHA256SUMS-0.2.18.txt
- APK SHA-256：5fe3af86dc2266877b874eb643c1c79afc55ed354bfa13f6bdac663671cc5ba3
- 测试证书 SHA-256：25455fa192994ce1911c0d04f9fbc452c7e2e51445f81186241ec548839472de

当前 APK 使用测试签名。私钥不放入源码或发布资料，持续覆盖升级需要固定并安全备份现有密钥。CI 自编译的 debug APK 不保证可覆盖该测试包。

## 发布流程
main 分支推送后，Actions 运行针对性检查、编译和 lint。build 成功后，release job 校验仓库中已有的测试 APK，再创建预发布版本 v0.2.18-alpha，标题为“拾光传输 0.2.18-alpha”，说明取 releases/v0.2.18-alpha.md，附件包含 APK 和对应 SHA256SUMS。

已存在同名 Release 时跳过，不覆盖原发布物。临时 GITHUB_TOKEN 仅在 release job 请求 contents:write，不需要公开个人令牌或签名密钥。公开的现成 APK 是已交付给用户的文件，不以新生成的 CI debug 包替换。

后续更新须同步版本号/代码、APK、校验文件、release 说明和工作流目标；核对完整性与签名后再发布。不要让说明中的验证结果超出实际检查范围。

## 历史与验证边界
首次公开上传为 0.2.10 源码快照，后有 0.2.13；旧阶段开发历程根据已有记录整理，没有倒填 Git 提交。

0.2.18 本地定向锁/连接取消 5 项、传输 48 项测试通过，Release 编译和签名校验通过，lint 0 errors / 50 warnings；云端运行结果以对应 Actions 为准。完整上游协议套件仍有 4 项已知失败。

后台停扫、锁屏、主页手势与其他机型兼容仍待真机验证。不能把“修正风险并增加日志”表述为“所有后台问题已解决”。Lab 直达编辑、GPS 和跨连接日期缓存尚未实现。
