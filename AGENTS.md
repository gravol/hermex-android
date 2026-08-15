# Hermex Android

Hermex Android is a native Kotlin/Compose chat client for the Hermes Agent AI assistant.

## Current state
- v0.1.92, repo `gravol/hermex-android` (PUBLIC since 2026-08-12)
- Dashboard at `http://100.80.204.66:9119` (REST + WebSocket)
- Full architecture in `PROJECT_HANDOFF_CURRENT.md`

## Next step
**None pending** — mid-turn slash commands shipped in v0.1.92 (`/stop` `/interrupt` `/steer` `/queue`; Stop button removed, Send always available, queued turns render). Next up is whatever surfaces from device QA.

## Key files
- `PROJECT_HANDOFF_CURRENT.md` — complete project state
- `app/src/main/java/com/hermex/android/feature/chat/DashboardChatViewModel.kt` — main ViewModel
- `app/src/main/java/com/hermex/android/feature/chat/ChatScreen.kt` — Compose UI
- `core/network/src/main/java/com/hermex/core/network/JsonRpcClient.kt` — RPC client
- `core/network/src/main/java/com/hermex/core/network/RpcNotification.kt` — event types
