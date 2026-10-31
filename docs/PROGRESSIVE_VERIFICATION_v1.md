# 验证记录 — 4.3.11-progressive.1

日期：2026-10-30。基线：Angelnable/LumaLink main 24a0156。

## 已执行

- 新增协议/访问调度测试：13 项通过。
- 完整协议测试：46 项，其中 42 项通过，4 项失败。
- 在未修改的上游 24a0156 重新执行失败用例，复现完全相同的 4 项失败，因此保留旧测试，不改动产品授权逻辑或降低断言要求。
- Core 单元测试：5 项通过。
- Transport 单元测试：2 项通过。
- `:app:assembleDebug :core:testDebugUnitTest :transport:testDebugUnitTest`：BUILD SUCCESSFUL。
- Debug APK 已通过 apksigner 校验，包名 com.lumalink.g100d.progressive，minSdk 29 / targetSdk 35。
- 独立代码复查发现的总数少报、短页漏读、下载后缩略图不恢复、刷新后旧回调竞争均已处理。前两项由回归测试先复现再修复；请求恢复由访问调度测试覆盖。进度回调在 Main 上校验扫描世代并发布。

## 上游已有失败（本修改版也保留）

1. LumixControlClientTest.sendsOnlyFixedPlaymodeCommand
2. LumixControlClientTest.waitsForCameraApprovalAndStoresSession
3. LumixHttpProtocolTest.handshakeFallsBackToGetStateWhenGetInfoIsMissing
4. UpnpContentDirectoryClientTest.retriesLumixPrefixedControlUrlAfter403

上游最后的 CI 提交注明暂时只构建 APK，等待协议测试夹具更新。这里记录失败，不声称完整测试全部通过。

## 实现取舍和限制

- 扫描、预览和下载仍串行访问相机，在页间交替执行，以避免授权互相失效；已经开始的一次请求可能仍需等到响应或超时后才能让路。
- 相机不提供总数时，明确提示并回退到渐进正向扫描，无法保证首屏最新。
- 缩略图仅对列表中进入组合的项目发起请求（含 Lazy 布局的小量预取），离屏取消，位图缓存最多 160 项；回看很久以前的照片可能需要重新获取。
- 首批最新窗口的顺序、不同固件授权、尾页异常仍需 S5 II 实测。此处没有真实相机或 Android 设备，不声称真机验证成功。
- 没有创建远程分支或发布到原作者仓库；交付为独立测试安装包和源码。

## 最终交付安装包

`:app:assembleRelease`：BUILD SUCCESSFUL（2m 44s）。Release APK 已通过 apksigner v2 签名校验，应用名 LumaLink 渐进版，版本 4.3.11-progressive.1，包名 com.lumalink.g100d.progressive。独立安装，不覆盖原版。

SHA-256: `7bab46a4750520fd708f187a1fe02b540f5856b36934eed3985dfdc0e1c81285`
