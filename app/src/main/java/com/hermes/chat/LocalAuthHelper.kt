package com.hermes.chat

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Wraps Android BiometricPrompt to authenticate locally.
 * Falls back to device credential (PIN/pattern/password) when available.
 */
class LocalAuthHelper(private val activity: FragmentActivity) {

    fun authenticate(
        title: String = "Authenticate",
        subtitle: String = "Unlock this command",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // BIOMETRIC_ERROR_NO_BIOMETRICS / BIOMETRIC_ERROR_HW_UNAVAILABLE are
                // reported here — surface them so the UI can decide what to show.
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                // Multiple failures — let the system UI handle retry
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators())
            .build()

        prompt.authenticate(promptInfo)
    }

    /** Check if biometric or device credential is available. */
    fun canAuthenticate(): Boolean {
        val mgr = BiometricManager.from(activity)
        return mgr.canAuthenticate(allowedAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun allowedAuthenticators(): Int {
        val bio = BiometricManager.Authenticators.BIOMETRIC_STRONG
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Device credential (PIN/pattern/password) as fallback on API 28+
            bio or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            bio
        }
    }
}
