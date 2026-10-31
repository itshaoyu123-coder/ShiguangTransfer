# 贡献说明

感谢反馈或改进。项目目前主要面向 S5 II 测试，没有通用机型兼容承诺。

- 报告问题请提供相机型号/固件、手机/系统、App 版本、操作步骤和相关时间段日志。
- 公开日志请去除密码、SSID、序列号、会话和授权 challenge 参数；无需提交原始照片，可用可公开的测试素材。
- 修改通信逻辑时保持机型差异说明，分页对象数不要用 JPG/RAW 文件数替代。
- 新增拍摄、写卡、删除相机原片等命令不属于当前只读导入范围，需先讨论产品行为与测试条件。
- 保留 LICENSE、NOTICE 和上游署名；修改的派生文件添加显著修改说明。
- 写明实际运行的测试，以及需要真机验证的部分。完整上游协议套件存在已知失败，请勿通过删除测试或随意改预期来伪装全绿。

基础检查：

```bash
./gradlew :transport:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

协议重点检查命令见 `.github/workflows/android.yml`。完整开发背景见 `docs/DEVELOPMENT_HISTORY.md`。
