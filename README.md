# 拾光传输 · Shiguang Transfer

一款为松下相机开发的 Android 图传 App，专注于把照片从相机带到手机。

目前为 **0.2.18-alpha 测试版**，主要根据 Panasonic S5 II（S5M2 / S52）的实际使用反馈迭代。最低 Android 10。其他机型尚未系统验证。

本项目参考 [Angelnable/LumaLink](https://github.com/Angelnable/LumaLink) 的通信协议部分开发，界面及后续改进由拾光传输维护，遵循 Apache-2.0，保留原作者署名；不是完全从零编写，也不是 Panasonic 官方应用。

## 下载与使用

- 安装包与版本说明：[Releases](https://github.com/itshaoyu123-coder/ShiguangTransfer/releases)。请使用标有测试版的 APK。
- 当前安装包：`ShiguangTransfer-0.2.18-alpha.apk`，包名 `app.shiguang.transfer`，versionCode 21。
- [快速使用说明](docs/SHIGUANG_USER_GUIDE.md) · [开发历程](docs/DEVELOPMENT_HISTORY.md) · [版本记录](CHANGELOG.md)

首次开启相机 Wi-Fi，在拾光中填写相机显示的 SSID 与密码，允许手机和相机的连接确认。胶卷会分批出现，不必等整张卡扫描完成。点开 JPG 后再读取清晰版本，选择照片后导入手机。

## 主要功能

- **快速传输**：快速浏览缩略图，按需读取清晰预览，将选中的照片导入手机。
- **稳定连接**：保存连接记录，支持会话保活与异常恢复，减少重复连接操作。
- **分批扫描**：优先显示最新照片，边扫描边浏览、边导入，无需等待整张卡读取完成。
- **后台继续**：提供后台扫描保护和进度通知，导入记录方便查看与管理。

**LUMIX Lab 直达照片编辑页尚未完成。** 当前只探测 Lab 公开的编辑入口；启动主页不能算作成功打开照片。不支持时提供手动从 Lab 图库选择的后备操作，仍需对应版本 APK 或真机日志继续适配。

拍摄日期可从已有预览或已导入文件中补齐，也可手动读取；日期数量不等同于已扫描照片数量。当前逐张补日期仍较慢，0.2.13 真机反馈未见明显提速；未开启自动逐张补全或跨连接日期缓存。

新导入媒体保存至「拾光传输」目录（相册可能归入其他分类）。0.2.18 改为主页跟手分页；后台扫描新增生命周期修正与停滞诊断，但用户反馈的后台停扫尚待确认解决。

## 验证范围

- 用户反馈扫描速度和已扫描照片的预览体验在 0.2.6 阶段已有明显改善。这是特定设备的反馈，不是所有机型的性能承诺。
- 0.2.18 本地相机锁/连接取消定向 **5 项**、传输模块 **48 项**测试通过，并通过 Release 编译、签名校验与 lint（0 errors，50 warnings）。
- 历史协议重点用例与 core 用例见版本验证文档。完整上游协议测试仍有 **4 项已知失败**，不能将针对性检查表述为全套通过。
- 电脑端没有连接相机/手机进行自动化运行验证。锁屏、日期、手势、删除、各格式缩略图及其他固件仍需真机回归。
- 游标只保留在同一协议会话，进程被杀、断开或换卡后会重新扫描；系统强制停止后不能保证继续。
- 日期来自相机元数据或已读取 EXIF，不用导入时间冒充拍摄时间。元数据缺失的照片无法完整按日期归类。

详细记录：[0.2.18 分页与后台扫描](docs/FIX_0.2.18_PAGER_BACKGROUND.md) · [0.2.16 滚动与附近日期](docs/FIX_0.2.16_SCROLL_NEARBY_DATES.md) · [0.2.10 导入缩略图](docs/FIX_0.2.10_HISTORY_THUMBNAILS.md)；更早的 `PROGRESSIVE_*`、`phase-*` 文档包含输入基线的历史资料，不代表拾光已复测其中全部结论。

## 构建

JDK 17、Android SDK 35、Build Tools 35.0.0、Gradle 8.9。可在 Android Studio 打开本仓库，配置 SDK 后使用自带 Gradle Wrapper：

```bash
./gradlew :transport:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Windows 使用 `gradlew.bat`。`local.properties`、本机 SDK 和签名密钥不随仓库分发。CI 的完整针对性命令见 [.github/workflows/android.yml](.github/workflows/android.yml)。

**签名说明：** 当前交付 APK 是测试签名，不是正式发行签名。不同电脑和 CI 自动生成的 debug 密钥不保证一致，不能假定自编译 APK 能覆盖此前安装包。持续发布前应配置并备份固定签名；密钥和密码不得提交仓库。见 [发布说明](docs/PUBLISHING.md)。

## 来源、许可与参与

- 上游：[Angelnable/LumaLink](https://github.com/Angelnable/LumaLink)
- 输入基线：`LumaLink-progressive-source-5.zip`，内部版本 `4.3.11-progressive.3`，见 [来源记录](docs/INPUT_SOURCE.txt)。没有完整的上游 Git 提交链，不能把本仓库的初始提交冒充原项目历史。
- [LICENSE](LICENSE) · [NOTICE](NOTICE) · [第三方声明](THIRD_PARTY_NOTICES.md) · [修改范围](docs/MODIFIED_FILES_SHIGUANG.txt)
- [反馈问题](https://github.com/itshaoyu123-coder/ShiguangTransfer/issues/new/choose) · [贡献说明](CONTRIBUTING.md)

项目由机主提出需求、进行真机测试，并在 Codex 协助下迭代开发。开发历程是根据版本和反馈整理的技术回顾，不是逐条聊天公开，也不是伪造的逐版本 Git 提交记录。

Panasonic、LUMIX 与 LUMIX Lab 的名称用于说明兼容对象；本项目与其或上游作者不存在官方隶属、赞助或背书关系。
