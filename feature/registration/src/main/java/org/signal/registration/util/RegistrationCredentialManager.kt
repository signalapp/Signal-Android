/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.util

import android.content.Context
import android.os.Build
import android.view.autofill.AutofillManager
import androidx.annotation.UiContext
import androidx.core.content.getSystemService
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialException
import org.signal.core.util.PlayServicesUtil
import org.signal.core.util.logging.Log

/**
 * Retrieves a previously saved password credential (e.g. a backup/recovery key) using Android's
 * Credential Manager.
 */
object RegistrationCredentialManager {

  private val TAG = Log.tag(RegistrationCredentialManager::class)

  /**
   * Whether a password manager / credential provider is available to fill credentials. On API 26+
   * this tracks whether the user has an autofill service enabled; older devices fall back to the
   * Credential Manager Play Services backend, so they are supported only when Play Services is.
   */
  fun isSupported(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 26) {
      context.getSystemService<AutofillManager>()?.isEnabled == true
    } else {
      PlayServicesUtil.getPlayServicesStatus(context) == PlayServicesUtil.PlayServicesStatus.SUCCESS
    }
  }

  /**
   * Prompts the device password manager to let the user pick a saved password credential and returns
   * its value, or null if none was chosen or retrieval failed. Must be called with an Activity
   * context so the Credential Manager UI can be shown.
   */
  suspend fun getPasswordCredential(@UiContext activityContext: Context): String? {
    return try {
      val result = CredentialManager.create(activityContext).getCredential(activityContext, GetCredentialRequest(listOf(GetPasswordOption())))
      (result.credential as? PasswordCredential)?.password
    } catch (e: GetCredentialException) {
      Log.w(TAG, "Failed to retrieve credential from password manager.", e)
      null
    }
  }
}
