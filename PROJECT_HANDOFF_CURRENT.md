# Hermex Android — Project Handoff (Current State)

**Last updated:** 2026-07-18 (Phase 6B — reliable interrupt)  
**Current version:** v0.1.38 (versionCode 38)  
**HEAD commit:** `5ff5ac4` — fix tool card display: persist live tool call state through MessageCompleted  
**Branch:** `master` (uncommitted Phase 5E + Phase 6B changes)  
**Repository:** `git@github.com:gravol/hermex-android.git`  
**Working directory:** `/home/jeff/HermexAndroid` (canonical)

---

## Verified Project Root

| Field | Value |
|---|---|
| Canonical path | `/home/jeff/HermexAndroid` |
| Remote URL | `git@github.com:gravol/hermex-android.git` |
| Branch | `master` |
| Latest commit | `5ff5ac4` — fix tool card display (v0.1.36) |
| Build command | `./gradlew assembleRelease --no-configuration-cache` |
| APK output | `app/build/outputs/apk/release/app-release.apk` |
| Version | v0.1.38 (versionCode 38) |
| Completed phase | **Phase 6B — reliable interrupt support** |
| Next phase | **Background WebSocket keepalive** |

> **Stale copy: `/mnt/storage/projects/HermexPort`** — Different git history (7 commits, no remote, version 0.2.0). Abandoned early port that was never pushed. **Do not edit.** The canonical repo is `/home/jeff/HermexAndroid`.

---

## Project Overview

### What the application does
Hermex Android is a native Kotlin/Compose chat client for the **Hermes Agent** AI assistant. It connects to a self-hosted Hermes Dashboard (running on a Linux server at Tailscale IP `100.80.204.66`) to provide a mobile-first conversational interface with live token streaming, tool-call visualization, thinking/reasoning blocks, session management, and tool approval.

### Architecture summary
- **Kotlin + Jetpack Compose** (Material 3) — single-module app with core library modules
- **MVVM with Compose snapshot state** — `mutableStateOf` used instead of `StateFlow` to avoid conflation during rapid streaming events
- **Plain OkHttp** — no Retrofit, no Hilt/Dagger. Manual DI via `AppModule` singleton
- **Two networking stacks coexist:**
  1. **JSON-RPC/WebSocket (port 9119)** — `DashboardApiClient` + `WsConnectionManager` + `JsonRpcClient` — primary path
  2. **Legacy REST+SSE (port 8650)** — `ApiClient` + `SseParser` — fallback, cleanup deferred

### Key technologies
| Layer | Library | Version |
|---|---|---|
| Language | Kotlin | 2.0.20 |
| UI | Jetpack Compose (Material 3) | BOM 2024.06.00 |
| Build | AGP + Gradle | 8.5.0 |
| HTTP/WS | OkHttp | 4.12.0 |
| Serialization | kotlinx.serialization | 1.7.3 |
| Persistence | Room + EncryptedSharedPreferences + DataStore | Room 2.6.1 |
| DI | Manual (`AppModule` singleton) | — |
| Navigation | Navigation Compose | 2.8.0 |
| Images | Coil | 2.7.0 |

### Relationship to other projects
This repo (`gravol/hermex-android`) is the canonical, actively developed native Kotlin port. The `~/hermes-android` directory alongside it is an abandoned Flutter/Dart project — do not touch.

---

## Current Status

