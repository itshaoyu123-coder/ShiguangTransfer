# Execution ledger — plan: docs/superpowers/plans/2026-10-30-latest-preview.md
User approved design and execution. Native inline implementation.
Pre-flight: protocol snapshots feed ViewModel; existing session protocol is LumixHttpProtocol.
Ruling: keep serial requests, interleave at page boundaries rather than parallel camera requests — avoids invalidating grants.
Baseline: ./gradlew :protocol:test blocked at distribution download (Java network path); curl download succeeded; configuring toolchain.

Protocol: initial eight progressive tests passed. Review added two tests reproducing skipped short pages and underreported totals (12 tests, 2 failed); then fixed both. Queued-transfer resume test initially failed to compile until the new wait method was implemented. Final focused run: 13 tests, 0 failures, BUILD SUCCESSFUL (41s).
Independent review by review_progressive_browser identified total calibration, short pages, thumbnail resumption, stale progress publication, and connection indicator copy; all addressed. Main-thread snapshot publication serializes generation checks with refresh.
Ruling: when total count is unavailable, use explicit-warning forward progressive loading rather than pretend newest-first is guaranteed.
Ruling: bound thumbnail bitmap cache to 160 entries to avoid retaining thousands of bitmaps.
Task 1: implementation and focused verification complete; full upstream suite pending.
Task 2: implementation complete; Android compile/build pending.

Full protocol suite: 46 tests, 4 failures. Unmodified upstream 24a0156 rerun reproduced exactly the same four failures: LumixControlClientTest.sendsOnlyFixedPlaymodeCommand; LumixControlClientTest.waitsForCameraApprovalAndStoresSession; LumixHttpProtocolTest.handshakeFallsBackToGetStateWhenGetInfoIsMissing; UpnpContentDirectoryClientTest.retriesLumixPrefixedControlUrlAfter403. Existing CI commit also states fixtures are outdated; no unrelated test expectation edits made.
Build environment initially lacked jlink; installed a complete JDK 17 and rerunning Android build.

Task 2: Android debug build successful; Core 5/5 and Transport 2/2 tests passed. Debug APK signature valid and distinct application ID verified via aapt.
Task 3: independent review findings addressed; source diff whitespace check passed. Packaging smaller release APK pending; S5 II validation remains a documented device test, not a completion claim.

Final package: release build successful, signature and manifest checked; app ~3.2 MiB. All review findings addressed; no deferred new-code review findings. Task 3: packaging complete; device validation explicitly pending. Branch retained locally; no remote mutation.

## Progressive.2 correction
Device logs show get_content_info=6829 but Browse TotalMatches=6337 and a valid empty response. Earlier total-calibration ruling is superseded: those counts must not be mixed for offsets. Object metadata governs paging; resource files govern loaded-file count. RAW+JPEG object progress and Android XML compatibility covered. Final focused protocol 17/17, Core 6/6, Transport 2/2; release assemble and signature checks pass. Legacy four failures remain documented. App ID progressive2 provides separate installation.

## Progressive.3 reliability correction
Device feedback: preview/download work with 298 resources from 150 objects, but older-page scanning aborts and duplicate failure banners persist. Shared Android-compatible XML parsing now covers device descriptions; advertised service URLs are preferred. Added quoted-first SOAPAction, fault diagnostics, bounded busy/network retries outside the camera lock, and one nonfatal partial-scan notice. A successful progress snapshot clears stale errors. HTTP500 root cause remains unconfirmed without fault data. Focused protocol 27/27, Core 6/6, Transport 6/6 passed; release assemble succeeded. Manifest versionCode99 and matching v2 certificate verified. S5 II validation remains pending.
