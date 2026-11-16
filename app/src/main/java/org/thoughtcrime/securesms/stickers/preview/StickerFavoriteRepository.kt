/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.preview

import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.StickerTables
import java.io.IOException

/**
 * Functions related to favoriting/unfavoriting a sticker.
 */
object StickerFavoriteRepository {

  private val TAG = Log.tag(StickerFavoriteRepository::class.java)

  /**
   * Returns the favorite status of a sticker. If a sticker can't be found, it defaults to false.
   */
  @WorkerThread
  fun isFavorite(packId: String, stickerId: Int): Boolean {
    return SignalDatabase.stickers.isFavorite(packId, stickerId) ?: false
  }

  /**
   * Sets the favorite status of a sticker. If the sticker has no local file, it is copied from a downloaded attachment.
   */
  @WorkerThread
  fun setFavorite(packId: String, packKey: String, stickerId: Int, favorited: Boolean): SetFavoriteResult {
    val isFavorite = SignalDatabase.stickers.isFavorite(packId, stickerId)
    val isMissingStickerFile = !SignalDatabase.stickers.hasStickerFile(packId, stickerId)

    if (favorited && isFavorite != true && SignalDatabase.stickers.getFavoriteCount() >= StickerTables.MAX_FAVORITES) {
      Log.w(TAG, "Maximum sticker limit reached.")
      return SetFavoriteResult.LIMIT_REACHED
    }

    if (isFavorite != null && (!favorited || !isMissingStickerFile)) {
      SignalDatabase.stickers.setFavorite(packId, stickerId, favorited)
      return SetFavoriteResult.SUCCESS
    } else {
      Log.i(TAG, "Missing sticker from sticker table, copying from attachments")
      val attachment = SignalDatabase.attachments.getDownloadedStickerAttachment(packId, stickerId)
      if (attachment == null) {
        Log.w(TAG, "No local copy of sticker to favorite.")
        return SetFavoriteResult.FAILURE
      }

      return try {
        SignalDatabase.attachments.getAttachmentStream(attachment.attachmentId, 0).use { stream ->
          SignalDatabase.stickers.insertFavorite(
            packId = packId,
            packKey = packKey,
            stickerId = stickerId,
            emoji = attachment.stickerLocator?.emoji ?: "",
            contentType = attachment.contentType,
            dataStream = stream
          )
        }
        SetFavoriteResult.SUCCESS
      } catch (e: IOException) {
        Log.w(TAG, "Failed to favorite sticker from attachment.", e)
        SetFavoriteResult.FAILURE
      }
    }
  }

  enum class SetFavoriteResult {
    SUCCESS,
    LIMIT_REACHED,
    FAILURE
  }
}