### What works (Dashboard JSON-RPC/WS stack)
- **Dashboard setup flow** — URL entry + password login, status check, credential persistence via `KeychainStore`
- **Cookie-based auth** — `POST /auth/password-login` → session cookies via `CookiePersistor` + `NetworkCookieJar`
- **401 auto-relogin** — `DashboardAuthenticator` intercepts 401, re-logs in with stored password, retries request
- **WebSocket lifecycle** — `WsConnectionManager` handles connect/disconnect/reconnect with exponential backoff
- **Ticket-based WS auth** — `POST /api/auth/ws-ticket` → 30s single-use ticket → WS upgrade
- **JSON-RPC request/response** — `JsonRpcClient` with `CompletableDeferred` pending map, timeout handler
- **Notification routing** — 18 event types parsed (`message.delta`, `thinking.delta`, `reasoning.delta`, `tool.generating/start/complete`, `approval.request`, `clarify.request`, `gateway.ready`, `run.started/completed`, `message.completed`, `session.info`, etc.)
- **Session list** — `SessionsViewModel.loadDashboardSessions()` via `session.list` RPC
- **Session resume** — `DashboardChatViewModel.loadMessages()` via `session.resume` RPC with **session ID normalization** (resolved live sid replaces DB key)
- **Chat streaming** — `DashboardChatViewModel` sends via `prompt.submit`, consumes notifications for real-time deltas
- **Auto-scroll** — `scrollGeneration` counter for instant scroll, `scrollToItem` (not animate); two-step height-compensation algorithm
- **Thinking/reasoning display** — collapsible blocks with delta concatenation
- **Tool call visualization** — started/progress/completed UI with `ToolCallCard` components. Now correctly parses real server event names (`tool.generating`/`tool.start`/`tool.complete`). Tool cards persist through message completion and session reload.
- **Keyboard anchoring** — frame-based keyboard settle (replaced fixed delay with `withFrameNanos` × 3)
- **Reconnect resume** — on WebSocket reconnect, calls `session.resume` to re-register the live session (fixes 4001 error after reconnect)
- **Stop generation** — `stopStreaming()` sends `session.interrupt` RPC, clears per-message `isStreaming` flag on the last assistant message so blinking cursor / thinking ticker / typing dots disappear immediately, and dismisses any visible approval or clarify dialog. Same fix applied to legacy SSE stack.
- **Tool approval dialog** — `PendingApproval` state model, Compose `Dialog` with Approve/Deny buttons. Notification handler correctly sets state from `ApprovalRequest` events, no more auto-deny in `JsonRpcClient`.
- **Clarify dialog** — `PendingClarify` state model, Compose `Dialog` with question display and free-text answer input. `ClarifyRequest` notifications set `pendingClarify` state, `respondToClarify()` calls `clarify.respond` RPC. Cancel sends empty string to unblock the turn.
- **Approval RPC** — `approvalRespond()` sends correctly-param'd `approval.respond` notification to server (choice: "approve"/"deny", all: bool)
- **Release CI** — `.github/workflows/release.yml` auto-builds APK and creates GitHub Release on push to master
- **Debug logging** — in-app ring buffer (1000 entries), exportable from Settings

### What works (Legacy REST+SSE stack — fallback)
- Setup screen with server URL + API key entry
- Session list via `GET /api/sessions`
- Chat screen with SSE streaming
- Message bubbles, typing dots, keyboard auto-scroll, copy-to-clipboard

### Known issues / NOT yet wired
- **Approval dialog only fires for dangerous terminal commands** — Server-side `approval.request` notification is emitted only when `detect_dangerous_command()` matches (e.g. `rm -rf`, `curl | bash`). Normal tools like `web_search`, `ls ~/`, `read_file` auto-approve silently. The Android dialog is correctly built but never triggered for everyday commands. This matches Telegram behavior (only dangerous patterns ask for approval).
- ~~**ClarifyRequest still auto-denied**~~ **Clarify dialog implemented** — `PendingClarify` state model with free-text answer input. `ClarifyRequest` notifications set `pendingClarify` in `ChatUiState`, triggering a Compose `Dialog` with the server's question and an answer field. Cancel sends empty string to unblock the turn.
- **Empty sessions in session.list** — `session.list` may return sessions with zero messages or no content. Need server-side filtering or client-side display filtering.
- **Legacy REST/SSE stack cleanup deferred** — `ApiClient.kt`, `DTOs.kt`, `SseParser.kt`, old `SetupViewModel`, `SetupScreen`, old `ChatViewModel` still present. Deferred until new stack is proven end-to-end.
- **No background WebSocket keepalive** — `HermesForegroundService` exists in `core/ui` but is not wired. Phone lock may disconnect WebSocket.
- **Feature modules contain dead code** — `feature/chat/`, `feature/session/`, `feature/skills/`, etc. contain auto-generated `Component_*.kt` stubs. Not included in `settings.gradle.kts` — do not compile. Safe to delete.
- **Debug APK is large** — 63MB debug build vs 28MB release (ProGuard + R8). Expected.
- **Signed with debug key** — Release builds use `signingConfigs.getByName("debug")`. Need real keystore before production distribution.

---

## Dashboard JSON-RPC/WebSocket Architecture

### Auth chain
```
1. POST http://100.80.204.66:9119/auth/password-login {"provider":"basic","username":"jeff","password":"***"}
   → session cookies (hermes_session_at 12h, hermes_session_rt 30d) stored in CookiePersistor

2. POST http://100.80.204.66:9119/api/auth/ws-ticket (cookie-authenticated)
   → {"ticket":"...", "ttl_seconds":30}  # single-use, 30s TTL

3. ws://100.80.204.66:9119/api/ws?ticket=...
   → 101 Switching Protocols
   → {"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready",...}}

401 interceptor: DashboardAuthenticator auto-relogins with stored password
```

