package com.kye.app.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * App lock uses the phone's own screen lock (fingerprint, face, PIN, pattern), so there is no
 * separate KYE PIN to remember or store.
 */
object AppLock {
    /** Locks again after KYE has been in the background this long. */
    const val RELOCK_AFTER_MS = 60_000L

    private const val ALLOWED = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** False when the phone has no screen lock set, so locking KYE isn't possible. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure(errString.toString())
                // A single unrecognised finger isn't final; the system prompt lets the user retry.
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Use your fingerprint, face or phone PIN")
                .setAllowedAuthenticators(ALLOWED)
                .build()
        )
    }
}
