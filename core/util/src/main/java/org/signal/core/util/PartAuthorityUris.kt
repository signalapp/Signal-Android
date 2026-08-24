/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.content.ContentUris
import android.net.Uri
import androidx.core.net.toUri
import org.signal.core.models.database.AttachmentId

/**
 * Builds and parses the content uris served by the application, so that models outside of the app
 * module can reference them without depending on the app module's PartAuthority.
 */
object PartAuthorityUris {

  const val PATH_PART = "part"
  const val PATH_THUMBNAIL = "thumbnail"
  const val PATH_STICKER = "sticker"
  const val PATH_WALLPAPER = "wallpaper"
  const val PATH_EMOJI = "emoji"
  const val PATH_AVATAR_PICKER = "avatar_picker"

  private lateinit var uris: Uris

  /**
   * Must be called with the application id before any uri is built or parsed. Intentionally not tied
   * to [CoreUtilDependencies] so that uris are available from the very start of [android.app.Application.onCreate].
   */
  @JvmStatic
  fun init(authority: String) {
    if (this::uris.isInitialized) {
      return
    }

    uris = Uris(authority)
  }

  @JvmStatic
  val authority: String
    get() = uris.authority

  @JvmStatic
  fun getAttachmentDataUri(attachmentId: AttachmentId): Uri {
    return ContentUris.withAppendedId(uris.part, attachmentId.id)
  }

  @JvmStatic
  fun getAttachmentThumbnailUri(attachmentId: AttachmentId): Uri {
    return ContentUris.withAppendedId(uris.partThumbnail, attachmentId.id)
  }

  @JvmStatic
  fun getStickerUri(id: Long): Uri {
    return ContentUris.withAppendedId(uris.sticker, id)
  }

  @JvmStatic
  fun getEmojiUri(sprite: String): Uri {
    return Uri.withAppendedPath(uris.emoji, sprite)
  }

  @JvmStatic
  fun getAvatarPickerUri(filename: String): Uri {
    return Uri.withAppendedPath(uris.avatarPicker, filename)
  }

  /** The filename component of a single-segment uri, e.g. the sprite name of an emoji uri. */
  @JvmStatic
  fun getFilename(uri: Uri): String {
    return uri.pathSegments[1]
  }

  private class Uris(val authority: String) {
    val part: Uri = "content://$authority/$PATH_PART".toUri()
    val partThumbnail: Uri = "content://$authority/$PATH_THUMBNAIL".toUri()
    val sticker: Uri = "content://$authority/$PATH_STICKER".toUri()
    val emoji: Uri = "content://$authority/$PATH_EMOJI".toUri()
    val avatarPicker: Uri = "content://$authority/$PATH_AVATAR_PICKER".toUri()
  }
}
