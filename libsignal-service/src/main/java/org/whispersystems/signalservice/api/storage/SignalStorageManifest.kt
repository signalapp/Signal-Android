package org.whispersystems.signalservice.api.storage

import org.signal.core.util.toOptional
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord
import org.whispersystems.signalservice.internal.storage.protos.StorageManifest
import java.util.Optional

class SignalStorageManifest(
  @JvmField val version: Long,
  @JvmField val sourceDeviceId: Int,
  @JvmField val storageIds: List<StorageId>
) {

  companion object {
    val EMPTY: SignalStorageManifest = SignalStorageManifest(0, 1, emptyList())

    fun deserialize(serialized: ByteArray): SignalStorageManifest {
      val manifest = StorageManifest.ADAPTER.decode(serialized)
      val manifestRecord = ManifestRecord.ADAPTER.decode(manifest.value_)
      val ids: List<StorageId> = manifestRecord.identifiers.map { id ->
        StorageId.forType(id.raw.toByteArray(), id.typeValue)
      }

      return SignalStorageManifest(manifest.version, manifestRecord.sourceDevice, ids)
    }
  }

  val storageIdsByType: Map<Int, List<StorageId>> = storageIds.groupBy { it.type }

  val versionString: String
    get() = "$version.$sourceDeviceId"

  val accountStorageId: Optional<StorageId>
    get() = storageIdsByType[ManifestRecord.Identifier.Type.ACCOUNT.value]?.takeIf { it.isNotEmpty() }?.get(0).toOptional()

  fun serialize(): ByteArray {
    val ids: List<ManifestRecord.Identifier> = storageIds.map { id ->
      ManifestRecord.Identifier.fromPossiblyUnknownType(id.type, id.raw)
    }

    val manifestRecord = ManifestRecord(
      identifiers = ids,
      sourceDevice = sourceDeviceId
    )

    return StorageManifest(
      version = version,
      value_ = manifestRecord.encodeByteString()
    ).encode()
  }
}
