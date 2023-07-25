/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.providers

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.UriMatcher
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.MemoryFile
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import androidx.annotation.RequiresApi
import org.signal.core.util.StreamUtil
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.AvatarUtil
import org.thoughtcrime.securesms.util.DrawableUtil
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.MemoryFileUtil
import org.thoughtcrime.securesms.util.ServiceUtil
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

    @JvmStatic
    fun getContentUri(context: Context, recipientId: RecipientId): Uri {
      val uri = ContentUris.withAppendedId(CONTENT_URI, recipientId.toLong())
      context.applicationContext.grantUriPermission("com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

      return uri
    }
  }

  override fun onCreate(): Boolean {
    Log.i(TAG, "onCreate called")
    return true
  }

  @Throws(FileNotFoundException::class)
  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
    Log.i(TAG, "openFile() called!")

    if (KeyCachingService.isLocked(context)) {
      Log.w(TAG, "masterSecret was null, abandoning.")
      return null
    }

    if (uriMatcher.match(uri) == AVATAR) {
      Log.i(TAG, "Loading avatar.")
      try {
        val recipient = getRecipientId(uri)?.let { Recipient.resolved(it) } ?: return null
        return if (Build.VERSION.SDK_INT >= 26) {
          getParcelStreamProxyForAvatar(recipient)
        } else {
          getParcelStreamForAvatar(recipient)
        }
      } catch (ioe: IOException) {
        Log.w(TAG, ioe)
        throw FileNotFoundException("Error opening file")
      }
    }

    Log.w(TAG, "Bad request.")
    throw FileNotFoundException("Request for bad avatar.")
  }

  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
    Log.i(TAG, "query() called: $uri")

    if (SignalDatabase.instance == null) {
      Log.w(TAG, "SignalDatabase unavailable")
      return null
    }

    if (uriMatcher.match(uri) == AVATAR) {
      val recipientId = getRecipientId(uri) ?: return null

      if (AvatarHelper.hasAvatar(context!!, recipientId)) {
        val file: File = AvatarHelper.getAvatarFile(context!!, recipientId)
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
    Log.i(TAG, "getType() called: $uri")

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
    Log.i(TAG, "insert() called")
    return null
  }

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
    Log.i(TAG, "delete() called")
    context?.applicationContext?.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return 0
  }

  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
    Log.i(TAG, "update() called")
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

  @RequiresApi(26)
  private fun getParcelStreamProxyForAvatar(recipient: Recipient): ParcelFileDescriptor {
    val storageManager = requireNotNull(ServiceUtil.getStorageManager(context!!))
    val handlerThread = SignalExecutors.getAndStartHandlerThread("avatarservice-proxy", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD)
    val handler = Handler(handlerThread.looper)

    val parcelFileDescriptor = storageManager.openProxyFileDescriptor(
      ParcelFileDescriptor.MODE_READ_ONLY,
      ProxyCallback(context!!.applicationContext, recipient, handlerThread),
      handler
    )

    Log.i(TAG, "${recipient.id}:createdProxy")
    return parcelFileDescriptor
  }

  private fun getParcelStreamForAvatar(recipient: Recipient): ParcelFileDescriptor {
    val outputStream = ByteArrayOutputStream()
    AvatarUtil.getBitmapForNotification(context!!, recipient, DrawableUtil.SHORTCUT_INFO_WRAPPED_SIZE).apply {
      compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    }

    val memoryFile = MemoryFile("${recipient.id}-imf", outputStream.size())

    val memoryFileOutputStream = memoryFile.outputStream
    StreamUtil.copy(ByteArrayInputStream(outputStream.toByteArray()), memoryFileOutputStream)
    StreamUtil.close(memoryFileOutputStream)

    return MemoryFileUtil.getParcelFileDescriptor(memoryFile)
  }

  @RequiresApi(26)
  private class ProxyCallback(
    private val context: Context,
    private val recipient: Recipient,
    private val handlerThread: HandlerThread
  ) : ProxyFileDescriptorCallback() {

    private var memoryFile: MemoryFile? = null

    override fun onGetSize(): Long {
      Log.i(TAG, "${recipient.id}:onGetSize:${Thread.currentThread().name}:${hashCode()}")
      ensureResourceLoaded()
      return memoryFile!!.length().toLong()
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray?): Int {
      ensureResourceLoaded()
      val memoryFileSnapshot = memoryFile
      return memoryFileSnapshot!!.readBytes(data, offset.toInt(), 0, size.coerceAtMost((memoryFileSnapshot.length() - offset).toInt()))
    }

    override fun onRelease() {
      Log.i(TAG, "${recipient.id}:onRelease")
      memoryFile = null
      handlerThread.quitSafely()
    }

    private fun ensureResourceLoaded() {
      if (memoryFile != null) {
        return
      }

      Log.i(TAG, "Reading ${recipient.id} icon into RAM.")

      val outputStream = ByteArrayOutputStream()
      val avatarBitmap = AvatarUtil.getBitmapForNotification(context, recipient, DrawableUtil.SHORTCUT_INFO_WRAPPED_SIZE)
      avatarBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)

      Log.i(TAG, "Writing ${recipient.id} icon to MemoryFile")

      memoryFile = MemoryFile("${recipient.id}-imf", outputStream.size())

      val memoryFileOutputStream = memoryFile!!.outputStream
      StreamUtil.copy(ByteArrayInputStream(outputStream.toByteArray()), memoryFileOutputStream)
      StreamUtil.close(memoryFileOutputStream)
    }
  }
}
