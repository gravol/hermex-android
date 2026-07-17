# Hermex Android — Project Handoff (Current State)

**Last updated:** 2026-07-17 (Phase 4L)  
**Current version:** v0.1.23 (versionCode 23)  
**HEAD commit:** `dc56adc` — Phase 4L groundwork: session_key field + debug logging in sessionResume  
**Branch:** `master`  
**Repository:** `git@github.com:gravol/hermex-android.git`  
**Working directory:** `/home/jeff/HermexAndroid` (canonical)

---

## Verified Project Root

| Field | Value |
|---|---|
| Canonical path | `/home/jeff/HermexAndroid` |
| Remote URL | `git@github.com:gravol/hermex-android.git` |
| Branch | `master` |
| Latest commit | `dc56adc` — Phase 4L groundwork: session_key field |
| Build command | `./gradlew assembleRelease --no-configuration-cache` |
| APK output | `app/build/outputs/apk/release/app-release.apk` |
| Version | v0.1.23 (versionCode 23) |
| Completed phase | **Phase 4L — chat viewport & keyboard anchoring, session_key field** |
| Next phase | **Phase 4M — device verification of end-to-end send/stream** |

> **Stale copy: `/mnt/storage/projects/HermexPort`** — Different git history (7 commits, no remote, version 0.2.0). Abandoned early port that was never pushed. **Do not edit.** The canonical repo is `/home/jeff/HermexAndroid`.

---

## Project Overview

### What the application does
Hermex Android is a native Kotlin/Compose chat client for the **Hermes Agent** AI assistant. It connects to a self-hosted Hermes Dashboard (running on a Linux server at Tailscale IP `100.80.204.66`) to provide a mobile-first conversational interface with live token streaming, tool-call visualization, thinking/reasoning blocks, and session management.

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
- **Notification routing** — 17 event types parsed (`message.delta`, `thinking.delta`, `reasoning.delta`, `tool.started/progress/completed`, `approval.request`, `clarify.request`, `gateway.ready`, `run.started/completed`, `message.completed`, `session.info`, etc.)
- **Session list** — `SessionsViewModel.loadDashboardSessions()` via `session.list` RPC
- **Session resume** — `DashboardChatViewModel.loadMessages()` via `session.resume` RPC with **session ID normalization** (resolved live sid replaces DB key)
- **Chat streaming** — `DashboardChatViewModel` sends via `prompt.submit`, consumes notifications for real-time deltas
- **Auto-scroll** — `scrollGeneration` counter for instant scroll, `scrollToItem` (not animate)
- **Thinking/reasoning display** — collapsible blocks with delta concatenation
- **Tool call visualization** — started/progress/completed UI
- **Approval/clarify auto-deny** — v1 auto-denies with log notice
- **Release CI** — `.github/workflows/release.yml` auto-builds APK and creates GitHub Release on push to master
- **Debug logging** — in-app ring buffer (1000 entries), exportable from Settings

### What works (Legacy REST+SSE stack — fallback)
- Setup screen with server URL + API key entry
- Session list via `GET /api/sessions`
- Chat screen with SSE streaming
- Message bubbles, typing dots, keyboard auto-scroll, copy-to-clipboard

### What is NOT yet wired / known issues
- **Empty sessions in session.list** — `session.list` may return sessions with zero messages or no content. Need server-side filtering or client-side display filtering.
- **Need device verification of send/stream flow** — Phase 4I/4J streaming notification parsing and hardening are protocol-correct per code analysis but remain untested against real server traffic. Code review confirms: `isWaitingForFirstEvent` clears on all delta types, `isStreaming` filter prevents cross-message pollution, thinking/reasoning toggle lifecycle is correct.
- **Legacy REST/SSE stack cleanup deferred** — `ApiClient.kt`, `DTOs.kt`, `SseParser.kt`, old `SetupViewModel`, `SetupScreen`, old `ChatViewModel` still present. Deferred until new stack is proven end-to-end.
- **Auto-deny for approval/clarify** — v1 expedient. `ApprovalRequest` and `ClarifyRequest` are auto-denied by `JsonRpcClient`. Real approval UI needed before removing auto-deny.
- **No background WebSocket keepalive** — `HermesForegroundService` exists in `core/ui` but is not wired. Phone lock may disconnect WebSocket.
- **Phone lock kills SSE stream** — legacy stack issue. New WS stack will need background service integration.
- **Feature modules contain dead code** — `feature/chat/`, `feature/session/`, `feature/skills/`, etc. contain auto-generated `Component_*.kt` stubs. Not included in `settings.gradle.kts` — do not compile. Safe to delete.
- **Debug APK is large** — 63MB debug build vs 28MB release (ProGuard + R8). Expected.
- **Signed with debug key** — Release builds use `signingConfigs.getByName("debug")`. Need real keystore before production distribution.

---

## Dashboard JSON-RPC/WebSocket Architecture

