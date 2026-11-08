package org.thoughtcrime.securesms.storage

import org.signal.core.util.Hex
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.whispersystems.signalservice.api.storage.SignalInstalledStickerPackRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.util.OptionalUtil.asOptional
import java.util.Optional

/**
 * Record processor for [SignalInstalledStickerPackRecord].
 * Handles merging and updating our local store when processing remote sticker pack storage records.
 */
class InstalledStickerPackRecordProcessor : DefaultStorageRecordProcessor<SignalInstalledStickerPackRecord>() {

  companion object {
    private val TAG = Log.tag(InstalledStickerPackRecordProcessor::class)

    private const val PACK_ID_LENGTH = 16
    private const val PACK_KEY_LENGTH = 32
  }

  override fun compare(o1: SignalInstalledStickerPackRecord, o2: SignalInstalledStickerPackRecord): Int {
    return if (o1.proto.packId == o2.proto.packId) {
      0
    } else {
      1
    }
  }

  /**
   * Sticker packs must have a 16-byte pack id.
   * Packs that are not deleted must have a 32-byte pack key.
   */
  override fun isInvalid(remote: SignalInstalledStickerPackRecord): Boolean {
    return remote.proto.packId.size != PACK_ID_LENGTH ||
      (remote.proto.deletedAtTimestamp == 0L && remote.proto.packKey.size != PACK_KEY_LENGTH)
  }

  override fun getMatching(remote: SignalInstalledStickerPackRecord, keyGenerator: StorageKeyGenerator): Optional<SignalInstalledStickerPackRecord> {
    val packId = StickerPackId(Hex.toStringCondensed(remote.proto.packId.toByteArray()))
    val local = SignalDatabase.stickers.getInstalledPackForStorageSync(packId)

    return if (local == null || (!local.installed && local.deletedTimestampMs == 0L)) {
      Log.d(TAG, "Could not find a matching record. Returning an empty.")
      Optional.empty<SignalInstalledStickerPackRecord>()
    } else if (local.storageServiceId != null) {
      StorageSyncModels.localToRemoteInstalledStickerPack(local, local.storageServiceId.raw).asOptional()
    } else {
      Log.d(TAG, "Pack was missing a storage service id, generating one")
      val storageId = StorageId.forStickerPack(keyGenerator.generate())
      SignalDatabase.stickers.applyStorageIdUpdate(packId, storageId)
      StorageSyncModels.localToRemoteInstalledStickerPack(local, storageId.raw).asOptional()
    }
  }

  /**
   * Note that we don't allow deleted packs to "always win" like we do in other records, because sticker packs can be reinstalled.
   */
  override fun merge(remote: SignalInstalledStickerPackRecord, local: SignalInstalledStickerPackRecord, keyGenerator: StorageKeyGenerator): SignalInstalledStickerPackRecord {
    val isRemoteDeleted = remote.proto.deletedAtTimestamp > 0
    val isLocalDeleted = local.proto.deletedAtTimestamp > 0

    return if (isRemoteDeleted && isLocalDeleted && local.proto.deletedAtTimestamp < remote.proto.deletedAtTimestamp) {
      local
    } else {
      remote
    }
  }

  override fun insertLocal(record: SignalInstalledStickerPackRecord) {
    SignalDatabase.stickers.insertInstalledStickerPackFromStorageSync(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<SignalInstalledStickerPackRecord>) {
    SignalDatabase.stickers.updateInstalledStickerPackFromStorageSync(update.new)
  }
}
