# Hermex Android

Hermex Android is a native Kotlin/Compose chat client for the Hermes Agent AI assistant.

## Current state
- v0.1.46, repo `gravol/hermex-android` (PUBLIC since 2026-08-12)
- Dashboard at `http://100.80.204.66:9119` (REST + WebSocket)
- Full architecture in `PROJECT_HANDOFF_CURRENT.md`

## Next step
**None pending** — StreamLoop is now state-change driven (snapshotFlow on `viewModel.uiState`, v0.1.44→v0.1.45) and the 4001 self-heal is live. Next up is whatever surfaces from device QA.

## Key files
- `PROJECT_HANDOFF_CURRENT.md` — complete project state
- `app/src/main/java/com/hermex/android/feature/chat/DashboardChatViewModel.kt` — main ViewModel
- `app/src/main/java/com/hermex/android/feature/chat/ChatScreen.kt` — Compose UI
- `core/network/src/main/java/com/hermex/core/network/JsonRpcClient.kt` — RPC client
- `core/network/src/main/java/com/hermex/core/network/RpcNotification.kt` — event types
