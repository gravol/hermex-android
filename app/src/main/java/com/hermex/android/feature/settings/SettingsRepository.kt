package com.hermex.android.feature.settings

import android.content.Context
import com.hermex.android.di.AppModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persisted display preferences (UI zoom + text scale), stored as integer
 * percentages in the existing "hermex_settings" DataStore (via DataStoreManager).
 *
 * - uiZoomPercent scales the whole UI (dp) — e.g. 120 = 1.2× zoom
 * - textScalePercent scales text (sp) on top of zoom — e.g. 110 = 1.1× text
 * Both default to 100 (= unchanged). Applied at the app root in MainActivity
 * via a CompositionLocalProvider(LocalDensity) override.
 */
class SettingsRepository(context: Context) {

    private val store = AppModule.provideDataStoreManager(context.applicationContext)

    val uiZoomPercent: Flow<Int> = store.getInt(KEY_UI_ZOOM).map { it ?: 100 }

    val textScalePercent: Flow<Int> = store.getInt(KEY_TEXT_SCALE).map { it ?: 100 }

    /** Accent color as #RRGGBB, or null/blank = system (follow wallpaper / dynamic). */
    val accentColorHex: Flow<String?> = store.getString(KEY_ACCENT_COLOR)
        .map { it?.takeIf { s -> s.isNotBlank() } }

    suspend fun setUiZoomPercent(value: Int) = store.setInt(KEY_UI_ZOOM, value)

    suspend fun setTextScalePercent(value: Int) = store.setInt(KEY_TEXT_SCALE, value)

    suspend fun setAccentColorHex(value: String?) = store.setString(KEY_ACCENT_COLOR, value.orEmpty())

    private companion object {
        const val KEY_UI_ZOOM = "ui_zoom_percent"
        const val KEY_TEXT_SCALE = "text_scale_percent"
        const val KEY_ACCENT_COLOR = "accent_color_hex"
    }
}
