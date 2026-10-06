package com.mcifu.mirador.ui.common

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Confirma que quien usa el móvil es su dueño con el bloqueo del sistema (huella, cara, PIN,
 * patrón o contraseña). Si el móvil no tiene bloqueo, no hay nada que pedir y se da por válido.
 *
 * Devuelve una función que lanza la comprobación; [onResult] recibe `true` si se superó.
 */
@Composable
fun rememberDeviceAuthenticator(title: String, subtitle: String, onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)
    // Android 8–10: pantalla de bloqueo del sistema (PIN/patrón/contraseña o huella según el móvil).
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result(it.resultCode == Activity.RESULT_OK)
    }
    return remember(context, title, subtitle) {
        {
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            when {
                keyguard == null || !keyguard.isDeviceSecure -> result(true)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> biometricPrompt(context, title, subtitle) { result(it) }
                else -> {
                    @Suppress("DEPRECATION")
                    val intent = keyguard.createConfirmDeviceCredentialIntent(title, subtitle)
                    if (intent == null) result(true) else legacyLauncher.launch(intent)
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun biometricPrompt(context: Context, title: String, subtitle: String, onResult: (Boolean) -> Unit) {
    val prompt = BiometricPrompt.Builder(context)
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
        .build()
    prompt.authenticate(
        CancellationSignal(),
        context.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        },
    )
}
