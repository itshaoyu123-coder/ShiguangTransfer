# Latest-first progressive camera browser

Approved in conversation: show the latest 50 entries first, append older pages incrementally, allow thumbnails and full previews before the scan completes, pause scanning while a full preview or transfer is active. Preserve selection during updates, show scan progress, preserve real filenames and advertised thumbnail URLs, and remember working control endpoints. Keep camera authorization plus each request serialized. Preserve read-only behavior and existing download implementation.

Approaches: all-at-once scanning (current, too slow); newest-first progressive scanning (chosen); on-demand-only paging (deferred). Camera order and tail anomalies require S5 II testing. Unknown totals fall back to progressive forward scanning with an explicit warning. Partial errors keep loaded files and mark the scan incomplete.
