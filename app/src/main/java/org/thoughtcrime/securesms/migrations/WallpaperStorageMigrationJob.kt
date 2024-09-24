/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.migrations

import android.content.Context
import android.net.Uri
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.storage.FileStorage
import java.io.File
import java.io.IOException

/**
 * We need to move the wallpapers to be stored in the attachment table as part of backups V2.
 */
internal class WallpaperStorageMigrationJob(parameters: Parameters = Parameters.Builder().build()) : MigrationJob(parameters) {
  companion object {
    private val TAG = Log.tag(WallpaperStorageMigrationJob::class.java)
    const val KEY = "WallpaperStorageMigrationJob"

    private const val DIRECTORY = "wallpapers"
    private const val FILENAME_BASE = "wallpaper"

    private val CONTENT_URI = Uri.parse("content://${BuildConfig.APPLICATION_ID}/wallpaper")
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = true

  override fun performMigration() {
    val wallpaperFileNames = FileStorage.getAll(context, DIRECTORY, FILENAME_BASE)

    if (wallpaperFileNames.isEmpty()) {
      Log.i(TAG, "No wallpapers to migrate. Done.")
      return
    }

    Log.i(TAG, "There are ${wallpaperFileNames.size} wallpapers to migrate.")

    val currentDefaultWallpaperUri = SignalStore.wallpaper.currentRawWallpaper?.file_?.uri

    for (filename in wallpaperFileNames) {
      val inputStream = FileStorage.read(context, DIRECTORY, filename)
      val wallpaperAttachmentId = SignalDatabase.attachments.insertWallpaper(inputStream)

      val directory = context.getDir(DIRECTORY, Context.MODE_PRIVATE)
      val file = File(directory, filename)

      val legacyUri = Uri.withAppendedPath(CONTENT_URI, filename)
      val newUri = PartAuthority.getAttachmentDataUri(wallpaperAttachmentId)

      val updatedUserCount = SignalDatabase.recipients.migrateWallpaperUri(
        legacyUri = legacyUri,
        newUri = newUri
      )
      Log.d(TAG, "Wallpaper with name '$filename' was in use by $updatedUserCount recipients.")

      if (currentDefaultWallpaperUri == legacyUri.toString()) {
        Log.d(TAG, "Wallpaper with name '$filename' was set as the default wallpaper. Updating.")
        SignalStore.wallpaper.setRawWallpaperForMigration(Wallpaper(file_ = Wallpaper.File(uri = newUri.toString())))
      }

      val deleted = file.delete()
      if (!deleted) {
        Log.w(TAG, "Failed to delete wallpaper file: $file")
      }
    }

    AppDependencies.recipientCache.clear()
    AppDependencies.recipientCache.warmUp()

    Log.i(TAG, "Successfully migrated ${wallpaperFileNames.size} wallpapers.")
  }

  override fun shouldRetry(e: Exception): Boolean = e is IOException

  class Factory : Job.Factory<WallpaperStorageMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): WallpaperStorageMigrationJob {
      return WallpaperStorageMigrationJob(parameters)
    }
  }
}
