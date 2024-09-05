/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.providers

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.UriMatcher
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.AndroidLogger
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SqlCipherLibraryLoader
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.ApplicationDependencyProvider
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.logging.PersistentLogger
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientCreator
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.AdaptiveBitmapMetrics
import org.thoughtcrime.securesms.util.AvatarUtil
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Provides user avatar bitmaps to the android system service for use in notifications and shortcuts.
 *
 * This file heavily borrows from [PartProvider]
 */
class AvatarProvider : BaseContentProvider() {

  companion object {
    private val TAG = Log.tag(AvatarProvider::class.java)
    private const val CONTENT_AUTHORITY = "${BuildConfig.APPLICATION_ID}.avatar"
    private const val CONTENT_URI_STRING = "content://$CONTENT_AUTHORITY/avatar"
    private const val AVATAR = 1
    private val CONTENT_URI = Uri.parse(CONTENT_URI_STRING)
    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
      addURI(CONTENT_AUTHORITY, "avatar/#", AVATAR)
    }

    private const val VERBOSE = false

    @JvmStatic
    fun getContentUri(recipientId: RecipientId): Uri {
      if (VERBOSE) Log.d(TAG, "getContentUri: $recipientId")
      return ContentUris.withAppendedId(CONTENT_URI, recipientId.toLong())
    }
  }

  private fun init(): Application? {
    val application = context as? ApplicationContext ?: return null

    SqlCipherLibraryLoader.load()
    SignalDatabase.init(
      application,
      DatabaseSecretProvider.getOrCreateDatabaseSecret(application),
      AttachmentSecretProvider.getInstance(application).getOrCreateAttachmentSecret()
    )

    SignalStore.init(application)

    Log.initialize(RemoteConfig::internalUser, AndroidLogger(), PersistentLogger(application))

    if (!AppDependencies.isInitialized) {
      Log.i(TAG, "Initializing AppDependencies.")
      AppDependencies.init(application, ApplicationDependencyProvider(application))
    }

    return application
  }

  override fun onCreate(): Boolean {
    if (VERBOSE) Log.i(TAG, "onCreate called")
    return true
  }

  @Throws(FileNotFoundException::class)
  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
    if (VERBOSE) Log.i(TAG, "openFile() called!")

    val application = init() ?: return null

    if (KeyCachingService.isLocked(application)) {
      Log.w(TAG, "masterSecret was null, abandoning.")
      return null
    }

    if (SignalDatabase.instance == null) {
      Log.w(TAG, "SignalDatabase unavailable")
      return null
    }

    if (uriMatcher.match(uri) == AVATAR) {
      if (VERBOSE) Log.i(TAG, "Loading avatar.")
      try {
        val recipient = getRecipientId(uri)?.let { RecipientCreator.forRecord(application, SignalDatabase.recipients.getRecord(it)) } ?: return null
        return getParcelFileDescriptorForAvatar(recipient)
      } catch (ioe: IOException) {
        Log.w(TAG, ioe)
        throw FileNotFoundException("Error opening file: " + ioe.message)
      }
    }

    Log.w(TAG, "Bad request.")
    throw FileNotFoundException("Request for bad avatar.")
  }

  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
    if (VERBOSE) Log.i(TAG, "query() called: $uri")

    val application = init() ?: return null

    if (SignalDatabase.instance == null) {
      Log.w(TAG, "SignalDatabase unavailable")
      return null
    }

    if (uriMatcher.match(uri) == AVATAR) {
      val recipientId = getRecipientId(uri) ?: return null

      if (AvatarHelper.hasAvatar(application, recipientId)) {
        val file: File = AvatarHelper.getAvatarFile(application, recipientId)
        if (file.exists()) {
          return createCursor(projection, file.name, file.length())
        }
      }

      return createCursor(projection, "fallback-$recipientId.jpg", 0)
    } else {
      return null
    }
  }

  override fun getType(uri: Uri): String? {
    if (VERBOSE) Log.i(TAG, "getType() called: $uri")

    init() ?: return null

    if (SignalDatabase.instance == null) {
      Log.w(TAG, "SignalDatabase unavailable")
      return null
    }

    if (uriMatcher.match(uri) == AVATAR) {
      getRecipientId(uri) ?: return null

      return MediaUtil.IMAGE_PNG
    }

    return null
  }

  override fun insert(uri: Uri, values: ContentValues?): Uri? {
    if (VERBOSE) Log.i(TAG, "insert() called")
    return null
  }

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
    if (VERBOSE) Log.i(TAG, "delete() called")
    context?.applicationContext?.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return 0
  }

  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
    if (VERBOSE) Log.i(TAG, "update() called")
    return 0
  }

  private fun getRecipientId(uri: Uri): RecipientId? {
    val rawRecipientId = ContentUris.parseId(uri)
    if (rawRecipientId <= 0) {
      Log.w(TAG, "Invalid recipient id.")
      return null
    }

    val recipientId = RecipientId.from(rawRecipientId)
    if (!SignalDatabase.recipients.containsId(recipientId)) {
      Log.w(TAG, "Recipient does not exist.")
      return null
    }

    return recipientId
  }

  private fun getParcelFileDescriptorForAvatar(recipient: Recipient): ParcelFileDescriptor {
    val pipe: Array<ParcelFileDescriptor> = ParcelFileDescriptor.createPipe()

    SignalExecutors.UNBOUNDED.execute {
      ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
        if (VERBOSE) Log.i(TAG, "Writing to pipe:${recipient.id}")

        AvatarUtil.getBitmapForNotification(context!!, recipient, AdaptiveBitmapMetrics.innerWidth).apply {
          compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        output.flush()
        if (VERBOSE) Log.i(TAG, "Writing to pipe done:${recipient.id}")
      }
    }

    return pipe[0]
  }
}
