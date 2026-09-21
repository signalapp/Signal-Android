/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.passwordmanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.annotation.UiContext
import androidx.core.content.getSystemService
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import org.signal.core.util.PlayServicesUtil
import org.signal.core.util.censor
import org.signal.core.util.logging.Log

/**
 * Stores and retrieves password credentials (e.g. backup/recovery keys) using Android's
 * Credential Manager, which delegates to the user's password manager.
 */
object SignalCredentialManager {

  private val TAG = Log.tag(SignalCredentialManager::class)

  private const val ERROR_CODE_GOOGLE_AUTOFILL_SUCCESS = "[28431]"
  private const val ERROR_CODE_MISSING_CREDENTIAL_MANAGER = "[28434]"
  private const val ERROR_CODE_SAVE_PROMPT_DISABLED = "[28435]"

  /**
   * Whether a password manager / credential provider is available. On API 26+ this tracks whether
   * the user has an autofill service enabled; older devices fall back to the Credential Manager
   * Play Services backend, so they are supported only when Play Services is.
   */
  fun isSupported(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 26) {
      context.getSystemService<AutofillManager>()?.isEnabled == true
    } else {
      PlayServicesUtil.getPlayServicesStatus(context) == PlayServicesUtil.PlayServicesStatus.SUCCESS
    }
  }

  /**
   * Prompts the user to save a password credential to their password manager. Must be called with
   * an Activity context so the Credential Manager UI can be shown.
   */
  suspend fun saveCredential(
    @UiContext activityContext: Context,
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
   * Prompts the device password manager to let the user pick a saved password credential and
   * returns both halves of it, or null if none was chosen or retrieval failed. If [id] is provided,
   * only a credential with that id will be returned. Must be called with an Activity context so the
   * Credential Manager UI can be shown.
   */
  suspend fun getCredential(@UiContext activityContext: Context, id: String? = null): UsernamePasswordCredential? = try {
    val result = CredentialManager.create(activityContext).getCredential(activityContext, GetCredentialRequest(listOf(GetPasswordOption())))
    val credential = result.credential
    if (credential is PasswordCredential && (id == null || credential.id == id)) {
      UsernamePasswordCredential(username = credential.id, password = credential.password)
    } else {
      Log.w(TAG, "Failed to find a matching credential from the password manager.")
      null
    }
  } catch (e: GetCredentialException) {
    Log.w(TAG, "Failed to retrieve credential from password manager.", e)
    null
  }

  /**
   * Returns an [Intent] that can be used to launch the device's password manager settings.
   */
  fun getSettingsIntent(context: Context): Intent? {
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

/** A username/password pair the user picked from their password manager. */
data class UsernamePasswordCredential(val username: String, val password: String) {
  override fun toString(): String = "UsernamePasswordCredential(username=${username.censor()}, password=${password.censor()})"
}

/** Represents the result of a [SignalCredentialManager] save operation. */
sealed interface CredentialManagerResult {
  data object Success : CredentialManagerResult
  data object UserCanceled : CredentialManagerResult

  /** The save operation was interrupted and should be retried. */
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
