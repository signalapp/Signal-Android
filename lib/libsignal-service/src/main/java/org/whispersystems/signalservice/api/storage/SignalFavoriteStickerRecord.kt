/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.internal.storage.protos.FavoriteStickerRecord
import java.io.IOException

/**
 * Wrapper around a [FavoriteStickerRecord] to pair it with a [StorageId].
 */
data class SignalFavoriteStickerRecord(
  override val id: StorageId,
  override val proto: FavoriteStickerRecord
) : SignalRecord<FavoriteStickerRecord> {

  companion object {
    fun newBuilder(serializedUnknowns: ByteArray?): FavoriteStickerRecord.Builder {
      return serializedUnknowns?.let { builderFromUnknowns(it) } ?: FavoriteStickerRecord.Builder()
    }

    private fun builderFromUnknowns(serializedUnknowns: ByteArray): FavoriteStickerRecord.Builder {
      return try {
        FavoriteStickerRecord.ADAPTER.decode(serializedUnknowns).newBuilder()
      } catch (e: IOException) {
        FavoriteStickerRecord.Builder()
      }
    }
  }
}
