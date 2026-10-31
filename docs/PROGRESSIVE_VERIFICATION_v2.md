# 第二版验证记录 — 4.3.11-progressive.2

依据 S5M2 实际日志修复：get_content_info 返回 6829；Browse 的 TotalMatches 返回 6337。第一版错误采用较大值，末页下标变成 6779，超出 Browse 列表。第二版以 Browse 的 6337 为分页依据，末页下标为 6287。一次 Browse 对象可以含 JPG 和 RAW 两个可下载资源，所以资源数也不能用于推进 StartingIndex。

返回 0 条的 DIDL-Lite 响应是有效空页，不应因平台不支持可选 XML 配置而报 Invalid UPnP SOAP XML。现在兼容空页和受限解析器，同时显式拒绝 DTD 并禁止外部实体解析。

## 测试与构建

- 先运行 3 个回归用例，均失败：不同接口总数被混用；RAW+JPG 两个资源被当作两个对象推进；受限 XML 工厂下有效空页失败。修复后通过。
- 最终针对性协议测试：17 项通过（11 分页用例、3 访问调度用例、3 XML 兼容与实体安全用例）。
- Core：6 项通过，包含复制日志最新在前；Transport：2 项通过。
- 最终 `:protocol:test`（上述筛选）+ `:core:testDebugUnitTest` + `:transport:testDebugUnitTest` + `:app:assembleRelease --offline`：BUILD SUCCESSFUL。
- 完整协议测试中保留 4 项已知上游失败（第一版工作时在未修改上游复现，详见 v1 验证记录）。本次完整运行还发现新增相对 URL 测试只排入一次模拟响应、而浏览器需要探测和首屏两次请求；已修正该测试夹具，随后相关用例通过。不声称旧测试全集全部通过。
- APK 签名与 manifest 已校验：包名 com.lumalink.g100d.progressive2，versionCode 98，应用名 LumaLink 渐进版2。独立安装，与第一版和官方原版并存。
- SHA-256: `f2f6ec4566724d3e85300bcb97d78bdbbc79ac5b105ae5b13caa3335097d3083`

## 尚待真机确认

首屏是否包含最后拍摄的照片；扫描时大图/缩略图/下载是否顺畅；不同固件的相机列表顺序。这里使用了真实日志对应的计数问题构造模拟响应，但不能代替 S5 II 实测。