### Networking stack
```
DashboardApiClient (REST, cookie-auth)
  ↓ provides OkHttpClient + auth helpers
WsConnectionManager (WebSocket lifecycle, reconnect, Channel<Flow>)
  ↓ provides raw text frames
JsonRpcClient (JSON-RPC 2.0, request/response correlation, notification router)
  ↓ provides notifications: Flow<RpcNotification>
UI Layer (DashboardChatViewModel, SessionsViewModel)
```

### JSON-RPC methods used
| Method | Purpose |
|---|---|
| `session.list` | List all available sessions |
| `session.resume` | Load session history (returns resolved live sid + session_key) |
| `prompt.submit` | Send message and start streaming response |
| `session.interrupt` | Stop current streaming turn |
| `approval.respond` | Respond to tool approval requests (params: `session_id`, `choice: "approve"|"deny"`, `all: bool`) |
| `clarify.respond` | Respond to clarification requests (params: `session_id`, `request_id`, `answer: string`) |

### Stream architecture
- Appends only — deltas (message.delta, thinking.delta, reasoning.delta) carry incremental text fragments
- No sequence numbers — order guaranteed by WebSocket stream order
- Concatenation at the client — content = previous_content + delta.text
- `message.completed` / `assistant.completed` carries the final consolidated message state
- Notifications carry `session_id` in params — route by session ID

---

## Completed Phases

### Phase 1 — Dashboard Initialization
- `KeychainStore` — dashboard credential methods (`saveDashboardCredentials`, `getDashboardUrl`, `getDashboardPassword`)
- `HermexApplication` — `DashboardApiClient.init(this)` in separate try/catch alongside legacy `ApiClient.init()`
- Initial separation of concerns: dashboard failure does not block legacy API access

### Phase 2 — Dashboard Authentication Setup Flow
- `DashboardSetupScreen` — Compose screen for URL + password entry
- `DashboardSetupViewModel` — `status()` → `login()` flow, credential persistence
- `MainActivity` — route for `dashboard-setup`, start destination priority: dashboard first, fallback to legacy setup
- Default URL was originally `https://100.80.204.66:8443` (changed to `http://100.80.204.66:9119` in v0.1.33)

### Phase 3 — Dashboard WebSocket + JSON-RPC Chat Pipeline
- `WsConnectionManager` — full lifecycle: connect, fresh ticket, reconnect loop with exponential backoff, `pingInterval(30s)`
- `JsonRpcClient` — `inline reified request<T>()`, `CompletableDeferred` pending map, `notifications: Flow<RpcNotification>`, timeout handler
- `RpcNotification` — 17-event sealed class for all server-pushed event types
- `DashboardChatViewModel` — WS connect → RPC start → session resume → notification collection → UI state management
- Reuses `ChatScreen`, `ChatUiState`, `UiMessage` — UI layer unchanged
- Full streaming pipeline: gateway.ready → run.started → message.delta → thinking.delta → tool.started/progress/completed → message.completed → run.completed

### Phase 4A — Diagnostic DebugLog Entries
- Added DebugLog instrumentation throughout all networking layers
- Session ID tracking, WS state transitions, RPC request/response timing
- All backend path calls logged with session_id context

### Phase 4B — Dashboard Migration Hardening
- Fixed URL derivation: REST URL (port 8443 HTTPS) vs WS URL (port 9119 WS) separated
- `setDashboardUrl()` derives WS URL from REST URL automatically
- Auth chain hardened: explicit `provider='basic'` in login requests, `encodeDefaults = true` in kotlinx serializer

### Phase 4D — Debug Instrumentation (v0.1.16 → v0.1.17)
- Added debug instrumentation for keyboard, scroll, and WS state transitions
- Enhanced DebugLog output for ScrollState, NestedScrollConnection behavior
- WS state monitoring via `state.collect` in ChatViewModel

### Phase 4E — Session ID Trace Logging (v0.1.17 → v0.1.18)
- Added session ID tracking through the entire `session.resume` → `prompt.submit` flow
- Logged `session_id` returned by `session.resume` vs the `session_id` sent in `session.resume`
- Tagged all session ID logs with "STATE" tag for easy filtering; tracked match/mismatch

