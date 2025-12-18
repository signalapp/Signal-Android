/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import android.net.Uri
import org.thoughtcrime.securesms.backup.v2.local.ArchiveFileSystem
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore

object PluginCache {
  private var archiveFileSystem: ArchiveFileSystem? = null
  var localBackups: ApiPlugin.LocalBackups? = null

  fun getArchiveFileSystem(): ArchiveFileSystem? {
    if (archiveFileSystem == null) {
      val backupDirectoryUri = SignalStore.backup.newLocalBackupsDirectory?.let { Uri.parse(it) }
      if (backupDirectoryUri == null || backupDirectoryUri.path == null) {
        return null
      }

      archiveFileSystem = ArchiveFileSystem.fromUri(AppDependencies.application, backupDirectoryUri)
    }
    return archiveFileSystem
  }

  fun clearBackupCache() {
    archiveFileSystem = null
    localBackups = null
  }
}
