/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.backup

import org.signal.core.util.Hex

/**
 * Represent a media name for the various types of media that can be archived.
 */
@JvmInline
value class MediaName(val name: String) {

  companion object {
    fun fromPlaintextHashAndRemoteKey(plaintextHash: ByteArray, remoteKey: ByteArray) = MediaName(Hex.toStringCondensed(plaintextHash + remoteKey))
    fun fromPlaintextHashAndRemoteKeyForThumbnail(plaintextHash: ByteArray, remoteKey: ByteArray) = MediaName(Hex.toStringCondensed(plaintextHash + remoteKey) + "_thumbnail")
    fun forThumbnailFromMediaName(mediaName: String) = MediaName("${mediaName}_thumbnail")

    /**
     * For java, since it struggles with value classes.
     */
    @JvmStatic
    fun toMediaIdString(mediaName: String, mediaRootBackupKey: MediaRootBackupKey): String {
      return MediaName(mediaName).toMediaId(mediaRootBackupKey).encode()
    }
  }

  fun toMediaId(mediaRootBackupKey: MediaRootBackupKey): MediaId {
    return mediaRootBackupKey.deriveMediaId(this)
  }

  fun toByteArray(): ByteArray {
    return name.toByteArray()
  }

  override fun toString(): String {
    return name
  }
}