### Phase 4G — Complete Session ID Normalization (v0.1.18 → v0.1.19)
- **Root cause of 4001 bug:** DB session key != runtime/live session ID
- **Server side:** `_sess_nowait` returns (session, resolved_sid, err) 3-tuple, resolves DB keys to live sids via `_find_live_session_by_key`; `_sess()` passes resolved sid to `_start_agent_build`, `prompt.submit` reassigns `sid = sid_resolved`
- **Android:** `DashboardChatViewModel.loadMessages()` normalizes `sessionId` to the resolved live sid after `session.resume` returns
- 5 regression tests on server side for DB key → live sid resolution
- Added auto-release CI workflow (`.github/workflows/release.yml`)
- Version bumped to v0.1.19

### Phase 4H — Stabilize Documentation (v0.1.19)
- Added PROJECT_HANDOFF_CURRENT.md and CHANGELOG.md
- Comprehensive documentation of project state, architecture, and history

### Phase 4I — Streaming Notification Parsing (v0.1.19)
- Added `RpcNotification.MessageStarted` — parses `message.start`/`message.started` events, updates placeholder with server-provided message ID
- Added `RpcNotification.ReasoningAvailable` — parses `reasoning.available`, triggers Thinking toggle appearance
- Extended `message.completed`/`assistant.completed` to also match `message.complete`; uses `payload` object first (Dashboard WS convention), falls back to `message` (REST SSE convention)
- Unknown events logged with full `rawParams` (200-char preview) for debugging

### Phase 4K — Chat Viewport Stabilization (v0.1.20 → v0.1.21)
- **Extracted `autoScrollToBottom()` helper** — unified two-step scroll: `scrollToItem(targetIndex)` then `scrollBy(remaining)` computed from `visibleItemsInfo`
- **Fixed streaming auto-scroll** — when a streaming bubble grows taller than the viewport, compensation scrolls remaining distance
- **Fixed keyboard open scroll** — after IME opens, compensation ensures last message's bottom is above keyboard
- **All 4 scroll sites unified** — SessionOpen, AutoScroll (per-delta), StreamEnd, and Keyboard all use the same helper
- **Bugfix: `isWaitingForFirstEvent` never cleared by `thinking.delta`/`reasoning.delta`** — TypingDots displayed indefinitely
- **Bugfix: `MessageStarted` didn't clear `isWaitingForFirstEvent` when `messageId` null** — fixed by always clearing the flag
- **Hardening: all delta handlers filter by `isStreaming`** — prevents content applied to wrong message

### Phase 4L — Chat Viewport & Keyboard Anchoring, Session Key Field (v0.1.22 → v0.1.23)

- **Replaced `scrollGeneration`-keyed LaunchedEffect with polling loop** — continuous 50ms polling while `isStreaming` ensures auto-scroll keeps up
- **Frame-based keyboard settle** — replaced fixed 500ms delay with `withFrameNanos` × 3
- **Session ID lifecycle hardening** — split `sessionId` (DB key) from `liveSid` (transient RPC sid); `session_key` field added to `SessionResumeResult`

### Phase 4L.1 — Session Lifecycle Fix (v0.1.24 → v0.1.25)
- **Root cause:** After `session.resume` returns a live SID, `prompt.submit` was passing the live SID directly in the params object. The server's `_sess()` calls `_sess_nowait` with `params.get("session_id")`, but the server handler for `prompt.submit` used a separate `session_id` variable — creating a mismatch.
- **Fix:** Split `sessionId` (DB key, persistent) from `liveSid` (ephemeral, for RPC routing). `sessionId` remains the DB key for storage/reference; `liveSid` is used for notification routing and logging. `prompt.submit` continues to send the DB key (not the live SID) and the server resolves it internally. Notification filtering compares against both `sessionId` (DB key) and `liveSid` (live SID).
- **Key rule enforced in code:** `// liveSid is DEBUG ONLY — never write into sessionId`
- Debug logging now uses `dbKey=$sessionId liveSid=$liveSid` format throughout.
- Version: v0.1.25

### Phase 4L.2 — Session DB Key Resolution (v0.1.24)
- **Problem:** Reconnect generates a NEW live SID, but the client still has the OLD live SID. When notifications arrive with the new live SID, the session ID filter rejects them.
- **Fix:** Two-phase notification matching: compare `nSid` against BOTH `sessionId` (DB key) AND `liveSid` (live SID). If either matches, the notification is accepted.
- The server's `_sess_nowait()` already resolves DB keys to live SIDs via `_find_live_session_by_key()`. The `session.resume` handler calls `_sess_nowait` which returns a resolved live SID.

