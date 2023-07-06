/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup

object BackupVersions {
  const val CURRENT_VERSION = 1
  const val MINIMUM_VERSION = 0

  @JvmStatic
  fun isCompatible(version: Int): Boolean {
    return version in MINIMUM_VERSION..CURRENT_VERSION
  }

  @JvmStatic
  fun isFrameLengthEncrypted(version: Int): Boolean {
    return version >= 1
  }
}
