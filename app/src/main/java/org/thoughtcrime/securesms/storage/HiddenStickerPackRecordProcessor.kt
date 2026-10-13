/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.storage

import org.signal.core.util.Hex
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.whispersystems.signalservice.api.storage.SignalHiddenStickerPackRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.util.OptionalUtil.asOptional
import java.util.Optional

/**
 * Record processor for [SignalHiddenStickerPackRecord].
 * Handles merging and updating our local store when processing remote hidden sticker pack storage records.
 */
class HiddenStickerPackRecordProcessor : DefaultStorageRecordProcessor<SignalHiddenStickerPackRecord>() {

  companion object {
    private val TAG = Log.tag(HiddenStickerPackRecordProcessor::class)

    private const val PACK_ID_LENGTH = 16
    private const val PACK_KEY_LENGTH = 32
  }

  override fun compare(o1: SignalHiddenStickerPackRecord, o2: SignalHiddenStickerPackRecord): Int {
    return if (o1.proto.packId == o2.proto.packId) {
      0
    } else {
      1
    }
  }

  /**
   * Hidden sticker packs must have a 16-byte pack id.
   * Packs that are not unhidden must have a 32-byte pack key.
   */
  override fun isInvalid(remote: SignalHiddenStickerPackRecord): Boolean {
    return remote.proto.packId.size != PACK_ID_LENGTH ||
      (remote.proto.unhiddenAtTimestamp == 0L && remote.proto.packKey.size != PACK_KEY_LENGTH)
  }

  override fun getMatching(remote: SignalHiddenStickerPackRecord, keyGenerator: StorageKeyGenerator): Optional<SignalHiddenStickerPackRecord> {
    val packId = StickerPackId(Hex.toStringCondensed(remote.proto.packId.toByteArray()))
    val local = SignalDatabase.stickers.getHiddenPackForStorageSync(packId)

    return if (local == null || (!local.hidden && local.unhiddenTimestampMs == 0L)) {
      Log.d(TAG, "Could not find a matching record. Returning an empty.")
      Optional.empty<SignalHiddenStickerPackRecord>()
    } else if (local.storageServiceId != null) {
      StorageSyncModels.localToRemoteHiddenStickerPack(local, local.storageServiceId.raw).asOptional()
    } else {
      Log.d(TAG, "Hidden pack was missing a storage service id, generating one")
      val storageId = StorageId.forHiddenStickerPack(keyGenerator.generate())
      SignalDatabase.stickers.applyHiddenPackStorageIdUpdate(packId, storageId)
      StorageSyncModels.localToRemoteHiddenStickerPack(local, storageId.raw).asOptional()
    }
  }

  /**
   * Note that unhidden packs do not always win, because packs can be hidden again after being unhidden.
   */
  override fun merge(remote: SignalHiddenStickerPackRecord, local: SignalHiddenStickerPackRecord, keyGenerator: StorageKeyGenerator): SignalHiddenStickerPackRecord {
    val isRemoteUnhidden = remote.proto.unhiddenAtTimestamp > 0
    val isLocalUnhidden = local.proto.unhiddenAtTimestamp > 0

    // Always take the oldest unhidden timestamp if both are unhidden
    return if (isRemoteUnhidden && isLocalUnhidden && local.proto.unhiddenAtTimestamp < remote.proto.unhiddenAtTimestamp) {
      local
    } else {
      remote
    }
  }

  override fun insertLocal(record: SignalHiddenStickerPackRecord) {
    SignalDatabase.stickers.insertHiddenPackFromStorageSync(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<SignalHiddenStickerPackRecord>) {
    SignalDatabase.stickers.updateHiddenPackFromStorageSync(update.new)
  }
}
