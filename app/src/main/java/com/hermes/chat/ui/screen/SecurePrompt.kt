package com.hermes.chat.ui.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.hermes.chat.LocalAuthHelper

/**
 * Full-screen or dialog prompt that requires biometric / device-credential
 * authentication before a privileged command can execute.
 */
@Composable
fun SecurePromptDialog(
    commandLabel: String,
    onAuthenticated: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val authHelper = remember(context) {
        (context as? FragmentActivity)?.let { LocalAuthHelper(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🔒 Privileged Command",
                color = MaterialTheme.colorScheme.primary,
            )
        },
        text = {
            Text(
                text = "The command \"$commandLabel\" requires local authentication.\n\n" +
                        "Authenticate with your device PIN, pattern, password, or biometrics to proceed.",
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    authHelper?.authenticate(
                        title = "Unlock: $commandLabel",
                        subtitle = "Authenticate to execute this privileged command",
                        onSuccess = onAuthenticated,
                        onError = { /* keep dialog open on error */ },
                    )
                },
            ) {
                Text(
                    "Authenticate",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
    )
}
