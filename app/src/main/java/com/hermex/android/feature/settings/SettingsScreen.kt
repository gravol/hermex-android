package com.hermex.android.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.hermex.android.ui.theme.hexToColor
import com.hermex.android.ui.theme.isDarkForeground
import com.hermex.core.network.DashboardApiClient
import com.hermex.core.network.DebugLog
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("App Info", style = MaterialTheme.typography.titleSmall)
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            Text("Version: ${pkgInfo.versionName} (${pkgInfo.longVersionCode})", style = MaterialTheme.typography.bodyMedium)
            Text("Server: ${DashboardApiClient.baseUrl()}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Device: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                style = MaterialTheme.typography.bodyMedium,
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Display", style = MaterialTheme.typography.titleSmall)
            Text(
                "Applies live to every screen. Text size stacks on top of UI zoom.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val settingsRepo = remember { SettingsRepository(context) }
            val savedZoom by settingsRepo.uiZoomPercent.collectAsState(initial = 100)
            val savedText by settingsRepo.textScalePercent.collectAsState(initial = 100)
            var zoomPercent by remember { mutableStateOf(savedZoom) }
            var textPercent by remember { mutableStateOf(savedText) }
            // Keep local slider state in sync if the persisted value changes externally
            LaunchedEffect(savedZoom) { zoomPercent = savedZoom }
            LaunchedEffect(savedText) { textPercent = savedText }

            Text("UI zoom: $zoomPercent%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = zoomPercent.toFloat(),
                onValueChange = { v ->
                    zoomPercent = v.roundToInt()
                    scope.launch { settingsRepo.setUiZoomPercent(zoomPercent) }
                },
                valueRange = 80f..130f,
            )

            Text("Text size: $textPercent%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = textPercent.toFloat(),
                onValueChange = { v ->
                    textPercent = v.roundToInt()
                    scope.launch { settingsRepo.setTextScalePercent(textPercent) }
                },
                valueRange = 80f..150f,
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Theme", style = MaterialTheme.typography.titleSmall)
            Text(
                "Accent color for bubbles, buttons and highlights. System follows your wallpaper.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val savedAccent by settingsRepo.accentColorHex.collectAsState(initial = null)
            ColorSwatchRow(
                options = accentOptions,
                selectedHex = savedAccent,
                onSelect = { hex -> scope.launch { settingsRepo.setAccentColorHex(hex) } },
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Appearance", style = MaterialTheme.typography.titleSmall)
            Text(
                "Tweak individual UI parts, or apply a full preset. Assistant bubbles and top bars share one color.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val savedBg by settingsRepo.uiBackgroundHex.collectAsState(initial = null)
            val savedUserBubble by settingsRepo.uiUserBubbleHex.collectAsState(initial = null)
            val savedAssistantBubble by settingsRepo.uiAssistantBubbleHex.collectAsState(initial = null)

            // Presets — apply accent + per-part overrides at once
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = savedAccent.equals("#00D1FF", ignoreCase = true) &&
                        savedBg.isNullOrBlank() &&
                        savedUserBubble.isNullOrBlank() &&
                        savedAssistantBubble.isNullOrBlank(),
                    onClick = {
                        scope.launch { settingsRepo.applyAppearance("#00D1FF", null, null, null) }
                    },
                    label = { Text("Classic") },
                )
                FilterChip(
                    selected = savedAccent.equals("#00FF41", ignoreCase = true) &&
                        savedBg.equals("#0A0C0A", ignoreCase = true),
                    onClick = {
                        scope.launch { settingsRepo.applyAppearance("#00FF41", "#0A0C0A", null, null) }
                    },
                    label = { Text("Terminal") },
                )
                FilterChip(
                    selected = savedAccent.isNullOrBlank() &&
                        savedBg.isNullOrBlank() &&
                        savedUserBubble.isNullOrBlank() &&
                        savedAssistantBubble.isNullOrBlank(),
                    onClick = {
                        scope.launch { settingsRepo.applyAppearance(null, null, null, null) }
                    },
                    label = { Text("Reset") },
                )
            }

            Spacer(Modifier.height(8.dp))

            Text("Background", style = MaterialTheme.typography.bodyMedium)
            ColorSwatchRow(
                options = accentOptions,
                selectedHex = savedBg,
                onSelect = { hex -> scope.launch { settingsRepo.setUiBackgroundHex(hex) } },
            )

            Spacer(Modifier.height(4.dp))

            Text("User bubbles", style = MaterialTheme.typography.bodyMedium)
            ColorSwatchRow(
                options = accentOptions,
                selectedHex = savedUserBubble,
                onSelect = { hex -> scope.launch { settingsRepo.setUiUserBubbleHex(hex) } },
            )

            Spacer(Modifier.height(4.dp))

            Text("Assistant bubbles & top bars", style = MaterialTheme.typography.bodyMedium)
            ColorSwatchRow(
                options = accentOptions,
                selectedHex = savedAssistantBubble,
                onSelect = { hex -> scope.launch { settingsRepo.setUiAssistantBubbleHex(hex) } },
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Debug Log", style = MaterialTheme.typography.titleSmall)
            Text(
                "${DebugLog.entryCount()} entries in buffer",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    scope.launch {
                        exportAndShare(context)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export Debug Log")
            }

            OutlinedButton(
                onClick = {
                    copyToClipboard(context)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Copy Debug Log")
            }
        }
    }
}

private val accentOptions = listOf(
    "System" to null,
    "Cyan" to "#00D1FF",
    "Blue" to "#5B7CFF",
    "Purple" to "#AF52DE",
    "Green" to "#34C759",
    "Red" to "#FF3B30",
    "Orange" to "#FF9500",
)

/**
 * Horizontal row of circular color swatches (System gradient + solid palette).
 * Selection shown with a colored border + ✓ check.
 */
@Composable
private fun ColorSwatchRow(
    options: List<Pair<String, String?>>,
    selectedHex: String?,
    onSelect: (String?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (_, hex) ->
            val selected = if (hex == null) {
                selectedHex.isNullOrBlank()
            } else {
                selectedHex.equals(hex, ignoreCase = true)
            }
            val swatchColor = hex?.let { hexToColor(it) }
            val swatchBackground = swatchColor?.let { Modifier.background(it) }
                ?: Modifier.background(
                    Brush.linearGradient(
                        listOf(Color(0xFF00D1FF), Color(0xFFAF52DE), Color(0xFFFF3B30)),
                    ),
                )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .then(swatchBackground)
                    .border(
                        width = 2.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (swatchColor != null && isDarkForeground(swatchColor)) {
                            Color.Black
                        } else {
                            Color.White
                        },
                    )
                }
            }
        }
    }
}

private suspend fun exportAndShare(context: Context) {
    withContext(Dispatchers.IO) {
        try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val appVersion = "${pkgInfo.versionName} (${pkgInfo.longVersionCode})"
            val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

            val content = DebugLog.export(
                appVersion = appVersion,
                deviceInfo = deviceInfo,
                serverUrl = DashboardApiClient.baseUrl(),
            )

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(context.cacheDir, "hermex_debug_$timestamp.txt")
            file.writeText(content)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Hermex Debug Log — $timestamp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, "Share Debug Log"))
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                DebugLog.error("Settings", "Export failed", e)
            }
        }
    }
}

private fun copyToClipboard(context: Context) {
    try {
        val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val appVersion = "${pkgInfo.versionName} (${pkgInfo.longVersionCode})"

        val content = DebugLog.exportShort(appVersion, DashboardApiClient.baseUrl())
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Hermex Debug Log", content))
        Toast.makeText(context, "Debug log copied to clipboard", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Copy failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
