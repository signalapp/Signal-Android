/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job

/**
 * [WallpaperStorageMigrationJob] left some stragglers in the DB for wallpapers that couldn't be found on disk. This cleans those up.
 * It'd be great if we could do this in a database migration, but unfortunately we need to ensure that the aforementioned
 * [WallpaperStorageMigrationJob] finished.
 */
internal class WallpaperCleanupMigrationJob(parameters: Parameters = Parameters.Builder().build()) : MigrationJob(parameters) {
  companion object {
    private val TAG = Log.tag(WallpaperCleanupMigrationJob::class.java)
    const val KEY = "WallpaperCleanupMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    val count = SignalDatabase.recipients.clearMissingFileWallpapersPostMigration()
    if (count > 0) {
      Log.w(TAG, "There were $count legacy wallpapers that needed to be cleared.")
    } else {
      Log.i(TAG, "No legacy wallpapers needed to be cleared.")
    }
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<WallpaperCleanupMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): WallpaperCleanupMigrationJob {
      return WallpaperCleanupMigrationJob(parameters)
    }
  }
}
