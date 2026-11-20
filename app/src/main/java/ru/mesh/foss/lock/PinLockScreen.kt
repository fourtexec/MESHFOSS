@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss.lock

import ru.mesh.foss.core.*

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ru.mesh.foss.R
import ru.mesh.foss.core.Words

/** Экран блокировки: PIN и, если включено, биометрия. */
@Composable
fun PinLockScreen(
    activity: FragmentActivity,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var biometricRunning by remember { mutableStateOf(false) }
    val biometricAvailable = remember(context) {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val showBiometric = PinManager.isBiometricEnabled(context) && biometricAvailable

    fun unlock() {
        pin = ""
        error = null
        onUnlocked()
    }

    fun biometric() {
        if (biometricRunning) return
        biometricRunning = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricRunning = false
                    unlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricRunning = false
                    error = errString.toString()
                }

                override fun onAuthenticationFailed() {
                    error = Words.BiometricNotRecognised.text
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(Words.UnlockMesh.text)
                .setSubtitle(Words.BiometricPromptSubtitle.text)
                .setNegativeButtonText(Words.UsePin.text)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                .build()
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_qr_code),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(Words.UnlockMesh.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            Words.EnterPinToOpen.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= PinManager.MAX_LENGTH && it.all(Char::isDigit)) {
                    pin = it
                    error = null
                }
            },
            label = { Text(Words.Pin.text) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                if (PinManager.verify(context, pin)) unlock()
                else {
                    pin = ""
                    error = Words.WrongPin.text
                }
            },
            enabled = pin.length >= PinManager.MIN_LENGTH,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(Words.Unlock.text)
        }
        if (showBiometric) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = ::biometric, enabled = !biometricRunning) {
                if (biometricRunning) {
                    LoadingIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(Words.UnlockWithBiometrics.text)
                }
            }
        }
    }
}
