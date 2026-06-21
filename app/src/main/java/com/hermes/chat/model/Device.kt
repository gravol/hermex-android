package com.hermes.chat.model

enum class DeviceState {
    AWAKE,
    OFF,
    UNKNOWN,
}

data class Device(
    val name: String,
    val macAddress: String,
    val ipAddress: String,
)
