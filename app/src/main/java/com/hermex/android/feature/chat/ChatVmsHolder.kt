package com.hermex.android.feature.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.hermex.android.service.WsKeepaliveService

/**
 * Activity-scoped holder for per-session chat ViewModels.
 *
 * Chat ViewModels are normally scoped to the nav back-stack entry, which means
 * backing out of a chat destroys the ViewModel → WebSocket disconnect → the
 * server's orphan-reaper tears the session down after a 20s grace window and
 * kills the running turn ("I left the chat and it stopped doing what I asked").
 *
 * Keeping one VM per session at Activity scope means the WebSocket stays
 * connected in the background: turns keep running after you leave the chat,
 * and reopening the session shows the live (possibly already-completed) state.
 * All VMs are disposed when the Activity finishes (holder cleared).
 */
class ChatVmsHolder(application: Application) : AndroidViewModel(application) {

    private val vms = mutableMapOf<String, DashboardChatViewModel>()

    /** Get (or create) the chat ViewModel for a session. One WS per session. */
    fun getOrCreate(sessionId: String): DashboardChatViewModel =
        vms.getOrPut(sessionId) { DashboardChatViewModel(getApplication()) }

    /** Dispose all held chat ViewModels and stop the keepalive service. */
    fun disposeAll() {
        vms.values.forEach { it.dispose() }
        vms.clear()
        WsKeepaliveService.stop(getApplication())
    }

    override fun onCleared() {
        disposeAll()
        super.onCleared()
    }
}
