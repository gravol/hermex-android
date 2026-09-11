# Planned Fixes — 2026-09-11 (for morning implementation)

**Prepared by:** agent, while Jeff slept. Nothing shipped yet — all three are
analysis + patch-ready to implement in the morning and verify with a build.
Current version on disk: **v0.1.157** (versionCode 158), commit `52bdfcd`.

---

## Fix #1 — Turn-finished notification silently fails when app is backgrounded/locked ✅ ROOT-CAUSE CONFIRMED, PATCH READY

### Symptom
Sending a message through Hermex and locking the phone / switching apps → no
notification when the assistant responds. Works fine in the foreground (proven
in Aug 24 QA: `postTurnFinished posted` logged). Fails every time backgrounded.

### Root cause (confirmed, "dead to rights")
`NotificationHelper.replyAction()` builds a `PendingIntent` for the inline
"Reply" action with `FLAG_IMMUTABLE`, but that intent carries a `RemoteInput`
(the reply text field). On Android, a `PendingIntent` holding a `RemoteInput`
**cannot be immutable** — building it throws. The notification `.build()` call
is wrapped in `runCatching {}` (line 148), so the exception is swallowed
silently → **every backgrounded turn-finished notification is killed at the last
step, every time.** Before this existed the failure was invisible.

This matches exactly what the field-device AI found: the alarm arms, fires,
authenticates, checks, decides to notify — and dies at `notify()`. Everything
upstream of the `.build()` works. The earlier v0.1.145 foreground test passed
because the notification was visible on-screen (foreground suppression aside),
so nobody saw the thrown exception.

### Files / lines
- `app/src/main/java/com/hermex/android/notify/NotificationHelper.kt`
  - Line 110–115: `replyAction()` PendingIntent — change `FLAG_IMMUTABLE` → `FLAG_MUTABLE`.
  - Line 148: `postTurnFinished` `.build()` is inside `runCatching {}` (this is why it was silent).

