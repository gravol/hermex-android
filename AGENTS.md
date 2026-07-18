# Hermex Android

Hermex Android is a native Kotlin/Compose chat client for the Hermes Agent AI assistant.

## Current state
- v0.1.36, repo `gravol/hermex-android` (PRIVATE)
- Dashboard at `http://100.80.204.66:9119` (REST + WebSocket)
- Full architecture in `PROJECT_HANDOFF_CURRENT.md`

## Next step
Phase 5E: **Clarify support** — Build a free-text input dialog UI for when the agent asks a question. Same pattern as the approval dialog.

## Key files
- `PROJECT_HANDOFF_CURRENT.md` — complete project state
- `app/src/main/java/com/hermex/android/feature/chat/DashboardChatViewModel.kt` — main ViewModel
- `app/src/main/java/com/hermex/android/feature/chat/ChatScreen.kt` — Compose UI
- `core/network/src/main/java/com/hermex/core/network/JsonRpcClient.kt` — RPC client
- `core/network/src/main/java/com/hermex/core/network/RpcNotification.kt` — event types
