package org.thoughtcrime.securesms

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.ServiceUtil

/**
 * Authentication using phone biometric (face, fingerprint recognition) or device lock (pattern, pin or passphrase).
 */
class BiometricDeviceAuthentication(
  private val biometricManager: BiometricManager,
  private val biometricPrompt: BiometricPrompt,
  private val biometricPromptInfo: PromptInfo
) {
  companion object {
    const val AUTHENTICATED = 1
    const val NOT_AUTHENTICATED = -1
    const val TAG: String = "BiometricDeviceAuth"
    const val BIOMETRIC_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    const val ALLOWED_AUTHENTICATORS = BIOMETRIC_AUTHENTICATORS or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /**
     * From the docs on [BiometricManager.canAuthenticate]
     *
     * > Note that not all combinations of authenticator types are supported prior to Android 11 (API 30). Specifically, DEVICE_CREDENTIAL alone is unsupported
     * > prior to API 30, and BIOMETRIC_STRONG | DEVICE_CREDENTIAL is unsupported on API 28-29.
     */
    private val DISALLOWED_BIOMETRIC_VERSIONS = setOf(28, 29)
  }

  fun canAuthenticate(): Boolean {
    return biometricManager.canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
  }

  fun authenticate(context: Context, force: Boolean, showConfirmDeviceCredentialIntent: () -> Unit): Boolean {
    val isKeyGuardSecure = ServiceUtil.getKeyguardManager(context).isKeyguardSecure

    if (!isKeyGuardSecure) {
      Log.w(TAG, "Keyguard not secure...")
      return false
    }

    return if (!DISALLOWED_BIOMETRIC_VERSIONS.contains(Build.VERSION.SDK_INT) && biometricManager.canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS) {
      if (force) {
        Log.i(TAG, "Listening for biometric authentication...")
        biometricPrompt.authenticate(biometricPromptInfo)
      } else {
        Log.i(TAG, "Skipping show system biometric or device lock dialog unless forced")
      }
      true
    } else {
      if (force) {
        Log.i(TAG, "firing intent...")
        showConfirmDeviceCredentialIntent()
      } else {
        Log.i(TAG, "Skipping firing intent unless forced")
      }
      true
    }
  }

  fun cancelAuthentication() {
    biometricPrompt.cancelAuthentication()
  }
}

class BiometricDeviceLockContract : ActivityResultContract<String, Int>() {

  override fun createIntent(context: Context, input: String): Intent {
    val keyguardManager = ServiceUtil.getKeyguardManager(context)
    return keyguardManager.createConfirmDeviceCredentialIntent(input, "")
  }

  override fun parseResult(resultCode: Int, intent: Intent?) =
    if (resultCode != Activity.RESULT_OK) {
      BiometricDeviceAuthentication.NOT_AUTHENTICATED
    } else {
      BiometricDeviceAuthentication.AUTHENTICATED
    }
}
