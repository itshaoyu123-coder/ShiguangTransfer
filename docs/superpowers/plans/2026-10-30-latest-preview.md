# Latest-first Preview Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Browse recent camera photos without waiting for a complete card scan.
**Architecture:** Snapshot callbacks after each reverse page; shared camera lock held for one authorization/request transaction, never for a whole scan. ViewModel publishes snapshots and pauses before the next page for previews/transfers.
**Tech Stack:** Existing Kotlin, coroutines, OkHttp, Jetpack Compose; no new runtime dependencies.
**Spec:** docs/superpowers/specs/2026-10-30-latest-preview-design.md

## Global Constraints
- Keep existing read-only camera operations and original download implementation.
- Page size 50; newest first; preserve already loaded files on failure.
- Build a separate application ID so testing does not replace the installed upstream app.

## Review Focus
- Unknown totals and invalid tail windows: explicit fallback/warning, no fake completion.
- 403/500 recovery, cancellation, and concurrent downloads: no lock held across whole scan.
- Refresh/disconnect: cancel old jobs and reject stale callbacks.
- RAW+JPEG: retain both and use the camera-provided original filename/thumbnail.
- New pages: do not clear selection or move the current preview.

### Task 1: Protocol paging and metadata
Files: protocol/.../UpnpContentDirectoryClient.kt, LumixHttpProtocol.kt, CameraAccessGate.kt; tests: UpnpContentDirectoryClientTest.kt, CameraAccessGateTest.kt.
Interfaces: browseNewest(onProgress: suspend (CameraBrowseProgress) -> Unit, beforePage: suspend () -> Unit); snapshots include files, total, scanned count, completion and warning.
- [x] Add tests for reverse windows (including non-multiple totals), progress before finish, metadata, failures and cancellation.
- [x] Run tests (expect failure before implementation).
- [x] Implement reverse paging with per-page serialization, recovery and incremental callbacks. Preserve original file/thumbnail metadata and cache bad endpoint choices.
- [x] Run protocol tests; expect pass for new tests; record existing fixture failures separately.
- [x] Commit protocol changes.

### Task 2: Progressive UI and lifecycle
Files: transport/.../ConnectViewModel.kt, ConnectSheets.kt; app/build.gradle.kts.
- [x] Publish page snapshots, keep selections, manage cancellation and session changes.
- [x] Enable previews while scanning, pause on full preview/transfer; show scanned/total and partial failure. Label selection as loaded items.
- [x] Set separate application ID/version suffix for side-by-side testing.
- [x] Compile/build APK and inspect resulting manifest if toolchain becomes available.
- [x] Commit UI changes.

### Task 3: Review and delivery
- [x] Review diff for all Review Focus conditions and fix consequential issues.
- [x] Run available tests/build; record exact blockers.
- [x] Package source and patch plus installable APK if build succeeds, with device test instructions.

Full upstream protocol suite retains four baseline failures; see docs/PROGRESSIVE_VERIFICATION.md. No claim of a completely green legacy suite or S5 II hardware validation.
