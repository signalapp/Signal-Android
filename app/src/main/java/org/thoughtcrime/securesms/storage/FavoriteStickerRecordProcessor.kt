/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.storage

import org.signal.core.util.Hex
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.StickerTables
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.whispersystems.signalservice.api.storage.SignalFavoriteStickerRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.util.OptionalUtil.asOptional
import java.io.IOException
import java.util.Optional

/**
 * Record processor for [SignalFavoriteStickerRecord].
 * Handles merging and updating our local store when processing remote favorite sticker storage records.
 */
class FavoriteStickerRecordProcessor : DefaultStorageRecordProcessor<SignalFavoriteStickerRecord>() {

  companion object {
    private val TAG = Log.tag(FavoriteStickerRecordProcessor::class)

    private const val PACK_ID_LENGTH = 16
    private const val PACK_KEY_LENGTH = 32
  }

  override fun compare(o1: SignalFavoriteStickerRecord, o2: SignalFavoriteStickerRecord): Int {
    return if (o1.proto.packId == o2.proto.packId && o1.proto.stickerId == o2.proto.stickerId) {
      0
    } else {
      1
    }
  }

  /**
   * Favorite stickers must have a 16-byte pack id.
   * Stickers that are not deleted must have a 32-byte pack key and a favorited timestamp.
   */
  override fun isInvalid(remote: SignalFavoriteStickerRecord): Boolean {
    return remote.proto.packId.size != PACK_ID_LENGTH ||
      (remote.proto.deletedAtTimestamp == 0L && (remote.proto.packKey.size != PACK_KEY_LENGTH || remote.proto.favoritedAtTimestamp == 0L))
  }

  override fun getMatching(remote: SignalFavoriteStickerRecord, keyGenerator: StorageKeyGenerator): Optional<SignalFavoriteStickerRecord> {
    val packId = StickerPackId(Hex.toStringCondensed(remote.proto.packId.toByteArray()))
    val local = SignalDatabase.stickers.getFavoriteForStorageSync(packId, remote.proto.stickerId)
    val localStorageId = local?.storageServiceId

    return if (local == null || (local.favoritedAt == 0L && local.unfavoritedAt == 0L)) {
      Log.d(TAG, "Could not find a matching record. Returning an empty.")
      Optional.empty<SignalFavoriteStickerRecord>()
    } else if (localStorageId != null) {
      StorageSyncModels.localToRemoteFavoriteSticker(local, localStorageId).asOptional()
    } else {
      Log.d(TAG, "Sticker was missing a storage service id, generating one")
      val storageId = StorageId.forFavoriteSticker(keyGenerator.generate())
      SignalDatabase.stickers.applyFavoriteStorageIdUpdate(local.rowId, storageId)
      StorageSyncModels.localToRemoteFavoriteSticker(local, storageId.raw).asOptional()
    }
  }

  /**
   * Note that deletions do not always win. This covers the case where a sticker is unfavorited and then refavorited
   */
  override fun merge(remote: SignalFavoriteStickerRecord, local: SignalFavoriteStickerRecord, keyGenerator: StorageKeyGenerator): SignalFavoriteStickerRecord {
    val isRemoteDeleted = remote.proto.deletedAtTimestamp > 0
    val isLocalDeleted = local.proto.deletedAtTimestamp > 0

    return if (isRemoteDeleted && isLocalDeleted && local.proto.deletedAtTimestamp < remote.proto.deletedAtTimestamp) {
      local
    } else {
      remote
    }
  }

  /**
   * Process the remote records and checks that favorited stickers is still under [StickerTables.MAX_FAVORITES].
   * If not, unfavorite the newest additions.
   */
  @Throws(IOException::class)
  override fun process(remoteRecords: Collection<SignalFavoriteStickerRecord>, keyGenerator: StorageKeyGenerator) {
    super.process(remoteRecords, keyGenerator)

    val unfavorited = SignalDatabase.stickers.unfavoriteNewestOverLimit(StickerTables.MAX_FAVORITES)
    if (unfavorited > 0) {
      Log.i(TAG, "Over the favorite sticker limit, unfavorited the $unfavorited newest.")
    }
  }

  override fun insertLocal(record: SignalFavoriteStickerRecord) {
    SignalDatabase.stickers.insertFavoriteFromStorageSync(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<SignalFavoriteStickerRecord>) {
    SignalDatabase.stickers.updateFavoriteFromStorageSync(update.new)
  }
}
