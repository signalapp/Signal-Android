package org.whispersystems.signalservice.api.storage

import okio.ByteString.Companion.toByteString
import org.signal.core.util.getUnknownEnumValue
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.logging.Log
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord
import org.whispersystems.signalservice.internal.storage.protos.StorageItem
import org.whispersystems.signalservice.internal.storage.protos.StorageManifest
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord
import java.io.IOException

object SignalStorageModels {
  private val TAG: String = SignalStorageModels::class.java.simpleName

  @JvmStatic
  @Throws(IOException::class, InvalidKeyException::class)
  fun remoteToLocalStorageManifest(manifest: StorageManifest, storageKey: StorageKey): SignalStorageManifest {
    val rawRecord = SignalStorageCipher.decrypt(storageKey.deriveManifestKey(manifest.version), manifest.value_.toByteArray())
    val manifestRecord = ManifestRecord.ADAPTER.decode(rawRecord)
    val ids: MutableList<StorageId> = ArrayList(manifestRecord.identifiers.size)

    for (id in manifestRecord.identifiers) {
      val typeValue = if ((id.type != ManifestRecord.Identifier.Type.UNKNOWN)) {
        id.type.value
      } else {
        id.getUnknownEnumValue(StorageRecordProtoUtil.STORAGE_ID_TYPE_TAG)
      }

      ids.add(StorageId.forType(id.raw.toByteArray(), typeValue))
    }

    return SignalStorageManifest(manifestRecord.version, manifestRecord.sourceDevice, ids)
  }

  @JvmStatic
  @Throws(IOException::class, InvalidKeyException::class)
  fun remoteToLocalStorageRecord(item: StorageItem, type: Int, storageKey: StorageKey): SignalStorageRecord {
    val key = item.key.toByteArray()
    val rawRecord = SignalStorageCipher.decrypt(storageKey.deriveItemKey(key), item.value_.toByteArray())
    val record = StorageRecord.ADAPTER.decode(rawRecord)
    val id = StorageId.forType(key, type)

    if (record.contact != null && type == ManifestRecord.Identifier.Type.CONTACT.value) {
      return SignalContactRecord(id, record.contact).toSignalStorageRecord()
    } else if (record.groupV1 != null && type == ManifestRecord.Identifier.Type.GROUPV1.value) {
      return SignalGroupV1Record(id, record.groupV1).toSignalStorageRecord()
    } else if (record.groupV2 != null && type == ManifestRecord.Identifier.Type.GROUPV2.value && record.groupV2.masterKey.size == GroupMasterKey.SIZE) {
      return SignalGroupV2Record(id, record.groupV2).toSignalStorageRecord()
    } else if (record.account != null && type == ManifestRecord.Identifier.Type.ACCOUNT.value) {
      return SignalAccountRecord(id, record.account).toSignalStorageRecord()
    } else if (record.storyDistributionList != null && type == ManifestRecord.Identifier.Type.STORY_DISTRIBUTION_LIST.value) {
      return SignalStoryDistributionListRecord(id, record.storyDistributionList).toSignalStorageRecord()
    } else if (record.callLink != null && type == ManifestRecord.Identifier.Type.CALL_LINK.value) {
      return SignalCallLinkRecord(id, record.callLink).toSignalStorageRecord()
    } else {
      if (StorageId.isKnownType(type)) {
        Log.w(TAG, "StorageId is of known type ($type), but the data is bad! Falling back to unknown.")
      }
      return SignalStorageRecord.forUnknown(StorageId.forType(key, type))
    }
  }

  @JvmStatic
  fun localToRemoteStorageRecord(record: SignalStorageRecord, storageKey: StorageKey): StorageItem {
    val builder = StorageRecord.Builder()

    if (record.proto.contact != null) {
      builder.contact(record.proto.contact)
    } else if (record.proto.groupV1 != null) {
      builder.groupV1(record.proto.groupV1)
    } else if (record.proto.groupV2 != null) {
      builder.groupV2(record.proto.groupV2)
    } else if (record.proto.account != null) {
      builder.account(record.proto.account)
    } else if (record.proto.storyDistributionList != null) {
      builder.storyDistributionList(record.proto.storyDistributionList)
    } else if (record.proto.callLink != null) {
      builder.callLink(record.proto.callLink)
    } else {
      throw InvalidStorageWriteError()
    }

    val remoteRecord = builder.build()
    val itemKey = storageKey.deriveItemKey(record.id.raw)
    val encryptedRecord = SignalStorageCipher.encrypt(itemKey, remoteRecord.encode())

    return StorageItem.Builder()
      .key(record.id.raw.toByteString())
      .value_(encryptedRecord.toByteString())
      .build()
  }

  private class InvalidStorageWriteError : Error()
}