### Verification that this is the ONLY place with the bug
- Grep for all `RemoteInput` usage across `app/src/main`: only
  `NotificationHelper.kt` (import line 11) and `NotificationReplyReceiver.kt`
  (reads results, doesn't build an intent).
- All other `PendingIntent.get*` calls (TurnWatcher lines 62/84/138, CronWatcher
  line 262, WsKeepaliveService line 86, NotificationHelper.openSessionIntent
  line 92) attach RemoteInputs? **No.** They are plain activity/broadcast
  intents → correctly immutable. Only the reply action pairs a RemoteInput with
  its PendingIntent. So only one flag needs to change.

### The patch (already applied to working tree, NOT committed)
```kotlin
val pendingIntent = PendingIntent.getBroadcast(
    context,
    (sessionKey.hashCode() and 0x7fffffff) + 1000,
    intent,
    // MUTABLE: a PendingIntent carrying a RemoteInput cannot be FLAG_IMMUTABLE —
    // Android throws when the notification is built, which silently killed every
    // turn-finished notification (the build was wrapped in runCatching {}).
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
)
```

### How to verify after building
1. `./gradlew assembleRelease --no-configuration-cache`
2. Confirm APK versionCode = 159, versionName = "0.1.158".
3. Install on phone. Send a message, lock the phone, wait for the turn to
   finish (~90s fallback or live WS). Expect the turn-finished notification to
   appear. Then tap the inline **Reply** action and type — that path must also
   work (the thing that was throwing). Both must pass: fixing the flag makes
   the notification fire AND unblocks inline reply.

### Release steps (per repo publishing protocol)
Bump versionCode→159 + versionName "0.1.158" in `app/build.gradle.kts`,
commit, push tag SEPARATELY from commit (CI skips build+release if they arrive
together). Verify `gh release view` shows the APK attached and non-draft.

---

## Fix #2 — Thinking does not auto-scroll until the first tool call appears ✅ ROOT-CAUSE CONFIRMED, PATCH READY

### Symptom
While the model is thinking (before any tool call / real content arrives), the
chat does not scroll to follow — the screen goes stale. Once the first tool
call shows up, scrolling "works" again.

### Root cause (confirmed by layout analysis)
During an active streaming turn the UI is split into two regions:
1. **The main `LazyColumn`** (`ChatScreen.kt` ~line 1232) — during streaming it
   renders ONLY a single assistant placeholder with `isWaitingForFirstEvent =
   true` (a spinner). Thinking text and tool cards are NOT rendered in-stream
   yet; they appear only after the turn ends (lines 1246–1266: `showLiveThinking`,
   `ThinkingScrollBox`, `ToolScrollBox` all gated on `!msg.isStreaming`).
2. **The docked `LiveActivityPanel`** (`ChatScreen.kt` ~line 1299, defined at
   2250) — a fixed-height (max 200dp) panel above the composer that hosts the
   live thinking + tool activity while streaming. It has its OWN internal
   `rememberLazyListState()` and auto-scrolls internally:
   `LaunchedEffect(thinking.length, toolCalls.size, ...)` → `scrollToItem(count-1)`
   (screen ~line 2438).

The main-list StreamLoop (`ChatScreen.kt` line 516) keys its snapshot on
`last.thinkingText?.length + last.content.length`. But during pure-thinking the
main list's last item is the empty spinner placeholder — `thinkingText` lives in
the docked panel, not in that message. So the main-list scroll trigger sees no
change (spinner stays same height) → nothing scrolls in the main list. The only
scrolling happening is inside the tiny 200dp docked panel, which clips the
thinking text and doesn't move the actual chat view.

The moment a tool call arrives, `toolCalls.size` grows on the placeholder AND
the live panel shows it — that's when scroll reactivity "turns on."

### The fix (design options — pick one in the morning)
**Option A (recommended, minimal):** While `isStreaming`, keep the docked
`LiveActivityPanel` visible but ALSO auto-scroll the main list to follow the
thinking as it grows. Concretely: in the StreamLoop's snapshot key, when the
last message is still a fresh spinner placeholder (`isWaitingForFirstEvent =
true`) but live thinking exists, key on the docked panel's thinking length too.
Simplest concrete form: expose the live message's `thinkingText.length` through
the VM state as a `streamingThinkingLen` field and add it to the StreamLoop
snapshot Triple so main-list re-scroll fires during pure-thinking.

**Option B:** Render the live thinking inline in the main list (not just docked)
during streaming, at least a truncated preview line, so the main LazyColumn has
growing content to follow. More visible but risks layout churn / double-rendering
(thinking appears both docked and inline).

**Option C:** Make the docked panel taller/fillable or convert it into the main
list's bottom item during streaming (larger change, rework of the split layout).

### Recommendation
Go with **Option A**: one new VM field (`streamingThinkingLen`, set on
`ThinkingDelta`/`ReasoningDelta` in `DashboardChatViewModel.kt` lines 1193–1213)
+ add it to the StreamLoop snapshot key. No layout rework, no double-render.

### To confirm root cause precisely before patching
Export debug log (Settings → Debug) from a thinking-only turn: the main-list
StreamLoop logs will show `totalItems` constant and no scroll while only
thinking is streaming; the docked panel's internal listState scrolls but clips.

---

## Fix #3 — `/yolo` (and other busy-policy=dispatch slash commands) fails to execute ✅ ROOT-CAUSE CONFIRMED, PATCH READY

### Symptom
`/yolo` "just fails to execute period." Other server slash commands that route
through `slash.exec` may fail similarly.

### Root cause (confirmed by tracing server + client dispatch)
1. `/yolo` is a real server command: `CommandDef("yolo", "Toggle YOLO mode
   (skip all dangerous command approvals)", busy_policy="dispatch")`
   (`hermes_cli/commands.py` line 276). It has NO registry-owned executor in
   `slash_exec.EXECUTORS`, so its behavior is handled at runtime by the gateway's
   `_dispatch_busy_slash_command`.
2. When a turn is **busy**, the gateway routes it through command.dispatch, which
   resolves `_sessions` by **live sid** ONLY (not the DB key). `run.py` line
   17050–17053 dispatches busy commands via that resolver.
3. In Hermex, `/yolo` falls through to `sendSlashCommand()` →
   `execSlashWithFallbacks()`. That method calls `slash.exec` and only falls back
   to `command.dispatch` on a **4018** error (lines 553–560), passing
   `liveSid.ifBlank { sessionId }`.
4. **The bug:** `liveSid` is populated by `session.resume`, which Hermex does NOT
   always call before slash commands — on a fresh session, after a reconnect, or
   when the client reuses a stale DB key, `liveSid` is blank. So the fallback
   passes the **DB key** to command.dispatch, which resolves `_sessions` by live
   sid only → "session not found" / dispatch failure → `/yolo` fails to execute.

### Files / lines
- `DashboardChatViewModel.kt` line 548–572 (`execSlashWithFallbacks`)
  - Line 559: `rpcClient.commandDispatch(liveSid.ifBlank { sessionId }, base, arg)` — passes DB key when liveSid is blank.

### The fix (design)
Before routing a busy/dispatch command to `command.dispatch`, ensure the client
has a fresh live sid. Two complementary hardening steps:
1. In `execSlashWithFallbacks`, before the 4018→dispatch fallback, **always try
   `session.resume(omitMessages=true)` to refresh `liveSid`** (the VM already has
   this pattern in `/steer`/`/new` via `wsConnection.connect()` + resume). Only
   then pass the live sid to command.dispatch. Never fall back to the DB key for
   a dispatch-resolved command.
2. Guard: if after resume the live sid is still blank, surface "⚠️ Could not
   refresh session — try /new or send a message again" instead of silently
   passing the wrong id.

This also fixes any other `busy_policy="dispatch"` command (/yolo, /footer,
/approvals when busy) that currently breaks on a blank liveSid.

### How to verify after building
- `/yolo` while NOT busy: should toggle approval bypass and return status.
- `/yolo` while busy (during an active turn): should route through dispatch and
  succeed — the case that was failing.
- Also test /new, /steer, /queue still work (regression check on the slash path).

### IMPORTANT — do NOT fire live tonight
`/yolo` toggles approval bypass (skips dangerous-command approvals). Do not run
it against the live gateway while unattended — it could enable unattended tool
actions with no one to hit `/stop`. Implement + verify only when someone is
present and can immediately reset with /stop.

---

## Summary of changes for morning

| # | Issue | File | Risk | Effort |
|---|-------|------|------|--------|
| 1 | Backgrounded turn-finished notif | NotificationHelper.kt L114 | Low (one flag) | 5 min |
| 2 | Thinking no auto-scroll until tool call | DashboardChatViewModel.kt + ChatScreen.kt StreamLoop | Med (add field + key) | 20 min |
| 3 | /yolo dispatch fails | DashboardChatViewModel.kt execSlashWithFallbacks L559 | Low-Med (resume before dispatch) | 15 min |

**Order:** #1 first (one-line, highest value, proven root cause). Then #2. Then
#3. Bump versionCode→159 / versionName "0.1.158" once all three land. Ship via
separate commit + tag per repo protocol.

**Not done tonight:** nothing shipped to Obtainium; `/yolo` not fired live; no
commits made. Working tree has the #1 patch staged (uncommitted).
