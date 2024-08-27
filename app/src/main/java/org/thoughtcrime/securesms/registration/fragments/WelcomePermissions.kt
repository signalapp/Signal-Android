/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.fragments

import android.Manifest
import android.os.Build

/**
 * Handles welcome permissions instead of having to do weird giant if statements.
 */
object WelcomePermissions {
  private enum class Permissions {
    POST_NOTIFICATIONS {
      override fun getPermissions(isUserBackupSelectionRequired: Boolean): List<String> {
        return if (Build.VERSION.SDK_INT >= 33) {
          listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
          emptyList()
        }
      }
    },
    CONTACTS {
      override fun getPermissions(isUserBackupSelectionRequired: Boolean): List<String> {
        return listOf(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS)
      }
    },
    STORAGE {
      override fun getPermissions(isUserBackupSelectionRequired: Boolean): List<String> {
        return if (Build.VERSION.SDK_INT < 29 || !isUserBackupSelectionRequired) {
          listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
          emptyList()
        }
      }
    },
    PHONE {
      override fun getPermissions(isUserBackupSelectionRequired: Boolean): List<String> {
        return listOf(Manifest.permission.READ_PHONE_STATE) +
          (if (Build.VERSION.SDK_INT >= 26) listOf(Manifest.permission.READ_PHONE_NUMBERS) else emptyList())
      }
    };

    abstract fun getPermissions(isUserBackupSelectionRequired: Boolean): List<String>
  }

  @JvmStatic
  fun getWelcomePermissions(isUserBackupSelectionRequired: Boolean): Array<String> {
    return Permissions.values().map { it.getPermissions(isUserBackupSelectionRequired) }.flatten().toTypedArray()
  }
}
