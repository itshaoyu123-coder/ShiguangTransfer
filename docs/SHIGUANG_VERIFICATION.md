# 拾光传输 0.2.0-alpha 验证记录

日期：2026-10-30。设备目标：用户的小米 15（澎湃 OS 4）与 Panasonic S5 II，约六千项目的 256GB 卡。

## 已完成的本机验证

- 使用 JDK 17、Gradle 8.9、SDK/Build Tools 35.0.0 构建。
- 按输入文档的要求先运行原始 protocol 全集：57 项，4 项失败，与文档记载名称一致。
- 新版针对性测试共 46 项，零失败：
  - 渐进分页与恢复：18 项（含新增 3 项检查点恢复测试）。
  - 相机访问调度：3 项。
  - 设备描述解析：6 项。
  - Android XML 兼容与实体拒绝：3 项。
  - Core：6 项。
  - Transport：10 项（含新增 2 项 Wi-Fi 输入验证、2 项续传范围验证）。
- `:app:assembleDebug` 与 `:app:lintDebug` 通过；lint 无错误，仍有旧 targetSdk、依赖版本、上游 API 与样式建议等警告。
- `:app:assembleRelease` 通过；测试发布配置使用本机 debug 签名，而非正式商店签名。

新增恢复测试实际检查：

1. 较早页失败后保留最新 50 项，“继续扫描”重新核对对象总数后直接读未完成窗口。
2. 对象总数改变时丢弃旧检查点，从新卡/新列表的最新窗口开始。
3. 显式刷新不复用旧游标。

## 已有的四项失败

原始输入基线中的失败为：

- LumixControlClientTest.sendsOnlyFixedPlaymodeCommand
- LumixControlClientTest.waitsForCameraApprovalAndStoresSession
- LumixHttpProtocolTest.handshakeFallsBackToGetStateWhenGetInfoIsMissing
- UpnpContentDirectoryClientTest.retriesLumixPrefixedControlUrlAfter403

本次未修改它们的断言以制造“全集通过”。针对性命令与 CI 明确列出测试范围；
不能把上述 46 项通过解释成原始完整测试套件全部通过。

## 未完成的设备验证

- 没有连接用户的相机或手机，无法确认真实下载失败原因或实际修复结果。
- 模拟器安装完成，但该电脑没有可用的模拟器加速；两次软件启动均未达到 adb 在线状态。
  已结束本次启动的模拟器，未安装驱动或改动 Windows 安全/虚拟化设置。
- 因此没有完成界面启动、布局截图或 Android Keystore 真机验证，不把成功编译当成运行验证。
- 未验证小米的重复 Wi-Fi 确认策略、相机关闭时行为、长时间锁屏或全卡下载。
- HTTP 500 已沿用上传版的有界重试与 fault 诊断，没有相机 fault 日志就不能确认其唯一原因。

## 用户的第一轮测试顺序

1. 覆盖安装拾光 0.2.0，先确认首页、胶卷/导入记录切换、连接表单、大字号显示正常。
2. 首次在 App 内填写相机 SSID/密码并连接，确认无需先手动进系统 Wi-Fi；允许首次系统与相机确认。
3. 相机热点保持开启，关闭再打开 App，观察是否自动请求重连；明确记录系统是否再次要求确认。
4. 核对首屏包含最后拍摄照片；观察缩略图逐批出现，而非等待全卡。
5. 点一张 JPG：取回大图后留在预览界面，观察扫描进度是否继续；再关闭。
6. 先导入一张 JPG，再导入一个 RAW 和一段短视频；在导入记录点“打开”，核对文件能读取。
7. 模拟扫描暂停，点“继续扫描”；重新扫描则应从最新页开始，不能混用对象数与文件数。
8. 再测试断网、失败重试、取消单个任务、剩余队列和锁屏。不要直接先跑整张卡的批量导入。

如果下载仍失败，请复制“更多 → 诊断日志”中包含 transfer-access、HTTP 状态和文件错误的段落。
本次没有收集或上传用户照片、Wi-Fi 密码和诊断数据。
