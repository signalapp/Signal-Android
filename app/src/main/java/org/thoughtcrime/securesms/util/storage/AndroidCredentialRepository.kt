/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.core.content.getSystemService
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import org.signal.core.util.logging.Log

/**
 * Responsible for storing and retrieving credentials using Android's Credential Manager.
 */
object AndroidCredentialRepository {
  private val TAG = Log.tag(AndroidCredentialRepository::class)

  private const val ERROR_CODE_GOOGLE_AUTOFILL_SUCCESS = "[28431]"
  private const val ERROR_CODE_MISSING_CREDENTIAL_MANAGER = "[28434]"
  private const val ERROR_CODE_SAVE_PROMPT_DISABLED = "[28435]"

  val isCredentialManagerSupported: Boolean = Build.VERSION.SDK_INT >= 19

  suspend fun saveCredential(
    activityContext: Context,
    username: String,
    password: String
  ): CredentialManagerResult = try {
    CredentialManager.create(activityContext)
      .createCredential(
        context = activityContext,
        request = CreatePasswordRequest(
          id = username,
          password = password,
          preferImmediatelyAvailableCredentials = false,
          isAutoSelectAllowed = false
        )
      )
    CredentialManagerResult.Success
  } catch (e: Exception) {
    when (e) {
      is CreateCredentialCancellationException -> CredentialManagerResult.UserCanceled
      is CreateCredentialInterruptedException -> CredentialManagerResult.Interrupted(e)
      is CreateCredentialNoCreateOptionException, is CreateCredentialProviderConfigurationException -> CredentialManagerError.MissingCredentialManager(e)
      is CreateCredentialUnknownException -> {
        when {
          Build.VERSION.SDK_INT <= 33 && e.message?.contains(ERROR_CODE_GOOGLE_AUTOFILL_SUCCESS) == true -> {
            // This error only impacts Android 13 and earlier, when Google is the designated autofill provider. The error can be safely disregarded, since users
            // will receive a save prompt from autofill and the password will be stored in Google Password Manager, which syncs with the Credential Manager API.
            Log.d(TAG, "Disregarding CreateCredentialUnknownException and treating credential creation as success: \"${e.message}\".")
            CredentialManagerResult.Success
          }

          e.message?.contains(ERROR_CODE_MISSING_CREDENTIAL_MANAGER) == true -> {
            Log.w(TAG, "Detected MissingCredentialManager error based on CreateCredentialUnknownException message: \"${e.message}\"")
            CredentialManagerError.MissingCredentialManager(e)
          }

          e.message?.contains(ERROR_CODE_SAVE_PROMPT_DISABLED) == true -> {
            Log.w(TAG, "CreateCredentialUnknownException: \"${e.message}\"")
            CredentialManagerError.SavePromptDisabled(e)
          }

          else -> CredentialManagerError.Unexpected(e)
        }
      }

      else -> CredentialManagerError.Unexpected(e)
    }
  }

  /**
   * Returns an [Intent] that can be used to launch the device's password manager settings.
   */
  fun getCredentialManagerSettingsIntent(context: Context): Intent? {
    if (Build.VERSION.SDK_INT >= 34) {
      val intent = Intent(
        Settings.ACTION_CREDENTIAL_PROVIDER,
        Uri.fromParts("package", context.packageName, null)
      )

      if (intent.resolveActivity(context.packageManager) != null) {
        return intent
      }
    }

    if (Build.VERSION.SDK_INT >= 26) {
      val isAutofillSupported = context.getSystemService<AutofillManager>()?.isAutofillSupported() == true
      if (isAutofillSupported) {
        val intent = Intent(
          Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
          Uri.fromParts("package", context.packageName, null)
        )
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
      }
    }

    return null
  }
}

sealed interface CredentialManagerResult {
  data object Success : CredentialManagerResult
  data object UserCanceled : CredentialManagerResult

  /** The backup key save operation was interrupted and should be retried. */
  data class Interrupted(val exception: Exception) : CredentialManagerResult
}

sealed class CredentialManagerError : CredentialManagerResult {
  abstract val exception: Exception

  /** No password manager is configured on the device. */
  data class MissingCredentialManager(override val exception: Exception) : CredentialManagerError()

  /** The user has added this app to the "never save" list in the smart lock for passwords settings. **/
  data class SavePromptDisabled(override val exception: Exception) : CredentialManagerError()

  data class Unexpected(override val exception: Exception) : CredentialManagerError()
}
