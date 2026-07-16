/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Computes the runtime permissions requested during registration, branching on API level and
 * whether the user must manually select a backup location.
 *
 * Mirrors the behavior of WelcomePermissions in the app module.
 */
object RegistrationPermissions {

  /**
   * The permissions to request, given whether the user must manually select a backup directory.
   */
  fun getRequiredPermissions(isModernBackupDirectorySelectionRequired: Boolean): List<String> {
    return buildList {
      if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }

      add(Manifest.permission.READ_CONTACTS)
      add(Manifest.permission.WRITE_CONTACTS)

      if (Build.VERSION.SDK_INT < 29 || !isModernBackupDirectorySelectionRequired) {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
      }

      add(Manifest.permission.READ_PHONE_STATE)
      if (Build.VERSION.SDK_INT >= 26) {
        add(Manifest.permission.READ_PHONE_NUMBERS)
      }
    }
  }

  /**
   * The permissions to request for the current device, resolving the backup location requirement
   * from the given [context].
   */
  fun getRequiredPermissions(context: Context): List<String> {
    return getRequiredPermissions(isModernBackupDirectorySelectionRequired(context))
  }

  /**
   * Whether every permission we would request during registration has already been granted, meaning
   * the permission screen can be skipped entirely.
   */
  fun hasAllRequiredPermissions(context: Context): Boolean {
    return getRequiredPermissions(context).all { isGranted(context, it) }
  }

  /**
   * True when the user will have to use the modern system folder picker to select their backup location.
   */
  private fun isModernBackupDirectorySelectionRequired(context: Context): Boolean {
    return Build.VERSION.SDK_INT >= 29 &&
      !(isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE) && isGranted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE))
  }

  private fun isGranted(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
  }
}
