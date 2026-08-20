# Hermex Android — Project Handoff (Current State)

**Last updated:** 2026-08-19 (v0.1.115 — improved command approval dialog with explicit labels and JSON args parsing)
**Current version:** v0.1.115 (versionCode 115)
**HEAD commit:** `03b4189` (v0.1.114 — improve command approval dialog)
**Branch:** `master`  
**Repository:** `git@github.com:gravol/hermex-android.git`  
**Working directory:** `/home/jeff/HermexAndroid` (canonical)

---

### PENDING / NEXT (unstarted)
- **Notification bugs from field device QA** — approval notification click kills in-flight response (#1), cron check-ins missing (#2), turn-finished pings missing (#3). Status: device-QA notes only, not yet reproduced from code. Investigate at next desk session.

### Verified Project Root

| Field | Value |
|---|---|
| Canonical path | `/home/jeff/HermexAndroid` |
| Remote URL | `git@github.com:gravol/hermex-android.git` |
| Branch | `master` |
| Latest commit | `03b4189` (v0.1.114 — improve command approval dialog) |
| Build command | `./gradlew assembleRelease --no-configuration-cache` |
| APK output | `app/build/outputs/apk/release/app-release.apk` |
| Version | v0.1.115 (versionCode 115) |
| Completed phase | **v0.1.115 — improved command approval dialog** (explicit "Command"/"Arguments" labels, JSON args parsing for any tool type, fallback from "unknown" to "command") |
| Next phase | **2026-08-14 plan (from Jeff):** ① move ALL cron management into the app (create/edit/pause/delete from CronScreen — currently list+action only) — **DONE v0.1.80** ② custom colors for everything (refine text color/text size controls) — **DONE v0.1.49–58/79/95** ③ message layout final pass: thinking = own box, tools = own box (tools ONLY), streamed response stays as-is (scrollable live), both boxes sit ABOVE the response — **DONE v0.1.66–79** ④ re-verify Obtainium update flow after CI races — **DONE v0.1.76** ⑤ **lock-screen interaction** — notification "Reply" action with RemoteInput (inline reply from lock screen → prompt.submit → reply arrives as new notification; full lock-screen chat loop without unlocking) — **DONE v0.1.98**. Note: literal lock-screen *widgets* aren't stock Android (launcher-specific); notification actions are the standard path. |

> **Stale copy: `/mnt/storage/projects/HermexPort`** — Different git history (7 commits, no remote, version 0.2.0). Abandoned early port that was never pushed. **Do not edit.** The canonical repo is `/home/jeff/HermexAndroid`.

---

## Project Overview

### What the application does
Hermex Android is a native Kotlin/Compose chat client for the **Hermes Agent** AI assistant. It connects to a self-hosted Hermes Dashboard (running on a Linux server at Tailscale IP `100.80.204.66`) to provide a mobile-first conversational interface with live token streaming, tool-call visualization, thinking/reasoning blocks, session management, and tool approval.

### Architecture summary
- **Kotlin + Jetpack Compose** (Material 3) — single-module app with core library modules
- **MVVM with Compose snapshot state** — `mutableStateOf` used instead of `StateFlow` to avoid conflation during rapid streaming events
- **Plain OkHttp** — no Retrofit, no Hilt/Dagger. Manual DI via `AppModule` singleton
- **Single networking stack (since v0.1.42):** **JSON-RPC/WebSocket (port 9119)** — `DashboardApiClient` + `WsConnectionManager` + `JsonRpcClient`. Legacy REST+SSE (`ApiClient`, `SseParser`, port 8650) fully deleted in Phase 7C (v0.1.42).

### Key technologies
| Layer | Library | Version |
|---|---|---|
| Language | Kotlin | 2.1.20 |
| UI | Jetpack Compose (Material 3) | BOM 2025.05.00 (foundation 1.8.1) |
| Build | AGP + Gradle | 8.6.1 (KSP 2.1.20-1.0.32, compileSdk 35) |
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
- **Tool call visualization** — started/progress/completed UI with `ToolCallCard` components. Now correctly parses real server event names (`tool.generating`/`tool.start`/`tool.complete`). Tool cards persist through message completion and session reload. Enhanced with tool-specific emoji icons, elapsed time display, expand/collapse with full Arguments and Result sections, and `result`/`summary`/`startedAt` captured from server notifications.
- **Keyboard anchoring** — frame-based keyboard settle (replaced fixed delay with `withFrameNanos` × 3)
- **Reconnect resume** — on WebSocket reconnect, calls `session.resume` to re-register the live session (fixes 4001 error after reconnect)
- **Stop generation** — `stopStreaming()` sends `session.interrupt` RPC, clears per-message `isStreaming` flag on the last assistant message so blinking cursor / thinking ticker / typing dots disappear immediately, and dismisses any visible approval or clarify dialog. Same fix applied to legacy SSE stack.
- **Tool approval dialog** — `PendingApproval` state model, Compose `Dialog` with Approve/Deny buttons. Notification handler correctly sets state from `ApprovalRequest` events, no more auto-deny in `JsonRpcClient`.
- **Clarify dialog** — `PendingClarify` state model, Compose `Dialog` with question display and free-text answer input. `ClarifyRequest` notifications set `pendingClarify` state, `respondToClarify()` calls `clarify.respond` RPC. Cancel sends empty string to unblock the turn.
- **Approval RPC** — `approvalRespond()` sends correctly-param'd `approval.respond` notification to server (choice: "approve"/"deny", all: bool)
- **Release CI** — `.github/workflows/release.yml` auto-builds APK and creates GitHub Release on push to master
- **Debug logging** — in-app ring buffer (1000 entries), exportable from Settings
- **Retry/regenerate** — `retry()` finds the last user prompt, removes the last assistant message, and re-submits via `promptSubmit`. Retry button (Refresh icon) shown in the bottom bar when an assistant response exists. Same functionality in legacy SSE stack.
- **Markdown rendering** — Assistant and user messages are rendered with the `multiplatform-markdown-renderer` library (v0.34.0, `-m3` module). Supports headings, bold, italic, code, fenced code blocks, tables, quotes, lists, and task lists. **Syntax highlighting** for code blocks (Kotlin, Java, Python, Bash, JSON, XML, Markdown) via the `-code` module and Highlights library. Streaming cursor preserved after markdown block. Uses `rememberMarkdownState` for efficient re-composition on delta updates.
- **Background keepalive** — `WsKeepaliveService` foreground service keeps the process alive while the chat WebSocket is active. Started on WS connect, stopped on ViewModel clear. Uses `dataSync` foreground service type with a low-importance persistent notification.
- **Theme extra surfaces (v0.1.95)** — code blocks, thinking box, tool cards and the context gauge each get their own color override (Settings → Appearance) via `LocalUiSurfaces`, independent of the assistant bubble color. Null = derive from the scheme (previous behavior).
- **Tool-call visibility toggle (v0.1.96)** — persisted "Show tool calls" switch (Settings → Appearance + wrench icon in the chat top bar). Off hides the finished tools box, live-panel tool rows and tool detail dialogs; thinking and the response stay. During streaming, tools render in their own labeled TOOLS section (separate from the THINKING block) inside the live panel.
- **Thinking visibility toggle (v0.1.97)** — persisted "Show thinking" switch (Settings → Appearance + brain icon in the chat top bar). Off hides the finished thinking box, the live THINKING section and the in-stream thinking ticker; tools and the response stay. Model selector + reasoning flow re-verified (v0.1.97): `model.options` picker → "Apply to this chat" via `config.set` with the live SID, "Save for new chats" via `model_pick`/`reasoning_pick` → `session.create`.
- **Lock-screen notification Reply (v0.1.98)** — turn-finished notifications carry an inline "Reply" action (RemoteInput). Typing a reply from the lock screen or shade → `NotificationReplyReceiver` → `NotificationReplyService` (foreground, dataSync) → fresh WS + `session.resume` + `prompt.submit` → the assistant's reply arrives as a new turn-finished notification (which carries the Reply action again — full notification chat loop without unlocking). Pinned 2026-08-14 plan item ⑤ done.
- **Slash commands fixed (v0.1.99)** — verified live against the gateway (WS RPC probe): completion, exec, worker, and DB-key paths all work server-side. Root-caused the failure: **skill commands** (`/hermes-agent`, `/hermexandroid`, … — a large share of the completion menu) are rejected by `slash.exec` with 4018 *"use command.dispatch"*, which the app never implemented → "⚠️ Command failed". Now: 4018 → `command.dispatch` (live SID only, like `config.set`); 4001 → self-heal resume + retry (same as `prompt.submit`); `{"type":"skill"|"send","message":...}` → submitted as a real prompt. Slash completions also work mid-turn now.
- **Cron notification reliability (v0.1.100)** — the 7am weather ping was missed this morning. Server side verified clean (run completed 2026-08-16 14:05:33 UTC); the phone's alarm path failed. Root causes fixed: exact alarms (`setExactAndAllowWhileIdle`, manifest already had `SCHEDULE_EXACT_ALARM`+`USE_EXACT_ALARM`) so Doze can't defer the 7am alarm 10+ min; re-check alarms use a **distinct request code** so a concurrent `sync()` (from `cron.changed` or app start) can no longer clobber a pending re-check (v0.1.77 bug class reintroduced via the WS path); login cooldown (30 min) stops the ~25-30 password-logins/hour storm caused by the server broadcasting `cron.changed` every scheduler tick → `sync()` → unconditional `login()`; catch-up scans bounded to once/10 min. (`CronWatcher.kt`.)

### Legacy REST+SSE stack — REMOVED (Phase 7C, v0.1.42)
`ApiClient`, `DTOs`, `SseParser`, `SetupScreen`/`SetupViewModel`, legacy `ChatViewModel`, and `HermesForegroundService` were fully deleted in v0.1.42. The app is dashboard JSON-RPC/WebSocket only.

### Known issues / NOT yet wired
- **Approval dialog only fires for dangerous terminal commands** — Server-side `approval.request` notification is emitted only when `detect_dangerous_command()` matches (e.g. `rm -rf`, `curl | bash`). Normal tools like `web_search`, `ls ~/`, `read_file` auto-approve silently. The Android dialog is correctly built but never triggered for everyday commands. This matches Telegram behavior (only dangerous patterns ask for approval).
- ~~**ClarifyRequest still auto-denied**~~ **Clarify dialog implemented** — `PendingClarify` state model with free-text answer input. `ClarifyRequest` notifications set `pendingClarify` in `ChatUiState`, triggering a Compose `Dialog` with the server's question and an answer field. Cancel sends empty string to unblock the turn.
- **Empty sessions in session.list** — `session.list` may return sessions with zero messages or no content. Need server-side filtering or client-side display filtering.
- ~~**Legacy REST/SSE stack cleanup deferred**~~ **Legacy stack cleaned up (Phase 7C)** — `ApiClient.kt`, `DTOs.kt`, `SseParser.kt`, old `SetupViewModel`, `SetupScreen`, old `ChatViewModel`, `HermesForegroundService` stub, and orphaned `feature/` modules all removed.
- ~~**No background WebSocket keepalive**~~ **Background keepalive implemented (Phase 7B)** — `WsKeepaliveService` foreground service wired into `DashboardChatViewModel`. Starts on WS connect, stops on ViewModel clear. (The old `HermesForegroundService` template was removed in Phase 7C.)
- **Feature modules contain dead code** — `feature/chat/`, `feature/session/`, `feature/skills/`, etc. contain auto-generated `Component_*.kt` stubs. Not included in `settings.gradle.kts` — do not compile. Safe to delete.
- **Debug APK is large** — 63MB debug build vs 28MB release (ProGuard + R8). Expected.
- ~~**Signed with debug key**~~ **Production signing DONE (2026-07-17)** — `keystore.properties` + `hermex-release.keystore` (both gitignored) + 4 CI secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Release builds sign with the real keystore.

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
- Version bumped to v0.1.38.

### Phase 6C — Regenerate Responses (v0.1.38)
- **Feature:** Users can now retry the last assistant response, similar to ChatGPT's regenerate.
- **Implementation:**
  - Added `abstract fun retry()` to `ChatViewModelContract`
  - `DashboardChatViewModel.retry()` finds the last user message's text, removes the last assistant message, adds a new streaming placeholder, and calls `rpcClient.promptSubmit(sessionId, text)`
  - `ChatViewModel.retry()` reuses the same approach with the legacy SSE `ApiClient.openChatStream()`
- **UI:** Retry button (`Icons.Default.Refresh`) appears in the bottom bar next to the Send button when the last message is an assistant response and not streaming.
- Version bumped to v0.1.38.

### Phase 6E — Tool Card Improvements (v0.1.39)
- **UiToolCall enhanced:** Added `result: String?`, `summary: String?`, `startedAt: Long?` fields. The server's `ToolComplete` notification carries `result` and `summary` — these are now captured instead of being dropped.
- **Tool event handling:** `ToolStart` handler now records `startedAt = System.currentTimeMillis()` for elapsed time calculation. `ToolComplete` populates `result` and `summary` in addition to existing fields.
- **ToolCallCard redesigned:**
  - **Icons:** Context-aware emoji icons mapped from tool name (`web_search`→🔍, `bash`→💻, `read_file`→📄, etc.)
  - **Elapsed time:** Shows duration since `startedAt` (e.g. "3.2s" for short tools, "1m 42s" for long/completed)
  - **Expand/collapse:** Click the card header to toggle. Collapsed view shows icon + name + elapsed + status + one-line preview. Expanded view reveals full Arguments and Result sections in styled surfaces
  - **Result preview:** Full tool result (up to 500 chars) shown in expanded section, replacing the old flat 200-char preview
- Version bumped to v0.1.39.

### Phase 7A — Markdown Rendering (v0.1.40)
- **Dependency:** Added `com.mikepenz:multiplatform-markdown-renderer-m3:0.33.0` (Maven Central, no repo changes needed).
- **Implementation:** Replaced the plain `Text(content = message.content)` in `MessageBubble` with `Markdown(markdownState = rememberMarkdownState(content = message.content))`.
- **Features rendered:** headings (`#`), bold (`**`), italic (`*`), inline code (`` ` ``), fenced code blocks (``` ```), tables, blockquotes (`>`), ordered/unordered lists (`-`/`1.`), and task lists (`- [x]`).
- **Streaming compatibility:** The streaming cursor (" ▌") is rendered outside the markdown block. `rememberMarkdownState` re-parses the full content on each delta, which is acceptable since chat messages are typically short-to-medium length.
- Version bumped to v0.1.40.

### Phase 7B — Background WebSocket Keepalive (v0.1.41)
- **Problem:** Phone lock caused Android to kill the process or throttle network, disconnecting the WebSocket. The existing `pingInterval(30s)` in `WsConnectionManager` only helped while the process stayed alive.
- **New file:** `app/.../service/WsKeepaliveService.kt` — foreground service that keeps the process alive while the chat WebSocket is active.
  - `START_STICKY` restart behavior
  - Notification channel `hermex_keepalive_channel` (LOW importance, silent, no badge)
  - Persistent notification "Hermex AI — Connected" with tap-to-open
  - Companion `start(context)` / `stop(context)` helpers
- **Manifest:** Added `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions and `<service foregroundServiceType="dataSync">` declaration.
- **ViewModel wiring:** `DashboardChatViewModel.connectWsAndStart()` calls `WsKeepaliveService.start()` after WS connects; `onCleared()` calls `stop()`.
- The service does NOT own the WebSocket — it only keeps the process alive so `WsConnectionManager` (owned by the ViewModel) can continue uninterrupted.
- Builds and assembles cleanly. Version bumped to v0.1.41.

### Phase 7C — Syntax Highlighting & Legacy Stack Cleanup (v0.1.42)
- **Dependency upgrade:** `multiplatform-markdown-renderer-m3` from 0.33.0 → 0.34.0. Added `multiplatform-markdown-renderer-code:0.34.0` for syntax highlighting via the Highlights library.
- **Syntax highlighting wired:** `highlightedCodeBlock` and `highlightedCodeFence` components passed to `Markdown` composable via `markdownComponents()`. Languages supported: Kotlin, Java, Python, Bash, JSON, XML, Markdown.
- **Streaming compatible:** Syntax highlighting re-parses on each delta via `rememberMarkdownState(immediate=true)`, same as the existing markdown pipeline. No performance regression for typical chat message lengths.
- **Legacy stack cleanup — Phase 1-3 (zero-risk):**
  - Deleted orphaned `feature/` directory (11 subdirs, 32 tracked files — not in `settings.gradle.kts`)
  - Deleted dead `core/ui/` files (`HermesForegroundService.kt`, `DeepLinkHandler.kt`, `ChatNotificationManager.kt`)
  - Deleted legacy `SetupScreen.kt`, `SetupViewModel.kt`, removed `"setup"` nav route from `MainActivity.kt`
- **Legacy stack cleanup — Phase 4-7 (core):**
  - `HermexApplication.kt`: removed `ApiClient.init()`, credential restore, and `ApiClient` import
  - `SettingsScreen.kt`: replaced `ApiClient.baseUrl()` with `DashboardApiClient.baseUrl()`
  - Deleted legacy `ChatViewModel.kt`, removed conditional in `MainActivity.kt` chat route (always `DashboardChatViewModel`)
  - `SessionsViewModel.kt`: removed `loadLegacySessions()` and `ApiClient`/`NetworkResult` imports
  - Extracted shared UI models (`UiMessage`, `ChatUiState`, `PendingApproval`, etc.) from legacy `ChatViewModel.kt` into new `UiModels.kt`
  - Extracted `SessionSummary` from `DTOs.kt` into its own file
  - Deleted `ApiClient.kt` and `DTOs.kt`
- Builds and assembles cleanly. Version bumped to v0.1.42.

---

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

**Note:** Repo is **public** (since 2026-08-12) — Obtainium sees GitHub Releases APKs without any PAT.

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
| `app/.../HermexApplication.kt` | 54 | Startup: DashboardApiClient.init (legacy ApiClient init removed v0.1.42) |
| `app/.../DashboardChatViewModel.kt` | 587 | Dashboard WS chat: connect, resume, send, notification handler, approve/deny/clarify, stop generation |
| `app/.../ChatScreen.kt` | 1094 | Compose UI: LazyColumn, bubbles, typing dots, thinking, auto-scroll, approval/clarify dialogs, retry button, enhanced tool cards, markdown rendering |
| `app/.../ChatViewModelContract.kt` | 24 | Abstract contract for both legacy and dashboard VMs |
| ~~`ChatViewModel.kt`~~ | — | Legacy SSE ViewModel — DELETED in v0.1.42; `DashboardChatViewModel` is the only chat VM |
| `app/.../DashboardSetupScreen.kt` | 150 | URL + password entry screen |
| `app/.../DashboardSetupViewModel.kt` | 119 | status() → login() flow for dashboard setup |
| `app/.../SessionsScreen.kt` | 165 | Session list UI |
| `app/.../SessionsViewModel.kt` | 153 | Session list loading (dashboard via RPC) |
| ~~`SetupScreen.kt` / `SetupViewModel.kt`~~ | — | Legacy setup — DELETED in v0.1.42 |
| `core/network/DashboardApiClient.kt` | 253 | REST client: login, ws-ticket, status, 401 auto-relogin |
| `core/network/WsConnectionManager.kt` | 215 | WebSocket lifecycle, reconnect, Frame Channel |
| `core/network/JsonRpcClient.kt` | 470 | JSON-RPC 2.0: request/response, notifications, approvalRespond, clarifyRespond |
| `core/network/RpcNotification.kt` | 156 | Sealed class for 18 event types, including ApprovalRequest, ClarifyRequest |
| `core/network/DebugLog.kt` | — | Ring buffer logger (1000 entries) |
| `core/network/DebugLoggingInterceptor.kt` | — | HTTP/SSE debug logging |
| `core/data/KeychainStore.kt` | — | EncryptedSharedPreferences for secrets |

| `app/.../service/WsKeepaliveService.kt` | 104 | Foreground service — keeps process alive during chat WS connection |

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

3. **`scrollGeneration` counter + polling loop** — Monotonic counter bumped on every list mutation. Combined with 100ms polling while `isStreaming`, ensures auto-scroll keeps up with rapid content growth. Polling loop replaced the earlier `scrollGeneration`-keyed LaunchedEffect approach (interval was 50ms at introduction, raised to 100ms to reduce fighting with markdown layout settling).

4. **Fresh ticket every reconnect** — WebSocket ticket is single-use with 30s TTL. Never cached or reused. Every reconnect cycle fetches a fresh ticket.

5. **`sessionId` (DB key) immutable; `liveSid` (transient) for routing** — The DB key never changes; the live SID may change on reconnect. Notification filter matches against BOTH. `liveSid` is DEBUG ONLY — never written into `sessionId`.

6. **Approval/clarify notifications flow through ViewModel** — `JsonRpcClient` parses notifications and emits them via `notifications` channel. ViewModel's `handleNotification()` sets state. No auto-deny in client (removed in v0.1.31 for approval, Phase 5E for clarify). User gets a dialog in both cases.

7. ~~**Coexist old and new networking stacks**~~ **OBSOLETE (Phase 7C, v0.1.42)** — Legacy stack proven unnecessary and fully deleted (`ApiClient.kt`, `DTOs.kt`, `SseParser.kt`, `Setup*`, legacy `ChatViewModel`). Single-stack app.

8. **Delta = append-only concatenation** — No sequence number field exists. Order guaranteed by WebSocket stream order. Client concatenates `content = content + delta.text`.

9. **MessageCompleted merges, not replaces, tool calls** — When `MessageCompleted` arrives, it merges server-provided IDs into the live-accumulated tool calls (preserving preview/args from `ToolStart`/`ToolComplete`) rather than replacing the list. This ensures tool cards keep their context through finalization and session reload.

10. **Tool event names match server reality, not docs** — The dashboard server emits `tool.generating`/`tool.start`/`tool.complete` with nested `payload` objects. Not the `tool.started`/`tool.progress`/`tool.completed` with flat params that the legacy SSE stack uses. Both coexist in separate code paths.

11. **Markdown via multiplatform-markdown-renderer** — Messages are rendered using `com.mikepenz:multiplatform-markdown-renderer-m3` (v0.34.0), chosen because it's on Maven Central (no repo changes), is native Compose (not a View wrapper), and supports Material 3 theming out of the box. Uses Maven Central artifact `com.mikepenz:multiplatform-markdown-renderer-m3`. Streaming performance is acceptable since chat messages are short-to-medium length and `rememberMarkdownState` re-parses the full string on each delta. Code syntax highlighting is provided by the companion `multiplatform-markdown-renderer-code` module (Highlights library), wired via `highlightedCodeBlock`/`highlightedCodeFence` custom components.

12. **Foreground service for process keepalive, not for WS ownership** — `WsKeepaliveService` is a lightweight `START_STICKY` foreground service that keeps the Android process alive but does NOT own the WebSocket connection. The `WsConnectionManager` remains owned by `DashboardChatViewModel` (tied to `viewModelScope`). The service only prevents the OS from killing the process or throttling network when the phone locks. When the ViewModel is cleared (user leaves chat), the service stops and the WS disconnects. This separation keeps the WebSocket lifecycle simple and avoids dual-connection issues.

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
- Obtainium fetches from the public GitHub Releases (no PAT needed since repo went public 2026-08-12)

### Build machine (BigRed)
- Linux 6.8.0 (tailscale-connected)
- Android SDK at `/mnt/storage/Android/Sdk`
- JDK 17
- GitHub CLI authenticated

---

## Next Steps

1. **Optional cleanup** — `composeOptions.kotlinCompilerExtensionVersion = "1.5.5"` is dead under the Kotlin 2.1.20 Compose plugin; `minSdk 34` (Android 14+) excludes older devices; v0.1.41 tag has no release (superseded by v0.1.42 — backfill or ignore)
2. **Upstream the server fix** — DB-key fallback in `_sess_nowait` should become a hermes-agent PR so it survives `hermes update`.

### Reported (2026-08-17) — Notification bugs from field device QA

1. **Approval notification click kills the in-flight response** — when an approval-request notification is tapped to open the app, the agent's streaming response stops mid-stream. Suspected: the tap-through path re-attaches/resumes the session or re-enters MainActivity in a way that disrupts the live WS stream / VM streaming state (same bug class as the v0.1.85 reconnect half-open). Needs root-cause from code; reproduced by Jeff in the field.
2. **Regular notifications don't fire — cron check-ins + turn-finished** — the 9am/12pm/2pm quick-check crons (agent jobs, `deliver=local`) and turn-finished pings never pop. The 7am weather report DID arrive, so the alarm path isn't uniformly dead — check-ins specifically missing. A one-shot `no_agent` cron test (2026-08-16 21:06 PDT) also produced no phone notification; server side verified clean (`last_status: ok`, no delivery error, output saved to `~/.hermes/cron/output/`). Follow-up to the v0.1.100 fixes (exact alarm, re-check clobber, login storm): device QA shows check-ins still missing. Check notification channels (cron channel suppressed by scheduled DND?), whether agent-run vs `no_agent` delivery differs, and CronWatcher arm/sync for these jobs specifically.
3. **Turn-finished pings missing** (paired with #2) — expected a ping when a Hermes turn finishes; not arriving. v0.1.98's lock-screen Reply rides on `postTurnFinished` — if those don't surface, that path is dead too.

**Status:** device-QA notes only, not yet reproduced from code. Investigate at next desk session.

### Pinned (2026-08-15) — Theme/color versatility
Parked at Jeff's request; implement on a future pass. **Item 6 done in v0.1.95** (see below); 1–5 remain:
1. **Custom color picker** — hex input + hue/sat wheel per part (removes the ~11-swatch ceiling).
2. **Split assistant bubble from top bars/composer/drawer** — they currently share one `surfaceVariant` color.
3. **Separate text roles** — user / assistant / secondary (timestamps/labels) instead of one global text color.
4. **Named savable presets** — replace the 3 hardcoded chips with a stored list (save/rename/delete, DataStore-backed).
5. **Theme mode System/Dark/Light** — accent + overrides currently force dark (`accentColorScheme` always returns `darkColorScheme`).
6. ~~**Theme extra surfaces** — code blocks (syntax-highlight bg), thinking box, tool cards, context gauge.~~ **DONE in v0.1.95.**

### DONE in v0.1.110 (2026-08-18) — New session creation fixed
- **Bug** — tapping "New session" did nothing (or hung for 10s then failed silently). Root cause: `fetchWsTicket()` did not call `login()` before fetching the WebSocket ticket. If session cookies were expired/missing, the server returned 401, the `DashboardAuthenticator` interceptor re-logged in but the ticket fetch didn't wait for the new cookies to be set before opening the WS — the ticket fetch failed, the reconnect loop started, `waitForConnection()` timed out after 10s, and `onDone(null)` was called (UI does nothing on null). **Fix:** `fetchWsTicket()` now calls `login(dashboardUsername, dashboardPassword)` before the HTTP call when `isConfigured` is true. (`DashboardApiClient.kt`)
- **Also:** tok/s rendered fallback verified working.

### DONE in v0.1.111 (2026-08-18) — New session 4007 fix
- **Bug** — new sessions worked (created + opened), but `prompt.submit` got 4001 (session reaped) → `session.resume` got 4007 (session not found) → message failed. Root cause: `submitWithSelfHeal()` called `session.resume` and assumed success; when the server returned 4007 (fresh/deleted session), the exception propagated as `prompt.submit` failure. **Fix:** `submitWithSelfHeal()` now catches 4007 from `session.resume` and falls through to `prompt.submit` directly — the server creates the DB row on first turn for deferred sessions. (`DashboardChatViewModel.kt`)

### DONE in v0.1.113 (2026-08-18) — Session delete; approval dialog enhanced
- **Session delete** — each session row (main list, drawer, and "all sessions" expanded) now shows a trash icon. Tapping it opens a confirmation dialog showing the session title and a "Delete"/"Cancel" choice. On confirm, the app calls `session.delete` RPC, reloads the session list, and the deleted session disappears. (`JsonRpcClient.kt` — `sessionDelete()` + `DeleteSessionResult`; `SessionsViewModel.kt` — `deleteSession()` + `deleting` state; `SessionsScreen.kt` — trash icon + `AlertDialog`.)
- **Approval dialog enhanced** — the "Approve Tool?" dialog now shows: the tool name (even when server returns null, defaults to "unknown"), a "What will happen" description built from the tool name and args, and a labeled "Arguments" section with monospace formatting. Previously it only showed the tool name and raw args without labels. (`ChatScreen.kt` — dialog layout; `DashboardChatViewModel.kt` — builds `description` from tool name + args; `UiModels.kt` — `PendingApproval` now has `description` field.)

### DONE in v0.1.116 (2026-08-19) — Thinking on/off + expanded effort levels
- **Thinking on/off** — the Model picker now has a dedicated **Thinking** toggle (Material `Switch`). Turning it OFF sets effort to `off` (the model stops reasoning before answering); ON uses any real effort level. This is distinct from the v0.1.97 **showThinking** visibility switch, which only hides the finished thinking box / live THINKING section / ticker — it never stopped the model from reasoning. The two are now orthogonal: you can turn thinking off at the reasoning level *and* keep the boxes visible (or vice-versa).
- **Effort levels expanded** — from `low / medium / high` to `off / minimal / low / medium / high / xhigh`. The effort chips are only shown while Thinking is ON (a hint text appears when OFF). `effortShort()` renders the chip + picker labels for the new values (`min`, `xhigh`).
- **Apply/save path** — `applyModelToSession(model, reasoning, thinkingOn)` and `saveModelPick(model, reasoning, thinkingOn)` now take a `thinkingOn` flag. `thinkingOn=false` persists and applies effort `"off"` via `config.set reasoning off`; `true` uses the chosen level (falling back to `DEFAULT_EFFORT="low"` if blank). Persisted as `reasoning_pick` in the DataStore → `session.create(reasoning_effort=…)` for new sessions. (`ChatScreen.kt` — `ModelPickerSheet` + `effortShort`/`EFFORT_OPTIONS`/`effortIsOff`/`DEFAULT_EFFORT`; `DashboardChatViewModel.kt`; `ChatViewModelContract.kt`; `SettingsRepository` unchanged — `reasoning_pick` already a free-form string.)

### DONE in v0.1.109 (2026-08-18) — tok/s readout actually updates
- **Bug** — the LiveActivityPanel tok/s readout stayed at `≈0.0 tok/s` during streaming. Root cause: `JsonRpcClient` parsed `message.delta` from `params.payload.text`, but the server sends `text` as an empty string and the actual delta text in `rendered`. The tok/s meter measured `(len - lastLen) / dtSec / 4f` where `len` never grew → always 0. (`JsonRpcClient.kt` — `message.delta` now falls back to `payload.rendered` when `text` is empty.)

### DONE in v0.1.110 (2026-08-18) — New session creation fixed
- **Bug** — tapping "New session" did nothing (or hung for 10s then failed silently). Root cause: `fetchWsTicket()` did not call `login()` before fetching the WebSocket ticket. If session cookies were expired/missing, the server returned 401, the `DashboardAuthenticator` interceptor tried to re-login but the `fetchWsTicket` didn't wait for the re-login cookies to be set before opening the WS — the ticket fetch failed, the reconnect loop started, `waitForConnection()` timed out after 10s, and `onDone(null)` was called (UI does nothing on null). **Fix:** `fetchWsTicket()` now calls `login(dashboardUsername, dashboardPassword)` before the HTTP call when `isConfigured` is true, ensuring cookies are fresh. (`DashboardApiClient.kt`)
- **Also:** tok/s rendered fallback verified working.

### DONE in v0.1.107 (2026-08-17) — Thinking tok/s beside "Live activity"
- **Feature** — the LiveActivityPanel header now shows a separate **thinking speed** while reasoning flows: `● Live activity  thinking 8.1 tok/s    ≈12.3 tok/s` (thinking in tertiary, generation in primary, gen dimmed at 0 during the wait). Computed from `thinkingText` deltas with the same chars/4 estimate + EMA; fades out a few seconds after thinking stops.
- **Header cleanup** — the "N working · N tools" counter moved from the panel header into the TOOLS section header (`TOOLS · 3 · 1 working`) to make room for both speeds. (`ChatScreen.kt` — ticker now tracks thinking length; `LiveActivityPanel(thinkingTokPerSec=…)`.)

### DONE in v0.1.106 (2026-08-17) — tok/s readout moved into the live activity panel
- **User preference** — after the v0.1.105 top-bar fix, the readout moved to the docked **LiveActivityPanel** header instead: `● Live activity  [N working · N tools]  ≈12.3 tok/s`.
- **Panel always visible while streaming** — previously the docked panel only appeared when thinking or tools were present; now it docks above the composer for every streaming turn (it hosts the speed readout). Thinking/tools sections inside still respect the show/hide toggles. Speed text is primary while text flows, dimmed at `≈0.0 tok/s` during the first-token wait. Removed from the top bar (model-chip 120dp cap kept). (`ChatScreen.kt` — `LiveActivityPanel(tokPerSec=…)`.)

### DONE in v0.1.105 (2026-08-17) — tok/s readout placement fix (was clipped)
- **Reported** — user couldn't find the tok/s readout. It WAS rendering, but as the last element of the top-bar gauge row (model chip + 64dp bar + `85.1k/1.0M` + two action icons ≈144dp of icons), it was pushed past the title width and clipped off the right edge on a phone.
- **Fix** — the readout now renders on its OWN line directly under the context-gauge row (still only while streaming; dimmed `≈0.0 tok/s` during the first-token wait, primary once text flows). The model chip is also capped at 120dp (ellipsized) so a long model name can't shove the gauge off either. (`ChatScreen.kt`.)

### DONE in v0.1.104 (2026-08-17) — tok/s readout always visible while streaming
- **Reported** — tok/s didn't appear when opening a session (local Qwen) and sending the first message; the user suspected the 76k-context load (16GB GPU) was slow and wondered if it would pick up after the first message.
- **Explanation** — the readout only rendered when speed > 0, so during the long first-token / prompt-ingestion phase (which produces no output text) it was hidden entirely. The meter measures GENERATION flow, not prompt processing — first message ingests the full 76k context (slow, ≈0 tok/s); subsequent turns in the session reuse Ollama's prompt cache so generation starts quickly.
- **Fix** — the readout now shows **whenever a turn is streaming**, dimmed at `≈0.0 tok/s` during the wait and colored primary once text flows; the estimate is EMA-smoothed (0.6/0.4) for a steady number instead of 1s spikes. (`ChatScreen.kt`.)

### DONE in v0.1.103 (2026-08-17) — Notification tap fix (turns no longer die; deep links per tap; per-session ids)
- **Bug (reported)** — notifications "don't work", and tapping the approval notification opened Hermex but instantly killed the turn (approval banner gone, agent dead).
- **Root cause 1 — activity recreate on every tap:** MainActivity had no `launchMode` (standard). Notification PendingIntents use `FLAG_ACTIVITY_NEW_TASK | CLEAR_TOP`, which with standard launch mode **destroys and recreates the activity**. The Activity-scoped `ChatVmsHolder` is cleared on recreation → `disposeAll()` → every chat VM's WS disconnects + keepalive stops → the server orphan-reaps the sessions → **all running turns die**, and the `pendingApproval` state (held in the old VM) is gone, so the approval banner never shows. **Fix:** `android:launchMode="singleTop"` on MainActivity — taps now arrive via `onNewIntent` (already implemented), the holder survives, the same VM shows the pending approval, and the turn continues. (`AndroidManifest.xml`.)
- **Root cause 2 — deep links only worked once:** `HermexNavGraph` used a one-shot `handledDeepLink` flag, so only the FIRST notification tap ever navigated; later taps (even for other sessions) did nothing. **Fix:** handle every new intent (the `LaunchedEffect` re-fires per `onNewIntent` → `setIntent`) with `launchSingleTop = true` so re-taps to the same chat are a no-op instead of stacking. (`MainActivity.kt`.)
- **Root cause 3 — only one turn notification visible:** every turn-finished notification used the constant id 1001, so a second session's completion REPLACED the first. **Fix:** per-session notification ids (`2000 + hash%9000`); the reply-cancel and reply-failed paths use the same per-session id. (`NotificationHelper.kt`, `NotificationReplyReceiver.kt`, `NotificationReplyService.kt`.)
- **Device QA:** start a turn that triggers an approval, background the app, tap the approval notification → app should open showing the approve/deny banner with the turn STILL running; approve → tool proceeds. Tap the same notification again → no-op, no stack. Two sessions finishing while away → two notifications (not one replaced).

### DONE in v0.1.102 (2026-08-17) — Live tokens/sec readout
- **Live streaming speed** — while a turn streams, the chat top bar (next to the context gauge) shows `≈N tok/s`, ticked every second. OpenAI-compatible APIs (DeepSeek) don't stream per-delta token counts (verified: gateway `message.delta` payload is `{text, rendered}` only), so the live rate is estimated client-side: chars/sec ÷ ~4 chars/token, labeled `≈`. Works identically for cloud and local models — both arrive as plain `message.delta` text, no server change. (`ChatScreen.kt` — `LaunchedEffect(state.isStreaming)` 1s ticker + top-bar Text.)
- **Exact turn average in the footer** — completed messages now show `N tokens · ≈X tok/s`: real token count (from the usage the app already parses — for the local Ollama Qwen path that's Ollama's exact eval counts, `session_completion_tokens` server-side) over the stream duration (message.timestamp = placeholder creation). Frozen via `remember` so it doesn't drift on recomposition. (`ChatScreen.kt` MessageBubble usage footer.)
- **Notes** — speed dips to ≈0 during tool-call gaps (honest); the ÷4 heuristic is English-oriented (code/other languages skew). If you later want exact live tok/s, it would need a server-side tokenizer patch (heavy) — not worth it for a live gauge.

### DONE in v0.1.101 (2026-08-17) — Session-open bottom scroll settle fix
- **Bug** — when a conversation loads, the last message's bottom outline sits ~1px short of the true bottom, its bottom border just hidden behind the composer ("99% there, splitting hairs").
- **Root cause** — the initial-load scroll (`SessionOpen`, one-shot `LaunchedEffect`) calls `autoScrollToBottom()` exactly once. `scrollToItem` clamps to the max scroll computed from the *pre-settle* layout; the loaded conversation's final layout (markdown parse, usage footer, bubble borders) lands a frame or two AFTER the messages arrive, so the true max scroll is a few px more and the last message ends up clipped at the composer seam. The v0.1.95 `StreamEnd` path already had a one-frame re-scroll settle for exactly this class of bug — the load path didn't.
- **Fix** — the `SessionOpen` effect now runs a 3-frame settle loop after the first scroll: wait a frame, and if the list still `canScrollForward` (not yet at the true bottom) and the user hasn't scrolled up, re-scroll (`reason="SessionOpenSettleN"`). No-op when the first scroll already lands exactly; respects `userScrolledUp`. (`ChatScreen.kt`.)
- **Device QA** — open a few conversations (long ones with markdown/tables/code, ones with a usage footer) and confirm the last message's border sits fully above the composer with the normal ~8dp gap.

### DONE in v0.1.100 (2026-08-17) — Cron notification reliability (missed 7am weather ping)
- **Forensics** — user missed the 7am weather ping (2026-08-16). Server side verified clean end-to-end: cron `0 14 * * *` UTC = 7am Vancouver, run started 14:00:07 / finished 14:05:33 UTC, status `completed`, no error; runs API returns `ended_at` (float seconds) matching the app's DTO; run output fetch works. The failure was the phone's alarm path. Phone evidence from the gateway auth log: pixel-8 (100.101.185.85) logged in **1987×** over ~2 days (~25-30/hour while connected) — a storm — with a login gap 14:05–14:15 UTC (7:05–7:15am PDT, deep Doze right when the run finished).
- **Root cause 1 — Doze delay:** `setAndAllowWhileIdle` is inexact; in deep Doze the 7:02am alarm can be deferred 10+ minutes (observed ~13 min), so the run-finished check ran late (or the re-check window was missed entirely). **Fix:** `armOne` now uses `setExactAndAllowWhileIdle` (manifest already declares `SCHEDULE_EXACT_ALARM` + `USE_EXACT_ALARM`), falling back to the inexact variant if the grant was revoked. The 7am alarm now fires on time. (`CronWatcher.kt`.)
- **Root cause 2 — re-check clobber (v0.1.77 bug class, reintroduced):** re-check alarms and next-occurrence alarms both used `jobId.hashCode()` as the PendingIntent request code, so ANY `sync()` during the re-check window — e.g. a `cron.changed` WS event arriving when the run completes and the server rewrites jobs.json — re-armed the job for the NEXT occurrence with the same identity, silently replacing the pending re-check → silence until the next day. **Fix:** re-check alarms use a distinct request code (`jobId.hashCode() xor 0x13579BDF`), so sync can never clobber them. (`CronWatcher.kt` `armOne(recheck=…)`.)
- **Root cause 3 — login storm:** the gateway's change watcher broadcasts `cron.changed` whenever `cron/jobs.json` mtime moves, which the scheduler does on every tick bookkeeping (last_run/next_run) — the chat VM forwards each event to `CronWatcher.sync()`, which called `DashboardApiClient.login()` **unconditionally**. **Fix:** 30-min explicit-login cooldown (the OkHttp 401 authenticator already re-logs in on demand); sync debounce 10s→30s; catch-up scans (up to 8 cronRuns fetches) bounded to once per 10 min. Storm should drop to ≤48 logins/day. (`CronWatcher.kt`.)
- **Device QA:** confirm tomorrow's 7am ping arrives ~7:02–7:10; the gateway auth log should show ~1-2 logins/day from pixel-8 instead of ~2000. If it's still missed, check phone **Do Not Disturb** (scheduled DND overnight suppresses the "cron" channel silently) and the notification permission.

### DONE in v0.1.99 (2026-08-16) — Slash command fix (root-caused via live gateway probe)
- **Verification** — connected to the local gateway (`hermes dashboard` PID 111987, creds from its env) and exercised the exact app RPCs: `complete.slash` returns clean items (text/display/meta/kind); `slash.exec /status` (live-direct) and `/help` (worker) return output; **DB-key resolution works** (the v0.1.44 fallback is present in the running code).
- **Root cause of "slash commands don't work"** — skill commands (a large share of the completion list, marked ⚡) are rejected by `slash.exec` with `4018 "skill command: use command.dispatch for /xxx"` — the app had no `command.dispatch` implementation, so every skill tap rendered "⚠️ Command failed". Reproduced live: `slash.exec /hermes-agent` → 4018; `command.dispatch` → skill content. (`tui_gateway/methods_tools.py` confirmed: `_PENDING_INPUT_COMMANDS` and skill/bundle commands must go through `command.dispatch`, which resolves `_sessions` by LIVE SID only.)
- **Fix** — `JsonRpcClient.commandDispatch(sessionId, name, arg)` (new RPC, 60s timeout). `sendSlashCommand` reworked: `execSlashWithFallbacks()` retries `slash.exec` → on 4018 "use command.dispatch" calls `command.dispatch(liveSid.ifBlank { sessionId }, base, arg)`; on 4001 re-registers via `session.resume` + retries once (self-heal, mirroring `submitWithSelfHeal`). Shared `applySlashResult()` now also treats `{"type":"skill","message":…}` as a prompt to submit (skill content → real turn), alongside the existing `"send"` type; `{"output":…}` still renders inline; unknown shapes fall back to raw JSON. (`DashboardChatViewModel.kt`.)
- **Slash completions work mid-turn** — removed the `!state.isStreaming` gate from the completion `LaunchedEffect`, aligning the menu with v0.1.92's mid-turn slash commands (`/stop`, `/steer`, `/queue`). (`ChatScreen.kt`.)
- **Device QA:** tap a skill command (⚡ row) → should now run via command.dispatch (submits skill content as a turn) instead of "Command failed"; `/queue`, `/steer` mid-turn; `/status` shows session output.

### DONE in v0.1.98 (2026-08-15) — Lock-screen notification Reply (pinned 2026-08-14 item ⑤)
- **Inline Reply action on turn-finished notifications** — `NotificationHelper.replyAction()` builds a "Reply" action with `RemoteInput` (`KEY_REPLY_TEXT`, label "Reply", `ic_menu_send` icon) targeting `NotificationReplyReceiver` (non-exported BroadcastReceiver, same-app PendingIntent). Every `postTurnFinished` notification now carries it, so replies chain. New low-importance "reply" channel for the in-flight foreground notification. (`NotificationHelper.kt`.)
- **`NotificationReplyService`** (new, foreground `dataSync` like `WsKeepaliveService`, `START_NOT_STICKY` so a system restart never re-sends a reply) — on the action: fresh `WsConnectionManager` + `JsonRpcClient` (the exact stack the chat VM uses), `session.resume(omitMessages=true)` to attach the session, `prompt.submit` with the DB key (server resolves), then waits (up to 10 min) for `message.completed` matching the DB key OR live sid (same two-phase filter as the VM) and posts the assistant's reply via `postTurnFinished` — the Reply action rides along, so the loop continues without ever unlocking. Failure → "⚠️ Reply failed" notification; stale turn notification cancelled on submit. (`notify/NotificationReplyService.kt`.)
- **`NotificationReplyReceiver`** (new) — reads `RemoteInput.getResultsFromIntent`, ignores blank replies, cancels the stale turn notification, starts the service with session/title/reply extras. (`notify/NotificationReplyReceiver.kt`.)
- **Manifest** — `<service .notify.NotificationReplyService foregroundServiceType="dataSync">` + `<receiver .notify.NotificationReplyReceiver exported="false">` (permissions already present from WsKeepaliveService). (`AndroidManifest.xml`.)
- **Device QA checklist:** reply from lock screen and from the shade; confirm the "Sending reply…" foreground pill appears and clears; confirm the reply lands as a new notification and chains (Reply action on the reply); verify no double-post when the chat for that session is open in the foreground (ID_TURNS replace-semantics prevent dupes).

### DONE in v0.1.97 (2026-08-15) — Thinking visibility toggle + model picker re-verify
- **"Show thinking" toggle (persisted)** — new `show_thinking` DataStore key (`SettingsRepository.showThinking`, default true), mirroring the v0.1.96 tool-calls toggle. Off hides thinking UI everywhere: the finished `ThinkingScrollBox`, the live `LiveActivityPanel` THINKING section (plus the divider above the TOOLS header), and the in-stream `LiveThinkingTicker`. Tools, the response, tasks panel and approval dialogs are unaffected. (`SettingsRepository.kt`, `ChatScreen.kt`.)
- **Quick toggle in the chat top bar** — a brain icon (`Icons.Outlined.Psychology`) sits next to the wrench: tinted primary when thinking is shown, dimmed when hidden; tap flips the persisted setting mid-conversation. (`ChatScreen.kt` TopAppBar actions — now two icons: wrench = tools, brain = thinking.)
- **Settings switch** — Settings → Appearance gains a "Show thinking" switch (subtitle: hides thinking only). (`SettingsScreen.kt`.)
- **Model selector + reasoning re-verified (no code changes needed)** — walked the full path: `ModelPickerSheet` loads `model.options` (grouped by provider, current highlighted, effort chips start at the session's current effort); **Apply to this chat** → `applyModelToSession` → `config.set` (model + reasoning) with `liveSid.ifBlank { sessionId }` → chip/gauge refresh via lightweight resume; **Save for new chats** → `model_pick`/`reasoning_pick` DataStore → `session.create` with `model` + `reasoning_effort`. Chip stays live via `session.info` + 30s poll. None of the v0.1.95/96/97 display changes touched these paths.

### DONE in v0.1.96 (2026-08-15) — Tool-call visibility toggle + live TOOLS section
- **"Show tool calls" toggle (persisted)** — new `show_tool_calls` DataStore key (`SettingsRepository.showToolCalls`, default true). Off hides tool-call UI everywhere: the finished `ToolScrollBox`, the live panel's tool rows + "N working · N tools" counter, and the tool detail dialogs (gated via the box). Thinking, the response, the tasks panel and approval dialogs are unaffected — exactly the approved scope. (`SettingsRepository.kt`, `ChatScreen.kt`.)
- **Quick toggle in the chat top bar** — a wrench icon (`Icons.Outlined.Handyman`) in the TopAppBar actions: tinted primary when tools are shown, dimmed when hidden; tap flips the persisted setting mid-conversation. (`ChatScreen.kt` TopAppBar.)
- **Settings switch** — Settings → Appearance gains a "Show tool calls" switch with a subtitle explaining it hides tools only. (`SettingsScreen.kt`.)
- **Tools separated from thinking while streaming** — the docked `LiveActivityPanel` now renders tools as their own labeled section: a `TOOLS · N` header (matching the finished `ToolScrollBox` style) with a subtle divider above it when thinking is present, so tools no longer read as part of the same box as thinking. The section (and the tool counter) vanishes when the toggle is off; auto-scroll count accounts for the header item. (`ChatScreen.kt` `LiveActivityPanel`, new `showTools` param.)

### DONE in v0.1.95 (2026-08-15) — Theme extra surfaces (pinned #6)
- **Code blocks / thinking box / tool cards / context gauge get their own colors** — previously all four derived from `surfaceVariant` (the "Assistant bubbles & top bars" pick) with hardcoded alpha multipliers, so you couldn't give code a dark slab while keeping thinking subtle. New `UiColorOverrides` fields (`codeBlock`, `thinkingBox`, `toolCard`, `gaugeTrack`) resolve into a `UiSurfaces` value provided by a new `LocalUiSurfaces` CompositionLocal inside `HermexTheme`; defaults are byte-identical to the old alpha math, so nothing changes until you touch the new rows. (`Theme.kt`.)
- **Settings → Appearance gains 4 rows** — Code blocks / Thinking box / Tool cards / Context gauge, each a swatch row from the same palette (Default = derive). New DataStore keys `ui_code_block_hex` / `ui_thinking_hex` / `ui_tool_card_hex` / `ui_gauge_hex`; `applyAppearance` extended (new params default null so presets still compile). (`SettingsRepository.kt`, `SettingsScreen.kt`, `MainActivity.kt`.)
- **Honest live preview** — `AppearancePreview` now renders a code-block sample (header + mono line), a tool-card row sample (🔍 web_search · 1.2s ✓), the thinking box with the thinking color, and the context gauge as an accent fill over the themeable track — the old hardcoded `surfaceVariant@35%` thinking box that lied is gone. (`SettingsScreen.kt`.)
- **Terminal preset sets code blocks to Input dark `#121512`** — the classic dark-on-charcoal slab; chip selection checks cover the new fields, Classic/Reset clear them. (`SettingsScreen.kt`.)
- **Wired into the chat** — gauge `trackColor`, `ThinkingScrollBox` + live thinking pill, `ToolScrollBox` + `LiveActivityPanel` + tool-card surfaces, `CodeBlockShell` header, and code-block/inline-code backgrounds via the markdown library's `markdownColor(codeBackground=…)` hook on the `Markdown` call. Diff colors (semantic red/green/blue) and alert/error surfaces intentionally untouched. (`ChatScreen.kt`.)
- **Minor default unification (note):** the live-activity panel, live thinking pill and tool-card surfaces previously used slightly different alphas (0.45 / 0.6 / 0.4); they now share the surface default (0.35) unless overridden. Code-block content background now follows the header at 55% surfaceVariant via `markdownColor` instead of the library's opaque default — marginally lighter, more consistent.
- **Stream-end re-scroll** — when a turn finishes, the completed message's final layout (usage footer, markdown settle) lands a frame AFTER the state write, so the auto-scroll key (content length) doesn't re-fire and the last line could end up hidden behind the composer. The StreamLoop now waits one frame when streaming stops and re-scrolls to the true bottom (skipped when the user has scrolled up). (`ChatScreen.kt` StreamLoop.)

### DONE in v0.1.69 (2026-08-13)
- **slash.exec timeout 30s → 180s** — `/compress` on a big session takes minutes; the client bailed at 30s with `timed out after 30000ms` (and the command may have actually completed server-side). (`JsonRpcClient.slashExec`.)
- **Slash menu keeps the `/`** — server completions omit the leading slash (it's already typed); tapping inserted bare text, killing the command. Prefix restored on insert. (`ChatScreen` popup.)

### DONE in v0.1.94 (2026-08-15)
- **Reply/Copy off long-press** — the v0.1.93 long-press → Reply/Copy dialog was stealing the long-press gesture from `SelectionContainer`, so text selection never fired (user-reported). Removed the bubble's `combinedClickable(onLongClick)`; text selection now works natively, and Reply/Copy moved to a small "⋯" (`MoreVert`) button overlaid on each bubble's corner. (`ChatScreen.kt` `MessageBubble`.)

### DONE in v0.1.93 (2026-08-15)
- **Code-block copy buttons** — every code block/fence now renders with a header bar (language label + Copy button) above the highlighted code; copies just the code. Custom `CopyableCodeBlock`/`CopyableCodeFence` wrap the existing `highlightedCodeBlock`/`highlightedCodeFence` via `markdownComponents`. (`ChatScreen.kt` `CodeBlockShell`.)
- **Selectable text** — messages wrapped in `SelectionContainer`, so any text can be selected (not just whole-message copy). (`ChatScreen.kt` `MessageBubble`.)
- **Reply-to-message** — long-press a message opens a Reply/Copy dialog; Reply quotes the message (`> …`) into the composer for follow-ups like "what did you mean by this". Replaces the old long-press-copy-whole-message. (`ChatScreen.kt` `replyTarget` + dialog.)

### DONE in v0.1.92 (2026-08-15)
- **Mid-turn slash commands** — removed the Stop button; the composer now always shows Send. While a turn streams you can type `/stop` / `/interrupt` / `/halt` (→ `session.interrupt` — NOT slash.exec, whose `/stop` kills background processes), `/steer <x>` (injects after the next tool call), `/queue <x>` (submits as a prompt, server auto-queues), or normal text (also auto-queued). `sendSlashCommand` now handles the `{"type":"send","message":...}` response shape (was rendering raw JSON for `/queue`). (`ChatScreen` button row, `DashboardChatViewModel.sendMessage`/`submitPrompt`/`sendSlashCommand`.)
- **Queued turns render** — a prompt sent mid-turn no longer fights the live placeholder: `submitPrompt` adds the user message without a second streaming placeholder, and `ensureStreamingPlaceholder` lazily creates one when the queued turn's first delta arrives. All delta/thinking/tool handlers route through it; `isStreaming` now derives from `messages.any { isStreaming }`. (Known minor: multiple queued messages stack responses in submission order after the last user message, not interleaved.) (`DashboardChatViewModel`.)

### DONE in v0.1.91 (2026-08-15)
- **Model switcher fixed** — "Apply to this chat" sent `/model` + `/reasoning` through `slash.exec`, but the server's slash-exec mirror (`_mirror_slash_side_effects`) has **no `/reasoning` branch** (silently no-ops) and only mirrors `/model` when a live agent already exists (fresh/reaped sessions no-op; busy sessions rejected). Now uses the **`config.set` RPC** (`key=model`/`key=reasoning`) — the desktop's path, which defers a busy model switch to the next turn and builds the agent for a fresh session. `config.set` resolves `_sessions` by LIVE SID only, so the app sends `liveSid.ifBlank { sessionId }` (not the DB key, which would apply the change globally). Verified live: `config.set model=deepseek-v4-pro` + `reasoning=high` → server emits `session.info {model, provider, reasoning_effort}`. (`JsonRpcClient.configSet`, `DashboardChatViewModel.applyModelToSession` + `refreshUsageAndModel`.)
- **Chip refreshes from session.info** — the model/reasoning chip now updates from `session.info` notifications (and resume info + the 30s poll), so a switch made from the app OR desktop/Telegram reflects immediately. (`DashboardChatViewModel` SessionInfo handler, `loadMessages`, `setScreenVisible`.)
- **Picker starts at current effort** — no longer hardcodes "medium", so a model-only switch won't silently reset reasoning. (`ChatScreen.ModelPickerSheet`.)
- `AGENTS.md` corrected from stale v0.1.48 → v0.1.91.

### DONE in v0.1.90 (2026-08-15)
- **Mid-session switch uses slash.exec** — "Apply to this chat" sent `/model …` via `prompt.submit`, which delivers it as LITERAL text (proven: Jeff's typed `/model` + `/reasoning` arrived as regular messages). The server only intercepts slash commands through the `slash.exec` RPC (desktop/TUI path). Also discovered: slash commands are rejected while a turn streams (busy session) — apply when idle. (`applyModelToSession`.)

### DONE in v0.1.89 (2026-08-15)
- **New-session 4007 fix** — root cause: the server only flushes a new session's DB row on its FIRST run, so `session.resume` right after `session.create` 4007s (reproduced server-side: fails with EITHER id, model param irrelevant). `prompt.submit` works on the fresh session (agent attaches + persists — verified). `loadMessages` now treats 4007 as a fresh/deleted session → empty chat, no error screen; the first message attaches.
- **Mid-session model switching** — the desktop/Telegram switch via `/model` + `/reasoning` slash commands (session-scoped). The picker now has "Apply to this chat" (submits those commands, instant switch in the CURRENT session) + "Save for new chats" (sticky default). (`applyModelToSession`, `ModelPickerSheet`.)

### DONE in v0.1.88 (2026-08-14)
- **Model + reasoning display & picker** — compact chip next to the context gauge shows the current model (from `session.resume` info.model, refreshed on every 30s poll) + reasoning effort (config.yaml `reasoning_effort` or the saved pick). Tap opens a bottom sheet: model list from `model.options` (grouped by provider, current highlighted) + Low/Med/High effort chips. The pick persists (SettingsRepository) and flows into `session.create` for NEW chats — the desktop-composer contract; the server has no mid-conversation switch RPC yet (candidate patch). (`JsonRpcClient.modelOptions/createSession`, `ChatViewModelContract`, `DashboardChatViewModel`, `SessionsViewModel`, `ChatScreen.ModelPickerSheet`.)

### DONE in v0.1.87 (2026-08-14)
- **Full-bleed assistant messages** — assistant text was capped at 400dp inside the bubble (`Markdown widthIn`) plus 8dp side insets, so neither the bubble nor the text used the full screen width (user-reported with screenshot). Assistant bubbles now go edge to edge (0 side padding) and text spans the full bubble; user bubbles keep the 320dp right-aligned style. (`ChatScreen.MessageBubble`.)
- **Always-live context gauge** — the gauge's 5s poll stopped after the FIRST reading, then relied on `session.info` events, which aren't guaranteed to reach the phone (single-owner transport — desktop usually owns the live stream) → gauge froze at the last value or "—/—" forever. The poll now runs continuously while the chat is open: 2s burst until first data, then a lightweight resume every 30s. (`DashboardChatViewModel.setScreenVisible`.)

### DONE in v0.1.86 (2026-08-14)
- **Stale catch-up deliveries labeled** — a missed 7am weather run surfaced 12h late via the catch-up net (first successful sync after the half-open saga), delivering "Good morning" at 7pm with no context. Catch-up notifications for runs started >30 min ago now get `⏪ Missed run from 7:00 AM —` prepended (phone-local time), so stale briefings are instantly recognizable. (`CronWatcher` catch-up, `NotificationHelper.postCronRun` missedLabel.)

### DONE in v0.1.85 (2026-08-14)
- **Half-open reconnect fix** — when a reconnect's lightweight session re-attach (`sessionResume(omitMessages=true)`) failed, the app logged and gave up: WS connected but session unattached → sends went into the void until the 30s "jpc error" timeout. The failure path now retries with the FULL `loadMessages()` re-attach (resume + history reload, handles 4001 internally). Root-caused from Jeff's report: approval-test denial worked (20:16), then background/swipe (20:24) → server orphan-reap → reopen = half-open. (`DashboardChatViewModel` reconnect-resume catch.)

### DONE in v0.1.84 (2026-08-14)
- **Approval-request notifications** — the app already received `approval.request` over WS and rendered an in-chat banner, but only when watching the chat; backgrounded requests sat unseen until the 60s timeout. Now an `ApprovalRequest` while not watching posts a high-priority "Alerts" notification (tool name + truncated args), tap deep-links to the chat with the approve/deny banner. Also bumped `approvals.timeout` 60→300 in config.yaml so requests don't expire while Jeff is away. (`NotificationHelper.postApproval`, `DashboardChatViewModel`, `~/.hermes/config.yaml`.)

### DONE in v0.1.83 (2026-08-14)
- **Cron schedule times in phone-local timezone** — one-shot jobs showed the server's UTC-naive "once at 2026-08-14 16:12"; now rendered in the phone's tz ("Once: Aug 14, 9:12 AM"), intervals as friendly text ("Every 90 minute(s)"), cron exprs as-is. (`CronJob.schedule` DTO, `scheduleDisplayLocal()`.)

### DONE in v0.1.82 (2026-08-14)
- **Cron list re-arms alarms** — root cause of the 1-min test not pinging: opening Settings → Cron only refreshed the list; `CronWatcher.sync` ran only on chat connect, so a job created while the app was closed never had its alarm armed. `CronScreen.load()` now syncs after every list fetch (also runs catch-up — missed test runs surface on open). (`SystemScreens.kt`.)
- **Session last-activity times** — landing-screen session rows show relative last-message time (`5m ago` / `3h ago` / `2d ago`) via `last_activity_at` (fallback `last_active` → `started_at`) + a "Last message: <description>" provenance line. (`SessionSummary` DTO, `SessionsScreen`.)

### DONE in v0.1.81 (2026-08-14)
- **One-shot catch-up** — the 10-min cron test never notified because (a) the job was created via API while the app was closed → no sync → no alarm armed, and (b) catchUpMissedRuns only considered enabled jobs with a future next_run, but fired one-shots are marked `state=completed, enabled=false, next_run_at=null` — excluded twice over. Catch-up candidates now = 5 soonest scheduled jobs + up to 3 completed one-shots (started-within-12h + finished + unseen checks bound it). Missed one-shot reminders surface on the next app open. (`CronWatcher.catchUpMissedRuns`.)

### DONE in v0.1.80 (2026-08-14) — Release B
- **Full cron management in the app** — CronScreen: **+ button** to create a job (name, schedule, prompt, deliver dropdown from `/api/cron/delivery-targets`), **edit** (pencil) and **delete** (trash, always confirm dialog — destructive) per row. `CronEditScreen` handles create (`POST /api/cron/jobs`) and edit (`PUT` with `{updates}`); after any change the alarm watcher re-arms so notifications follow the new schedule. Schedule accepts cron expressions or interval shorthand (`every 90m`). (`DashboardApiClient.cronCreate/cronUpdate/cronDelete/cronDeliveryTargets`, `SystemScreens.kt`.)

### DONE in v0.1.79 (2026-08-14) — Release A
- **Message boxes refined (③)** — thinking + tools boxes now have matching slim headers (THINKING / TOOLS · N), dropped the "scroll for more" / "tap a tool for details" hints — pure content boxes, both sitting above the answer. (`ThinkingScrollBox`, `ToolScrollBox`.)
- **Live theme preview + richer palette (②)** — Settings → Appearance now shows a live mini-chat preview rendering with the current overrides (accent gauge, user bubble, thinking box, flat assistant reply, mono font) so color changes are visible instantly. Per-part color rows use a richer palette (Terminal green/charcoal/deep green/mint/slate/input dark/cyan/purple/red/white) and swatch rows are horizontally scrollable. (`AppearancePreview`, `uiOptions`, `ColorSwatchRow`.)

### DONE in v0.1.78 (2026-08-14)
- **Cron reports land in the app** — ALL 30 cron jobs (default + link-curator profiles) flipped `deliver=telegram:*`/origin→telegram → `local` via the update API (verified on disk: zero telegram-delivering jobs remain). The app is now the cron report channel: `CronWatcher` fetches the finished run's output via `GET /api/sessions/{id}/messages` (last assistant text) and shows it in the notification (BigText, 400 chars) — tap opens the run session. Verified live against the 7am weather run ("Good morning, Jeff! It's a sunny, mild 16°C…"). (`DashboardApiClient.sessionMessages`, `CronWatcher.fetchRunOutput`, `NotificationHelper.postCronRun`.)

### DONE in v0.1.77 (2026-08-14)
- **Cron notification clobber fix** — root cause of the silent morning weather cron: `onAlarm`'s still-running branch armed a 2-min re-check alarm, then called `sync()`, which re-armed the SAME job with the SAME request code for the next occurrence — clobbering the re-check (one check, then silence until the next day). Server verified the 7am job ran + finished (`cron_complete`); the phone never checked again. Fix: re-check path arms alone; notify/give-up path syncs. Patience 2min×10 → 5min×24 (2h). (`CronWatcher.onAlarm`.)
- **Missed-run catch-up** — `sync()` now notifies for finished runs never reported (bounded: next_run within 48h, run started within 12h, max 3 per sync) — missed morning runs surface on next app open. Sync-on-connect added (VM connect → re-arm + catch-up). (`CronWatcher.catchUpMissedRuns`, `DashboardChatViewModel`.)

### DONE in v0.1.76 (2026-08-14)
- **Version bump only** — the v0.1.75 fix was correct, but the CI auto-published the same v0.1.75 tag from the master push before the manual release, so Obtainium already had 0.1.75 and showed no update. 0.1.76 = identical code, new version, update visible.

### DONE in v0.1.75 (2026-08-14)
- **Turn-finished notifications fire on app backgrounding** — bug: the guard used `screenVisible` (chat screen in composition), which stays TRUE when the app is merely backgrounded, so backgrounding during a turn produced no notification (user-reported within an hour of v0.1.74). MainActivity now tracks foreground/background via `onStart`/`onStop` → `AppState.isBackgrounded`; the MessageCompleted guard fires when navigated away OR app backgrounded. (`AppState.kt`, `MainActivity`, `DashboardChatViewModel`.)

### DONE in v0.1.74 (2026-08-14)
- **Turn-finished notifications (A1)** — `message.complete` while the chat isn't visible posts a local notification ("turns" channel); tap deep-links into the session (`open_session` extra → MainActivity → chat route). (`DashboardChatViewModel.MessageCompleted`, `NotificationHelper`, MainActivity deep link.)
- **Scheduled-alarm cron watcher (A2, calendar-sync model)** — learns each job's `next_run_at` from the cron API, arms an AlarmManager alarm (run time + 2min buffer, `setAndAllowWhileIdle`), wakes ONLY then, checks the job's latest run via `/api/cron/jobs/{id}/runs` (cron runs ARE sessions), notifies when finished ("cron" channel, tap opens the run session), re-arms from fresh data. Still-running runs re-check every 2min (capped 10×); moved schedules discovered at the old alarm and re-armed. Re-sync triggers: app start, BOOT_COMPLETED/MY_PACKAGE_REPLACED, every alarm fire, and the gateway's `cron.changed` WS broadcast (forwarded from the VM's unknown-event path). (`CronWatcher`, `CronAlarmReceiver`, `DashboardApiClient.cronRuns`.)
- **Battery** — WS ping 30s → 5min (server has no WS read timeout; streaming traffic verifies liveness), plus a few alarm wakeups/day instead of minute-polling. POST_NOTIFICATIONS runtime request added (was missing entirely). (`WsConnectionManager`, `MainActivity`.)

### DONE in v0.1.73 (2026-08-13)
- **History reload: tools merged into the tool box, thinking restored** — root cause from a raw resume-payload probe: the server stores tool activity as SEPARATE `role='tool'` rows (`{name, context}`) and assistant thinking in `reasoning`/`reasoning_content`; the reload mapped each row 1:1 so tool contexts rendered as jumbled standalone bubbles and thinking was lost. The loader now merges `role='tool'` rows into the preceding assistant message's `toolCalls` (completed calls with `preview = context`) and carries reasoning into `thinkingText`, so reopened sessions show the same clean stack as live: thinking box → tools box → answer. Tool-only assistant rows (blank text) skip the empty bubble; tool box tightened to 140dp. (`JsonRpcClient.MessageData.resolvedThinking`, `DashboardChatViewModel.loadMessages` merge, `ChatScreen` bubble guard.)

### DONE in v0.1.72 (2026-08-13)
- **Connection menu in Settings** — server address (IP:port), username, password now editable in Settings → Connection with a Save & Reconnect button: validates via `status()` + `login()`, persists to the encrypted KeychainStore (username added as a stored field, `KEY_DASHBOARD_USERNAME`), then recreates the activity so held VMs reconnect to the new gateway. The hardcoded `"jeff"` username in `DashboardApiClient`'s 401 re-login path now reads the stored value. Makes the app pointable at any Hermes gateway (shareable with brother). (`SettingsScreen.kt` Connection section, `KeychainStore`, `DashboardApiClient.setUsername`, `HermexApplication` restore.)

### DONE in v0.1.71 (2026-08-13)
- **Context gauge never hides (desktop-mirror)** — root cause proven via server probe (reaped agent → NO usage data; live agent → full data) + desktop source (apps/desktop `gateway-event.ts` merges usage over last-known without clearing; `statusbar.tsx` renders `context_used ?? 0`). The phone now always renders the gauge slot once the chat is open: last-known reading when the server is quiet, `—/—` dimmed before any reading exists, never vanishing. The 5s poll still upgrades it to live data within seconds when the agent has it. (`ChatScreen.kt` top bar.) Server-side estimate-on-rebuild remains a future hermes-agent PR for instant freshness after reaps.

### DONE in v0.1.70 (2026-08-13)
- **Slash commands show a working spinner** — `/compress` ran silently for minutes (user: "doesn't seem to do anything"). `sendSlashCommand` now inserts a streaming placeholder message (spinner) before the RPC and resolves it with the output or error when done. (`DashboardChatViewModel.sendSlashCommand`.)

### DONE in v0.1.69 (2026-08-13)
- **Context gauge self-heals, guaranteed** — verified live via WS probe: server always reports `usage.context_used/context_max` on both full and omit-messages resumes (255k/1.0M at probe time); the app was catching a transient null reading (agent rebuild/compression window) with no retry. `setScreenVisible(true)` now re-resumes in a burst (2s ×3) then polls every 5s while the chat is visible until the gauge has real data. Cannot stay hidden while the server has data. (`DashboardChatViewModel.setScreenVisible`.)
- **Slash fixes (committed by parallel Telegram session, `3b5051e`)** — slash.exec timeout raised 180s (compression can take >30s), and selecting a slash menu item keeps `/` in the composer for chaining. Both commits ship in v0.1.69.

### DONE in v0.1.68 (2026-08-13)
- **Tool calls fold into a scrollable box** — finished messages no longer stack individual tool cards above the answer; they consolidate into one compact scrollable TOOLS box (icon · name · elapsed · ✓, same row style as the live panel via shared `ToolActivityRow`). Tap a row to expand the full card (call context, args, inline diff, result) in a dialog. Stack: thinking box → tools box → answer. (`ChatScreen.kt` — `ToolScrollBox`, `ToolActivityRow`.)

### DONE in v0.1.67 (2026-08-13)
- **Slash commands execute** — messages starting with `/` route to `slash.exec` (same path as desktop/TUI) instead of `prompt.submit`; output lands as an assistant message; context gauge refreshes after (compress changes it). `/compress`, `/model`, `/new` etc. now work from the phone. (`JsonRpcClient.slashExec`, `DashboardChatViewModel.sendSlashCommand`.)
- **Message borders** — assistant + user bubbles get a 1dp outline in the context-gauge color (primary, 28% alpha) — first pass at "borders around conversations". (`MessageBubble`.)

### DONE in v0.1.66 (2026-08-13)
- **Thinking persists after the turn** — finished messages now keep a capped scrollable THINKING box (max 180dp, monospace, "scroll for more") above the tool cards + answer; during streaming the live docked panel owns thinking instead. (`ThinkingScrollBox` in ChatScreen.)
- **Slash-command NPE fix (committed by parallel desktop session, `9443c0c`)** — `itemsIndexed(slashItems!!)` inside LazyColumn's deferred DSL lambda re-read mutable state → NPE when the list was nulled (focus loss/stream start/tap). Fixed by capturing to a local before the guard. Both commits ship in v0.1.66.

### DONE in v0.1.65 (2026-08-13)
- **CRASH FIX (v0.1.64 regression)** — running the same tool twice in one turn (e.g. `patch` ×2) made `ToolStart` overwrite the FIRST card's id by name-match → two cards shared one id → the live panel's keyed `LazyColumn` threw `Key "...call_00_... was already used"`. Fixed at source (`ToolStart`: match id or first unstarted card; `ToolComplete`: id or first incomplete) AND the panel uses positional keys (history-replay ids can also collide). (`DashboardChatViewModel` tool handlers, `LiveActivityPanel` itemsIndexed.)
- **No double-thinking** — in-bubble thinking toggle removed entirely; thinking now lives only in the live panel (finished messages show tools above the answer, no thinking block). (`MessageBubble`.)
- **Slash commands** — typing `/` pops a server-filtered command list above the composer (`complete.slash` RPC, 150ms debounce, `SlashItem` DTOs, `ChatViewModelContract.completeSlash`), narrows as you type, tap fills the command; commands vs skills color-coded. (`JsonRpcClient.completeSlash`, ChatScreen popup panel.)

### DONE in v0.1.64 (2026-08-13)
- **Docked Live Activity panel** — while a turn runs, the last streaming message's thinking + tool calls render in a small scrollable panel docked above the composer (`LiveActivityPanel`: "● Live activity" header with working/tool counts, THINKING monospace block, compact tool rows with icon/name/elapsed/spinner-or-✓, auto-scrolls to newest). The streaming answer grows above the panel. On completion the panel vanishes; the finished message shows tools + thinking above the final answer (in-stream thinking ticker + tool cards hidden while streaming to avoid duplication). (`ChatScreen.kt` — `LiveActivityPanel`, item-composable gating on `!msg.isStreaming`.)

### DONE in v0.1.63 (2026-08-13)
- **Context gauge self-heals** — phone's debug log showed a full streamed turn with ZERO `session.info` receipts: the gauge only ever got data from the one-time resume snapshot, which can miss real context after a server-side agent rebuild (auto-compression seen in logs: 478k→443k). Fix: on every chat open (`setScreenVisible(true)`) and every WS reconnect, the VM fires a lightweight `session.resume(omit_messages=true)` and parses `usage.context_used/context_max` into the gauge. `session.info` events remain as live per-turn updates when they arrive. (`JsonRpcClient.sessionResume(omitMessages)`, `DashboardChatViewModel.setScreenVisible` + reconnect listener.)

### DONE in v0.1.62 (2026-08-13)
- **Tool cards above the response** — tool cards were rendered inside the assistant bubble AFTER the content; long tool runs pushed the reply below the fold. Now rendered ABOVE the bubble in the message item (desktop-style: tool activity first, response lands last at the bottom). (`ChatScreen.kt` — moved the toolCalls block out of `MessageBubble` to the item composable.)

### DONE in v0.1.61 (2026-08-13)
- **System panels** — menu drawer gains a SYSTEM section (Cron Jobs / Skills & Tools / Config (core / soul)). `DashboardApiClient` +7 cookie-authed REST calls + DTOs (`cronJobs`, `cronAction` pause/resume/trigger, `skillsList`, `skillContent`, `toggleSkill`, `configRaw`, `saveConfigRaw` — note: PUT config body is `yaml_text`, verified against `RawConfigUpdate`; all shapes verified live before shipping). `feature/system/SystemScreens.kt`: CronScreen (list + state chip + pause/resume/run-now), SkillsScreen (list + enable switch), SkillDetailScreen (raw SKILL.md viewer), ConfigScreen (live config.yaml editor, monospace, warning banner, Save-when-changed). Routes `cron`/`skills`/`skills/{name}`/`config` in MainActivity.

### DONE in v0.1.60 (2026-08-13)
- **Session activity indicators** — `ChatVmsHolder.activeSessions: StateFlow<Map<sessionId, isStreaming>>` (snapshotFlow over each held VM's `uiState.isStreaming`, distinctUntilChanged). Session list shows spinner + "working" (card rows) / "● working…" + spinner (drawer rows) for chats with a background turn. **Completed-while-away banner**: `ChatUiState.completedWhileAway`, set in `onTurnFinished()` (message.completed/run.completed) when the screen isn't visible (`setScreenVisible` via DisposableEffect in ChatScreen); re-entry shows "✓ Turn finished while you were away" + View latest (clears + scrolls). (`ChatVmsHolder`, `DashboardChatViewModel`, `UiModels`, `ChatViewModelContract` +2 open funs, `ChatScreen` banner, `SessionsScreen` isActive param.)

### DONE in v0.1.59 (2026-08-13)
- **Context gauge fix** — `JsonRpcClient` parsed `session.info` with `info = params` (whole event object) instead of `params.payload` (the info dict with `usage.context_used/context_max`). `parseContextUsage` always missed → after an app restart the gauge never came back (resume reports a fresh agent with no context data yet, and per-turn `session.info` updates were silently dropped). Fixed; gauge now live-updates after every turn. (`JsonRpcClient.kt` session.info branch.)

### DONE in v0.1.58 (2026-08-13)
- **Terminal preset: monospace + mint text** — the "all black" complaint was missing green entirely. Now the preset also sets: **text override `#A5D6A7`** (soft green-white onSurface/onBackground; onSurfaceVariant = 72% alpha → muted mint labels/timestamps/icons like the desktop) and **monospace font everywhere** (`MonoTypography` in Type.kt, `HermexTheme(monospace=)` param). New Appearance controls work with any theme: Text color swatch row + Monospace switch. `UiColorOverrides.text`, `SettingsRepository.uiTextHex`/`uiMonospace` + `applyAppearance` 5-arg, MainActivity wiring. Terminal preset now: accent `#00FF41`, bg `#0A0C0A`, user `#1E3D24`, assistant `#0A0C0A`, text `#A5D6A7`, mono on.

### DONE in v0.1.115 (2026-08-19)
- **Improved command approval dialog** — title now shows the actual tool name (e.g., "Approve: bash") instead of generic "Approve Command?"; added explicit "Command" and "Arguments" section headers so each piece of info is labeled; ViewModel now parses JSON args with Gson for any tool type (not just bash regex), extracting from common fields (`cmd`, `command`, `arguments`, `arg`, `query`, `text`, `content`, `path`, `file_path`); falls back to "command" when `toolName` is null instead of the confusing "unknown"); if a "tool" field exists in JSON, uses it as the tool name. (`DashboardChatViewModel.kt`, `ChatScreen.kt`)

### DONE in v0.1.114 (2026-08-19)
- **Improve command approval dialog** — shows extracted command line for bash tool calls in monospace, title changed to "Approve Command?".

### DONE in v0.1.57 (2026-08-13)
- **Terminal preset: flat desktop look** — preset now sets ALL four keys: accent `#00FF41`, bg `#0A0C0A`, **user bubble `#1E3D24`** (muted green box), **assistant bubble `#0A0C0A`** (same as bg → invisible → flat text-on-charcoal like the desktop). Previously only bg+accent were set so the assistant bubble stayed green-tinted — looked nothing like the desktop. (`SettingsScreen.kt` preset + selected check.)

### DONE in v0.1.56 (2026-08-13)
- **Photo attach** — composer 📷 → `ActivityResultContracts.PickVisualMedia` (Android Photo Picker, no permission needed) → downscale ≤1600px JPEG q82 base64 (`downscaleAndEncode`) → `image.attach_bytes` RPC (staged into session) → next `prompt.submit` carries it; thumbnail preview (Coil `AsyncImage`) with remove X; blank text falls back to server placeholder. (`JsonRpcClient.attachImage`, `DashboardChatViewModel.sendMessageWithImage`, `ChatViewModelContract.sendMessageWithImage`, ChatScreen.)
- **Voice messages** — composer 🎤 → `MediaRecorder` WebM/Opus (RECORD_AUDIO runtime permission, manifest added) → `POST /api/audio/transcribe` (dashboard :9119, cookie auth, Whisper-backed; `DashboardApiClient.transcribeAudio` + DTOs) → transcript appended to composer. Red recording indicator + elapsed timer; transcribing state. **No server changes** — `image.attach_bytes` + `/api/audio/transcribe` both pre-existing (verified live: 401 unauthenticated).

### DONE in v0.1.55 (2026-08-13)
- **Terminal preset matched to desktop screenshot** — extracted exact colors from the desktop app screenshot via the vision pipeline (gemma4:cloud): main bg `#0A0C0A` (near-black charcoal w/ green tint), sidebar `#070807`, mint labels `#76C76B`, neon accent `#00FF41`. Terminal preset background corrected `#0A1A15` → `#0A0C0A`. (`SettingsScreen.kt` preset values.)

### DONE in v0.1.54 (2026-08-13)
- **Inline diffs in tool cards** — `tool.complete` events carry `inline_diff` (ANSI-colored unified diff from `render_edit_diff_with_delta`, for `write_file`/`patch`/`skill_manage`) which the app previously dropped. Now: `RpcNotification.ToolComplete.inlineDiff`, `UiToolCall.inlineDiff`, captured in the VM, rendered in the expanded tool card via `DiffView` (monospace, red/green tinted `+`/`-`, blue hunks, teal file lines, scrollable ≤280dp). ANSI stripped + re-classified by line prefix (`parseDiffLines`/`DiffLine`/`DiffKind` in ChatScreen.kt). No server changes.

### DONE in v0.1.53 (2026-08-13)
- **Background turns** — leaving a chat no longer kills the running task. Chat ViewModels were scoped to the nav back-stack entry: backing out destroyed the VM → WS disconnect → server orphan-reaper (20s grace) tore the session down. New `ChatVmsHolder` (Activity-scoped AndroidViewModel) keeps one `DashboardChatViewModel` per session alive across navigation — WS + keepalive stay up, turns finish in the background, reopening shows live state. `DashboardChatViewModel.dispose()` extracted; keepalive `stop` moved to the holder (only when all chat VMs go, i.e. Activity finish). (`ChatVmsHolder.kt`, `MainActivity` — `viewModel<ChatVmsHolder>()` + `getOrCreate(sessionId)`.)

### DONE in v0.1.52 (2026-08-13)
- **Full-width assistant messages** — `MessageBubble` assistant bubbles now `fillMaxWidth()` (edge to edge, 8dp margins); user bubbles keep the 320dp right-aligned cap. Kills the "centered column" look. (`ChatScreen.kt` — `bubbleWidthModifier`.)

### DONE in v0.1.51 (2026-08-13)
- **Tasks panel** — collapsible card pinned above the chat messages showing the agent's live todo list ("Tasks 2/5" + active task + progress bar; expand → done ✓ / active spinner / pending ○, cancelled struck through). Source: `tool.complete` events with `name == "todo"` (server's `payload.todos` — the documented source of truth), plus history replay on `session.resume` (last tool message `role=="tool" && name=="todo"`). Auto-expands on first appearance mid-turn, respects manual collapse. `todo` excluded from tool-card rendering (dedicated UI). (`ChatScreen.kt` TasksCard/TodoRow, `DashboardChatViewModel.parseTodos/parseTodosFromString`, `UiTodo`, `ChatUiState.todos/todosExpanded`, `ChatViewModelContract.toggleTodosExpanded`.)

### DONE in v0.1.50 (2026-08-13)
- **Compact session browser** — menu (drawer): pinned + **RECENT (top 5)** + expandable "All sessions (N)" (grouped by source, scrollable in-menu). Search still matches everything. Main page trimmed to pinned + recent 5 + "Browse all sessions" row (opens menu); no full list on main. Empty state gets a New session button. (`SessionsScreen.kt` — `RECENT_LIMIT = 5`, `showAllSessions` expander.)

### DONE in v0.1.49 (2026-08-13)
- **Navigation drawer** — hamburger on the session list opens a ModalNavigationDrawer: New session (session.create RPC), client-side search, PINNED section (local DataStore string-set, desktop-style — long-press-free, 📌 icon per row), sessions grouped by source (TELEGRAM/API/TUI…, sorted by count) with counts. Main list mirrors the same pinned+grouped structure. (`SessionsScreen.kt` full rework, `SessionsViewModel` + `createSession`, `JsonRpcClient.createSession`, `DataStoreManager` stringSet API.)
- **Context gauge in chat top bar** — session title now `labelLarge` (compact); below it a thin progress bar + `85.1k/1.0M`-style readout from `session.info` payload `usage.context_used/context_max` (server already sends it — no server change). Turns red >80%. Populated on `session.resume` (`result.info`) and refreshed by `SessionInfo` notifications each turn. (`ChatScreen.kt`, `DashboardChatViewModel.parseContextUsage`, `ChatUiState.contextUsed/contextMax`.)
- **Appearance settings** — new section: presets (Classic cyan / **Terminal** — the desktop's phosphor-green look: accent `#00FF41` + background `#0A1A15` / Reset) + per-part swatch rows for Background, User bubbles, Assistant bubbles & top bars. `UiColorOverrides` plumbed through `HermexTheme` (background→surface, userBubble→primaryContainer, assistantBubble→surfaceVariant); null = derive from accent. (`SettingsRepository` keys, `Theme.kt`, `MainActivity` wiring, `SettingsScreen` + extracted `ColorSwatchRow`; Settings column now scrolls.)

### DONE in v0.1.48 (2026-08-12)
- **Accent visibility fix** — v0.1.47's scheme used 22% primaryContainer + untouched surfaceVariant → sliders/cursor recolored but chat chrome (assistant bubbles/top bars, which read surfaceVariant) didn't. Now: primaryContainer 40% (user bubbles), surfaceVariant 16% accent tint (assistant bubbles/top bars/composer), secondaryContainer 25%. All in `accentColorScheme()` (Theme.kt).

### DONE in v0.1.47 (2026-08-12)
- **Theme accent colors** — `HermexTheme(accentColor: Color?)` param; when set, `accentColorScheme()` builds a dark M3 scheme from the accent (containers = accent alpha-over the dark surfaces, on-colors via `isDarkForeground`). Picked accent overrides dynamic/wallpaper colors. `SettingsScreen` Theme section: 7-swatch row (System gradient + 6 solids) with tap-to-apply, persisted as hex in DataStore via `SettingsRepository.accentColorHex`.

### DONE in v0.1.46 (2026-08-12)
- **Display settings** — `SettingsRepository` (int percentages in "hermex_settings" DataStore) + `LocalDensity` override at the app root in `MainActivity`: UI zoom 80–130% (dp), text size 80–150% (sp, stacks on zoom). Sliders in `SettingsScreen` save on every change → live preview while dragging. No per-screen changes needed; the density override covers everything.

### DONE in v0.1.45 (2026-08-12)
- **StreamLoop auto-scroll fix (v0.1.44 regression):** the `snapshotFlow` block read `state.messages` / `content.length` / `toolCalls.size` off the captured `ChatUiState` instance — plain field reads, zero snapshot-state reads, so the flow emitted exactly once at stream start and never re-fired as deltas grew the bubble. Fixed by reading `viewModel.uiState` (the `MutableState` getter) inside both the block and the `collect`. Key now covers `thinkingText` growth; effect gated on message presence (not `isStreaming`) so a session resumed mid-response still tracks. (`ChatScreen.kt`, commit `44268b3`)

### DONE in v0.1.44 (2026-08-12)
- StreamLoop: 100ms poll → `snapshotFlow` + `distinctUntilChanged` keyed on (message count, content length, toolCalls size) — fixes same-message growth gap + kills no-op wake-ups during thinking (`ChatScreen.kt`).
- 4001 self-heal: `submitWithSelfHeal()` re-registers via `session.resume` and retries once when the dashboard reclaimed the session (`ws_orphan_reap` / idle / LRU) without client signal (`DashboardChatViewModel.kt`).
- Server: DB-key fallback restored in `_sess_nowait` (`tui_gateway/server.py` — **uncommitted local patch, clobbered by `hermes update`; re-verify**).
- Repo public (Obtainium pulls releases without PAT); signing done (keystore + CI secrets since 2026-07-17).