### Phase 4L.3 — Log Cleanup (v0.1.26)
- **101 WebSocket close fix:** OkHttp's `normalClose()` override was incorrectly logging a false `onFailure` when the server sent a WS close frame (code 1000). Fixed by returning early from `onFailure` when a close frame was already processed.
- **Keyboard spam reduction:** Wrapped NestedScrollConnection debug logging in `env_var_enabled("LOG_SCROLL")` guard. Keyboard events now only log on significant viewport height changes (>50px delta).
- Code comment updated: Logged close codes 1000–1005 are all normal. 1006 is the only one that indicates a true connection loss.

### Phase 4L.4 — Reconnect Session Resume (v0.1.27 → v0.1.28)
- **Problem:** After WebSocket reconnection (e.g. phone lock), the server's live SID session changes. The client reconnects but the server doesn't know about the old session ID → 4001 error on `session.interrupt`.
- **Fix:** In `DashboardChatViewModel`, added WS state listener. On every `state == Connected` transition, call `session.resume` to re-register the session with the server's new runtime.
- **Bugfix: keyboard bottom-lock regression** — `imePadding()` + `navigationBarsPadding()` in Scaffold caused the composer to sit below the keyboard. Fixed by replacing with `WindowInsets.ime`-only calculation.
- Verified working on-device: v0.1.28 log shows clean reconnects with `resumeCount` incrementing.

### Phase 5A — Approval RPC Method Fix (v0.1.29)
- **Problem:** `JsonRpcClient.approvalRespond()` used the wrong parameter names (`session_key` instead of `session_id`, `approved` boolean instead of `choice` string, `reason` instead of `all`), so it would never reach the server's `approval.respond` handler.
- **Fix:** Updated to `session_id`, `choice: "approve"|"deny"`, `all: Boolean`. Also clarified `clarifyRespond` docs.
- Uses `notify()` (fire-and-forget — no response expected) since the server processes it either way.
- No behavior change — nobody was calling these with the corrected signature yet.

### Phase 5B+5C — Approval State Model + Dialog UI (v0.1.30)
- **State:** Added `PendingApproval` data class (`toolName`, `toolArgs`, `requestId`). Added `pendingApproval: PendingApproval?` to `ChatUiState`.
- **ViewModel:** Added `approveCurrentTool(approveAll)` and `denyCurrentTool(denyAll)` methods that call `rpcClient.approvalRespond()`. Added `open` methods to `ChatViewModelContract` (no-op on legacy SSE VM).
- **Notification handler:** `ApprovalRequest` notifications now set `uiState.pendingApproval` instead of logging a warning.
- **Dialog:** Compose `Dialog` in `ChatScreen` showing tool name with 🔒 icon, args in monospace box, and Approve/Deny buttons.
- **Build issues fixed:** `override` modifier, import for `Dialog`, color fallback (`surfaceVariant` instead of `surfaceContainerHigh`).

### Phase 5D — Remove JsonRpcClient Auto-Deny (v0.1.31)
- **Root cause:** `JsonRpcClient.parseNotification()` had a `when` block that intercepted `ApprovalRequest` and auto-denied it via `notify("approval.respond", ...)` BEFORE the notification was emitted to the `notifications` channel. The ViewModel never saw the request.
- **Fix:** Removed the auto-deny block entirely. Approval requests now flow through the full pipeline: `server → WS → JsonRpcClient → notifications → ViewModel → Dialog`.
- Also removed stale auto-deny params that didn't match the server contract anyway.

### Phase 5D Fix — Stuck Scroll Loop (v0.1.32)
- **Problem:** `loadMessages()` did not reset `state.isStreaming` to false. When a ViewModel survived navigation, the StreamLoop continued scrolling at 20fps forever in the background.
- **Fix:** Added explicit `isStreaming = false` in `loadMessages()` `uiState.copy()` call.