### Auth chain
```
1. POST /auth/password-login {"provider":"basic","username":"jeff","password":"***"}
   → session cookies (hermes_session_at 12h, hermes_session_rt 30d) stored in CookiePersistor

2. POST /api/auth/ws-ticket (cookie-authenticated)
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
| `session.resume` | Load session history (returns resolved live sid) |
| `prompt.submit` | Send message and start streaming response |
| `session.interrupt` | Stop current streaming turn |
| `approval.respond` | Respond to tool approval requests (v1: auto-deny) |
| `clarify.respond` | Respond to clarification requests (v1: auto-deny) |

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
- Default URL: `https://100.80.204.66:8443`

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
- Verified: thinking dropdown remains functional and collapsible after message completion

### Phase 4L — Chat Viewport & Keyboard Anchoring, Session Key Field (v0.1.23)

- **Replaced `scrollGeneration`-keyed LaunchedEffect with polling loop** — continuous 50ms polling while `isStreaming` ensures auto-scroll keeps up with rapid content growth without being cancelled by SSE event restarts
- **Frame-based keyboard settle** — replaced fixed 500ms delay with `withFrameNanos` × 3 to wait for IME-driven layout pass
- **Session ID lifecycle hardening** — split `sessionId` (DB key) from `liveSid` (transient RPC sid); `session_key` field added to `SessionResumeResult` with debug logging of match/mismatch
- **Debug logging overhaul** — all session ID logs use `dbKey=$sessionId liveSid=$liveSid` format for clarity
- **Handoff doc updated** to reflect Phase 4L completion
- Version bumped to v0.1.23

### Phase 4K — Chat Viewport Stabilization (v0.1.21)
- **Extracted `autoScrollToBottom()` helper** — unified two-step scroll: `scrollToItem(targetIndex)` then `scrollBy(remaining)` computed from `visibleItemsInfo` offset+size vs viewport height
- **Fixed streaming auto-scroll** — when a streaming bubble grows taller than the viewport, the compensation step scrolls the remaining distance so the newest content's bottom is visible
- **Fixed keyboard open scroll** — after IME opens, the same compensation ensures the last message's bottom is above the keyboard
- **All 4 scroll sites unified** — SessionOpen, AutoScroll (per-delta), StreamEnd, and Keyboard all use the same helper
- **Comprehensive debug logging** — every scroll event logs `firstVisibleItemIndex`, `lastVisibleItemIndex`, `canScrollForward`, `viewportSize.height`, and actual item bottom offset during compensation
- IME open/close events log viewport height before and after
- Build verified: `assembleRelease` compiles successfully
- Version bumped to v0.1.21
- **Bugfix: `isWaitingForFirstEvent` never cleared by `thinking.delta`/`reasoning.delta`** — if the server's first event was a thinking delta (model thinking before responding), `TypingDots` displayed indefinitely because `isWaitingForFirstEvent` stayed `true`. Fixed by ensuring all delta handlers clear the flag.
- **Bugfix: `MessageStarted` didn't clear `isWaitingForFirstEvent` when `messageId` was null** — if `message.start` arrived without a server message ID, the placeholder never transitioned from TypingDots. Fixed by always clearing the flag regardless of messageId.
- **Hardening: all delta handlers now filter by `isStreaming`** — `indexOfLast { it.role == "assistant" && it.isStreaming }` prevents content from being applied to a completed non-streaming assistant message when multiple assistant messages exist in the list.
- Verified: `reasoning.available` correctly triggers Thinking toggle, reasoning content remains collapsible
- Verified: tool events render as `ToolCallCard` components, do not leak into chat bubble text
- Verified: `Unknown` events are logged with full params, never modify UI state
- Verified: session ID normalization (DB key → live sid) propagates correctly through `prompt.submit`
- Build verified: `assembleRelease` compiles successfully
- Version bumped to v0.1.20

---

## Session ID Bug — Explanation and Fix

### Problem
When a user taps a session in the session list, a DB session key (e.g., `1741812014_a1b2c3`) is passed to the chat screen. `session.resume` is called with this key, and the server returns a live session ID (e.g., `abc12345`). However, subsequent calls like `prompt.submit` were still using the original DB key, causing a 4001 "session not found" error because the DB key is not a valid runtime session ID.

### Root cause
The server stores sessions indexed by DB keys. When a prompt submission arrives with a DB key instead of a live runtime session ID, the server cannot find the session in its runtime agent registry. The `session.resume` method returns the resolved live sid, but until Phase 4G, the Android client never used it.

### Fix
1. **Server side** (server.py changes in Phase 4G):
   - `_sess_nowait` returns a 3-tuple: (session, resolved_sid, err)
   - `_find_live_session_by_key` resolves DB keys to live runtime sids
   - `_sess()` passes resolved sid to `_start_agent_build`
   - `prompt.submit` handler reassigns `sid = sid_resolved` before all downstream operations

2. **Android side** (`DashboardChatViewModel.kt`):
   - After `session.resume`, if the returned `session_id` differs from what was sent, `sessionId` is reassigned to the resolved live sid
   - All subsequent operations (prompt.submit, session.interrupt) then use the canonical live sid
   - Debug logging reports normalization with "normalizing sessionId from X to Y"

