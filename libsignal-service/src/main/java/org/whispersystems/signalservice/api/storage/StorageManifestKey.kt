package org.whispersystems.signalservice.api.storage

/**
 * Key used to encrypt a manifest in the storage service.
 *
 * Created via [StorageKey.deriveManifestKey].
 */
class StorageManifestKey(val key: ByteArray) : StorageCipherKey {
  init {
    check(key.size == 32)
  }

  override fun serialize(): ByteArray = key.clone()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as StorageManifestKey

    return key.contentEquals(other.key)
  }

  override fun hashCode(): Int {
    return key.contentHashCode()
  }
}
