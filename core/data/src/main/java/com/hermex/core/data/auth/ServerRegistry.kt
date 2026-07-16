package com.hermex.core.data.auth

interface ServerRegistry {
    fun getServers(): List<ServerAccount>
    fun activate(serverUrl: String)
}

class CustomHeaderStore {
    companion object {
        val shared = CustomHeaderStore()
    }
}