### Phase 5D.2 — Port 8443 Cleanup (v0.1.33 → v0.1.34)
- **Root cause:** Dashboard serves REST + WebSocket on port 9119 plain HTTP. Code assumed REST on 8443 HTTPS.
- Fixed default URL in setup screen: `https://100.80.204.66:8443` → `http://100.80.204.66:9119`
- Removed hardcoded `:8443` → `:9119` port derivation in `setDashboardUrl()` — WS now uses same port, only scheme changes
- Removed dead SSL trust-all code (`hostnameVerifier { _, _ -> true }` + trust-all `X509TrustManager`) — dead since dashboard is plain HTTP
- Replaced app-wide `android:usesCleartextTraffic="true"` with scoped `network_security_config.xml` allowing cleartext to `100.80.204.66` only
- Updated all code comments, KDoc, and handoff docs to reflect 9119

### Phase 5D.3 — Tool Event Name Fix (v0.1.35)
- **Root cause:** `RpcNotification` expected `tool.started`/`tool.progress`/`tool.completed` but the dashboard server emits `tool.generating`/`tool.start`/`tool.complete` with different payload shapes. Every tool event fell through to `Unknown`.
- Replaced `ToolStarted`/`ToolProgress`/`ToolCompleted` with `ToolGenerating` (payload: `{name}`), `ToolStart` (`{tool_id, name, context}`), `ToolComplete` (`{tool_id, name, args, result, summary}`)
- Updated `JsonRpcClient` parsing to extract fields from nested `payload` object (server convention for dashboard WS)

### Phase 5D.4 — Tool Card Display Fix (v0.1.36)
- **Root cause:** `MessageCompleted` handler replaced the entire `toolCalls` list with stripped-down data from the message object, losing all preview/args/context accumulated from live tool events.
- `MessageCompleted` now **merges** server IDs into live-accumulated tool calls instead of replacing them wholesale
- `loadMessages()` session resume now populates `UiToolCall.args` from `function.arguments` so tool cards show context on replay
- `ToolCallCard` renders args text when no preview is available
- Added `[RPC] ToolEvent` debug logging for all three tool events

### Phase 5E — Clarify Dialog UI (v0.1.37)
- **Problem:** `ClarifyRequest` notifications were auto-denied in `JsonRpcClient` (previously removed) and still auto-denied in `DashboardChatViewModel.handleNotification()`. No UI for the user to provide input.
- **State model:** Added `PendingClarify` data class (`requestId`, `question`) to `ChatViewModel.kt`. Added `pendingClarify: PendingClarify?` to `ChatUiState` (same pattern as `PendingApproval`).
- **Contract:** Added `open fun respondToClarify(answer: String)` to `ChatViewModelContract` with no-op default (inherited by legacy SSE `ChatViewModel`).
- **ViewModel:** `DashboardChatViewModel.handleNotification()` now sets `uiState.pendingClarify` on `ClarifyRequest` instead of auto-denying. `respondToClarify()` calls `rpcClient.clarifyRespond(requestId, answer)` and clears the pending state.
- **Dialog UI:** Compose `Dialog` in `ChatScreen` showing:
  - Title: "Clarification Needed"
  - Server's question text
  - Free-text `OutlinedTextField` for the user's answer
  - **Cancel** sends empty string (unblocks the turn)
  - **Send** sends the user's answer (enabled only when non-blank)
- **JsonRpcClient:** Auto-deny block was already removed in prior uncommitted change — `ClarifyRequest` notifications pass through to the ViewModel's `notifications` channel.
- Builds cleanly with no errors.

### Phase 6B — Reliable Interrupt Support (v0.1.38)
- **Problem:** `stopStreaming()` set `uiState.isStreaming = false` but never cleared `isStreaming` on the individual `UiMessage` object. After pressing Stop, the blinking cursor (▌), thinking ticker, and typing dots persisted visually even though streaming had stopped. Pending approval/clarify dialogs were also left on-screen.
- **Fix:** `DashboardChatViewModel.stopStreaming()` now:
  - Finds the last assistant message with `isStreaming == true` and sets it to `false` (same pattern as the error handler in `sendMessage`)
  - Clears `pendingApproval` and `pendingClarify` so any visible dialog is dismissed
- **Legacy stack:** Applied the same `isStreaming` cleanup to `ChatViewModel.stopStreaming()` for consistency.
- Version bumped to v0.1.38.---

## Session ID Bug — Explanation and Fix

### Problem
When a user taps a session in the session list, a DB session key (e.g., `20260717_205748_97c893e8`) is passed to the chat screen. `session.resume` is called with this key, and the server returns a live session ID (e.g., `f4c9982c`). However, subsequent calls like `prompt.submit` were still using the original DB key, causing a 4001 "session not found" error because the DB key is not a valid runtime session ID.