### Verification
The fix is protocol-correct and regression-tested on the server side, but has **not been verified on-device**. Device testing should:
1. Open an existing session from the session list
2. Verify `session.resume` succeeds with the DB key
3. Verify the session ID is normalized to the live sid
4. Send a message and verify `prompt.submit` succeeds (no 4001)
5. Verify streaming deltas arrive
6. Verify `message.completed` finalizes the message

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
# 3. Tag and push
git tag -a v0.1.X -m "v0.1.X — description"
git push origin master && git push origin v0.1.X
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
| `app/.../DashboardChatViewModel.kt` | 406 | Dashboard WS chat: connect, resume, send, notification handler |
| `app/.../ChatScreen.kt` | 655 | Compose UI: LazyColumn, bubbles, typing dots, thinking, auto-scroll |
| `app/.../DashboardSetupScreen.kt` | 150 | URL + password entry screen |
| `app/.../DashboardSetupViewModel.kt` | 119 | status() → login() flow for dashboard setup |
| `app/.../SessionsScreen.kt` | 165 | Session list UI |
| `app/.../SessionsViewModel.kt` | 153 | Session list loading (dashboard via RPC, legacy fallback) |
| `app/.../ChatViewModel.kt` | 371 | Legacy SSE chat ViewModel (will be removed) |
| `app/.../SetupScreen.kt` | — | Legacy setup screen (will be removed) |
| `app/.../SetupViewModel.kt` | — | Legacy setup ViewModel (will be removed) |
| `core/network/DashboardApiClient.kt` | 253 | REST client: login, ws-ticket, status, 401 auto-relogin |
| `core/network/WsConnectionManager.kt` | 215 | WebSocket lifecycle, reconnect, Frame Channel |
| `core/network/JsonRpcClient.kt` | 444 | JSON-RPC 2.0: request/response, notifications, v1 methods |
| `core/network/RpcNotification.kt` | 143 | Sealed class for 17 event types |
| `core/network/ApiClient.kt` | — | Legacy REST+SSE client (will be removed) |
| `core/network/CookiePersistor.kt` | — | Encrypted cookie storage |
| `core/network/NetworkCookieJar.kt` | — | OkHttp CookieJar backed by CookiePersistor |
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

1. **`mutableStateOf`, not `StateFlow`** — `StateFlow` conflates intermediate values. During rapid streaming events (10-50ms deltas), snapshot state writes directly to the next composition frame. Proven correct through testing.

2. **`scrollToItem`, not `animateScrollToItem`** — Spring animation (~200-300ms) is constantly cancelled and restarted by rapid deltas, causing viewport drift. Instant scroll completes in one frame.

3. **`scrollGeneration` counter, not field-keyed LaunchedEffect** — Monotonic counter bumped on every list mutation ensures ANY future event type gets auto-scroll for free. Keying on specific fields breaks when new event types are added.

4. **Fresh ticket every reconnect** — WebSocket ticket is single-use with 30s TTL. Never cached or reused. Every reconnect cycle fetches a fresh ticket.

5. **Auto-deny for approval/clarify in v1** — Unhandled approval/clarify requests cause hung turns. Auto-deny via `notify()`, then emit notification to UI. Remove only when real approval UI is built.

6. **Coexist old and new networking stacks** — Do NOT delete `ApiClient.kt` or related files until the new JSON-RPC/WS stack is proven end-to-end on device.

7. **Delta = append-only concatenation** — No sequence number field exists. Order guaranteed by WebSocket stream order. Client concatenates `content = content + delta.text`.

---

## Development Environment

### Server
- Hermes Dashboard at `https://100.80.204.66:8443` (TLS, self-signed cert, Tailscale IP)
- WebSocket at `ws://100.80.204.66:9119/api/ws?ticket=...` (plain WS, inside Tailscale)
- Legacy API at `http://100.80.204.66:8650` (plain HTTP, Hermes API Server)

### Device
- GrapheneOS, Tailscale-connected
- Obtaimium for updates (point to GitHub Releases)

### Required secrets
- Dashboard password (stored in `KeychainStore` as `dashboard_password`)
- Hermes API Server bearer key (stored as `api_key` — legacy)
- Obtaimium needs GitHub PAT with `repo` scope for private repo access

---

## Next Steps (After Device Verification)

1. **Device-verify Phase 4K fix** — Install v0.1.21 APK, test streaming auto-scroll (100-word, 500+-word, thinking-enabled, keyboard-during-streaming), verify no content hidden below viewport
2. **Legacy stack cleanup** — Remove `ApiClient.kt`, `DTOs.kt`, `SseParser.kt`, old `SetupViewModel`, `SetupScreen`, `SessionsViewModel`, `ChatViewModel`
3. **Feature module cleanup** — Delete stub files in `feature/` directories not included in build
4. **Background WebSocket** — Wire `HermesForegroundService` to keep WebSocket alive when phone locks
5. **Approval/clarify UI** — Replace auto-deny with real approval dialog UI
6. **Production signing** — Wire proper keystore for release builds
7. **Make repo public** — So Obtainium can see GitHub Releases and auto-update
