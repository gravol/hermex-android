# Hermex Android

Hermex Android is a native Kotlin/Compose chat client for the Hermes Agent AI assistant.

## Current state
- v0.1.115, repo `gravol/hermex-android` (PUBLIC since 2026-08-12)
- Dashboard at `http://100.80.204.66:9119` (REST + WebSocket)
- Full architecture in `PROJECT_HANDOFF_CURRENT.md`

## Next step
**None pending** — v0.1.115: improved command approval dialog with explicit labels and JSON args parsing for any tool type.

## Key files
- `PROJECT_HANDOFF_CURRENT.md` — complete project state
- `app/src/main/java/com/hermex/android/feature/chat/DashboardChatViewModel.kt` — main ViewModel
- `app/src/main/java/com/hermex/android/feature/chat/ChatScreen.kt` — Compose UI
- `core/network/src/main/java/com/hermex/core/network/JsonRpcClient.kt` — RPC client
- `core/network/src/main/java/com/hermex/core/network/RpcNotification.kt` — event types

## File path rules (CRITICAL — read before touching files)
- Kotlin/Java package declarations map to **real filesystem directories**: every `.` in the package name is a separate directory.
- `package com.hermex.core.network` → path `com/hermex/core/network/` (NOT `com.hermex.core.network/`).
- NEVER write a package name as a single folder segment (e.g. `com/hermex.core.network/` is wrong).
- `src/main` uses a **slash**, never a dot: it is `app/src/main/...`, NOT `app/src.main/...`.
- ALWAYS verify a file path exists before reading/editing: run `ls <path>` or `find /home/jeff/HermexAndroid -name '<File>.kt'`.
- NEVER reconstruct a path from memory — if unsure, `find /home/jeff/HermexAndroid -name '<File>.kt'` to get the exact path, then use it verbatim.
- Root of the repo is `/home/jeff/HermexAndroid`. Use paths relative to this root.

## dsh tool return contract (CRITICAL — read before writing JS)
- dsh `tools.bash()` and `tools.read()` return results to the model as **plain text strings**, NOT structured JS objects.
- Do NOT do `const { stdout } = await tools.bash(...); stdout.stdout.text` — there is no `.stdout.text` nested path.
- To inspect a bash/read result, treat the returned value as a string: `const out = await tools.bash({command:"pwd"}); console.log(String(out));` or just `console.log(out)`.
- If you need structured access, parse a string you control: `JSON.parse(String(result))`.
- Never assume a `.stdout.text` / `.stderr` / `{stdout:{...}}` shape — dsh gives you the rendered text directly.
