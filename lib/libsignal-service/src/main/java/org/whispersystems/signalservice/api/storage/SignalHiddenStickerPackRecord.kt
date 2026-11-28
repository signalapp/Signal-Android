/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.internal.storage.protos.HiddenStickerPackRecord
import java.io.IOException

/**
 * Wrapper around a [HiddenStickerPackRecord] to pair it with a [StorageId].
 */
data class SignalHiddenStickerPackRecord(
  override val id: StorageId,
  override val proto: HiddenStickerPackRecord
) : SignalRecord<HiddenStickerPackRecord> {

  companion object {
    fun newBuilder(serializedUnknowns: ByteArray?): HiddenStickerPackRecord.Builder {
      return serializedUnknowns?.let { builderFromUnknowns(it) } ?: HiddenStickerPackRecord.Builder()
    }

    private fun builderFromUnknowns(serializedUnknowns: ByteArray): HiddenStickerPackRecord.Builder {
      return try {
        HiddenStickerPackRecord.ADAPTER.decode(serializedUnknowns).newBuilder()
      } catch (e: IOException) {
        HiddenStickerPackRecord.Builder()
      }
    }
  }
}
