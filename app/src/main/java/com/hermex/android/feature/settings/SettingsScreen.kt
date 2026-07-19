package com.hermex.android.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.hermex.core.network.DashboardApiClient
import com.hermex.core.network.DebugLog
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
                .fillMaxSize(),
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
