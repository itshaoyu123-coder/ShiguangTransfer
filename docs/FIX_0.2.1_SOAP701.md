# 0.2.1-alpha：针对用户日志中的 SOAP 701

## 日志能确认的事实

- 20:27:51.851，challenge 授权返回 ok、5 个字段、有 session。
- 20:27:52.033，ConnectionManager GetProtocolInfo 返回 HTTP 200。
- 随后的 Browse 为 start=0 count=1，四种 Content-Type/SOAPAction 组合均返回 HTTP 500。
- SOAP fault 给出 errorCode=701、errorDescription=No such object。

因此不能将本次故障仅描述成“未授权”“网络断开”或“临时相机忙”。
相机已经收到请求并返回了目录对象错误。日志本身不能证明唯一根因：
可能涉及重新授权后的播放模式/目录就绪状态，或固件对特殊探测请求的处理。
没有猜测或遍历未公开 ObjectID，也没有改用 get_content_info 文件数作为分页下标。

## 代码修改

1. 结构化解析 SOAP Fault，保留数值、描述和 start/count，不再截断到只剩 HTTP 500。
2. 701 不进行 SOAPAction/Content-Type 四连发，也不进入普通 500 的 busy 退避循环。
3. 重新授权后调用播放模式预热/恢复；成功后等待 1 秒再读目录。
4. 遇到 701，最多恢复一次播放模式，再用相同 ObjectID/StartingIndex 重试。
   如果仍是 701，停止本次扫描并保留已加载内容，显示具体原因。
5. 首次总数探测、下载前刷新授权使用正常 50 项窗口，避免日志中的 count=1 特殊探测。
   这是兼容性处理，不声称已证明 count=1 是固件错误的唯一触发条件。
6. 下载重新授权并成功切换播放模式后同样等待目录就绪。

普通无明确 701 的临时 500 仍保留原有有界退避与兼容头处理。

## 新增回归

- SOAP 701 及命名空间字段解析，保留分页上下文，拒绝 DTD/非 Fault 响应。
- 701 不触发重复头部请求；持续 701 只有一次播放模式恢复。
- 播放模式恢复后同一位置读成功；较早页失败保留已加载最新照片。
- 403 重新授权后，测试相机要求先恢复 playmode 才接受 Browse。
- 测试相机拒绝 count=1 时，扫描与授权刷新改用正常窗口并成功。

仍需 S52 真机验证。安装后先只测试连接和首屏，不用直接下载整卡。
如果还出现 701，请复制从连接/授权开始的完整诊断，重点包含 browse-playback、
upnp-playback-recovery、upnp-page-result 和 upnp-http-error。
