package com.hermes.chat.model

enum class ModelType(
    val displayName: String,
    val apiName: String,
) {
    FLASH("Flash", "deepseek-v4-flash"),
    PRO("Pro", "deepseek-v4-pro"),
}