### Root cause
The server stores sessions indexed by DB keys. When a prompt submission arrives with a DB key instead of a live runtime session ID, the server cannot find the session in its runtime agent registry. The `session.resume` method returns the resolved live sid, but until Phase 4G, the Android client never used it.

### Fix
1. **Server side** (server.py changes in Phase 4G):
   - `_sess_nowait` returns a 3-tuple: (session, resolved_sid, err)
   - `_find_live_session_by_key` resolves DB keys to live runtime sids
   - `_sess()` passes resolved sid to `_start_agent_build`
   - `prompt.submit` handler reassigns `sid = sid_resolved` before all downstream operations

2. **Android side** (`DashboardChatViewModel.kt`):
   - `sessionId` (DB key, immutable) is kept separate from `liveSid` (transient, for notification routing)
   - `prompt.submit` sends the DB key; server resolves it internally
   - Notification filter matches against BOTH `sessionId` and `liveSid`
   - On reconnect, `session.resume` is called to re-register with the new live SID

---

## Build Instructions

### Prerequisites
- Android Studio (or `sdkmanager`) with SDK 34
- JDK 17
- `local.properties` pointing to Android SDK

### Build commands
```bash
cd ~/HermexAndroid

# Full release build
./gradlew assembleRelease --no-configuration-cache

# Compile individual modules
./gradlew :core:network:compileReleaseKotlin
./gradlew :core:data:compileReleaseKotlin

# Clean build
./gradlew clean assembleRelease --no-configuration-cache
```

**Note:** The `--no-configuration-cache` flag is critical — cached configs have produced stale APK bytecode.

### Output
APK at `app/build/outputs/apk/release/app-release.apk`

---

## Release Process

### Manual
```bash
# 1. Bump versionCode and versionName in app/build.gradle.kts
# 2. Build
./gradlew assembleRelease --no-configuration-cache
# 3. Commit + push
git add -A && git commit -m "v0.1.X — description"
git push origin master
# 4. Draft release
gh release create v0.1.X app/build/outputs/apk/release/app-release.apk \
  --title "v0.1.X — description" --notes "..."
```

### Automatic (CI)
The `.github/workflows/release.yml` workflow triggers on push to `main`/`master`:
1. Reads `versionName` from `app/build.gradle.kts`
2. Checks if tag already exists
3. Builds signed release APK
4. Creates and pushes tag
5. Creates GitHub Release with APK attached

**Caveat:** The repo is private. GitHub Releases are created but APK attachments are invisible to Obtainium while the repo remains private.

---

## Project Structure

### Active modules (in `settings.gradle.kts`)
```
Hermex/
├── app/                          # Main application + Compose UI
├── core/network/                 # Networking layer (both stacks)
└── core/data/                    # Data layer
```

### Key files

| File | Lines | Purpose |
|---|---|---|
| `app/.../MainActivity.kt` | 121 | NavGraph, route definitions, dashboard vs legacy routing |
| `app/.../HermexApplication.kt` | 54 | Startup: ApiClient.init + DashboardApiClient.init |
| `app/.../DashboardChatViewModel.kt` | 587 | Dashboard WS chat: connect, resume, send, notification handler, approve/deny/clarify, stop generation |
| `app/.../ChatScreen.kt` | 970 | Compose UI: LazyColumn, bubbles, typing dots, thinking, auto-scroll, approval/clarify dialogs |
| `app/.../ChatViewModelContract.kt` | 24 | Abstract contract for both legacy and dashboard VMs |
| `app/.../ChatViewModel.kt` | 370 | Legacy SSE chat ViewModel (has no-op approve/deny) |
| `app/.../DashboardSetupScreen.kt` | 150 | URL + password entry screen |
| `app/.../DashboardSetupViewModel.kt` | 119 | status() → login() flow for dashboard setup |
| `app/.../SessionsScreen.kt` | 165 | Session list UI |
| `app/.../SessionsViewModel.kt` | 153 | Session list loading (dashboard via RPC, legacy fallback) |
| `app/.../SetupScreen.kt` | — | Legacy setup screen (will be removed) |
| `app/.../SetupViewModel.kt` | — | Legacy setup ViewModel (will be removed) |
| `core/network/DashboardApiClient.kt` | 253 | REST client: login, ws-ticket, status, 401 auto-relogin |
| `core/network/WsConnectionManager.kt` | 215 | WebSocket lifecycle, reconnect, Frame Channel |
| `core/network/JsonRpcClient.kt` | 470 | JSON-RPC 2.0: request/response, notifications, approvalRespond, clarifyRespond |
| `core/network/RpcNotification.kt` | 156 | Sealed class for 18 event types, including ApprovalRequest, ClarifyRequest |
| `core/network/DebugLog.kt` | — | Ring buffer logger (1000 entries) |
| `core/network/DebugLoggingInterceptor.kt` | — | HTTP/SSE debug logging |
| `core/data/KeychainStore.kt` | — | EncryptedSharedPreferences for secrets |

### Routes
```
dashboard-setup (if not configured) → home (dashboard) → chat/{sessionId}/{title}
                                                                         ↓
                                                                      settings
```

---

## Architectural Decisions (Preserve)

1. **`mutableStateOf`, not `StateFlow`** — `StateFlow` conflates intermediate values. During rapid streaming events (10-50ms deltas), snapshot state writes directly to the next composition frame.

2. **`scrollToItem`, not `animateScrollToItem`** — Spring animation (~200-300ms) is constantly cancelled and restarted by rapid deltas, causing viewport drift. Instant scroll completes in one frame.

3. **`scrollGeneration` counter + polling loop** — Monotonic counter bumped on every list mutation. Combined with 50ms polling while `isStreaming`, ensures auto-scroll keeps up with rapid content growth. Polling loop replaced the earlier `scrollGeneration`-keyed LaunchedEffect approach.

4. **Fresh ticket every reconnect** — WebSocket ticket is single-use with 30s TTL. Never cached or reused. Every reconnect cycle fetches a fresh ticket.

5. **`sessionId` (DB key) immutable; `liveSid` (transient) for routing** — The DB key never changes; the live SID may change on reconnect. Notification filter matches against BOTH. `liveSid` is DEBUG ONLY — never written into `sessionId`.

6. **Approval/clarify notifications flow through ViewModel** — `JsonRpcClient` parses notifications and emits them via `notifications` channel. ViewModel's `handleNotification()` sets state. No auto-deny in client (removed in v0.1.31 for approval, Phase 5E for clarify). User gets a dialog in both cases.

7. **Coexist old and new networking stacks** — Do NOT delete `ApiClient.kt` or related files until the new JSON-RPC/WS stack is proven end-to-end on device.

8. **Delta = append-only concatenation** — No sequence number field exists. Order guaranteed by WebSocket stream order. Client concatenates `content = content + delta.text`.

9. **MessageCompleted merges, not replaces, tool calls** — When `MessageCompleted` arrives, it merges server-provided IDs into the live-accumulated tool calls (preserving preview/args from `ToolStart`/`ToolComplete`) rather than replacing the list. This ensures tool cards keep their context through finalization and session reload.

10. **Tool event names match server reality, not docs** — The dashboard server emits `tool.generating`/`tool.start`/`tool.complete` with nested `payload` objects. Not the `tool.started`/`tool.progress`/`tool.completed` with flat params that the legacy SSE stack uses. Both coexist in separate code paths.

---

## Development Environment

### Server
- Hermes Dashboard at `http://100.80.204.66:9119` (REST + WebSocket, plain HTTP, Tailscale IP)
- Legacy API at `http://100.80.204.66:8650` (plain HTTP, Hermes API Server)

### Device
- Pixel 8 (Android 17 / SDK 37) with Tailscale
- Obtainium for updates (point to GitHub Releases)

### Required secrets
- Dashboard password (stored in `KeychainStore` as `dashboard_password`)
- Hermes API Server bearer key (stored as `api_key` — legacy)
- Obtainium needs GitHub PAT with `repo` scope for private repo access

### Build machine (BigRed)
- Linux 6.8.0 (tailscale-connected)
- Android SDK at `/mnt/storage/Android/Sdk`
- JDK 17
- GitHub CLI authenticated

---

## Next Steps

1. **Background WebSocket keepalive** — Wire `HermesForegroundService` to keep WebSocket alive when phone locks
2. **Legacy stack cleanup** — Remove `ApiClient.kt`, `DTOs.kt`, `SseParser.kt`, old `SetupViewModel`, `SetupScreen`, `SessionsViewModel`, `ChatViewModel`
3. **Feature module cleanup** — Delete stub files in `feature/` directories not included in build
4. **StreamLoop optimization** — Only poll on actual state change instead of 50ms timer; currently wasteful during thinking (no content change)
5. **Production signing** — Wire proper keystore for release builds
6. **Make repo public** — So Obtainium can see GitHub Releases and auto-update
