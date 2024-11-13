package org.thoughtcrime.securesms.keyvalue

import org.whispersystems.signalservice.api.storage.SignalStorageManifest
import org.whispersystems.signalservice.api.storage.StorageKey
import org.whispersystems.signalservice.api.util.Preconditions

class StorageServiceValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {
  companion object {
    private const val LAST_SYNC_TIME = "storage.last_sync_time"
    private const val NEEDS_ACCOUNT_RESTORE = "storage.needs_account_restore"
    private const val MANIFEST = "storage.manifest"
    private const val SYNC_STORAGE_KEY = "storage.syncStorageKey"
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = emptyList()

  @get:Synchronized
  val storageKey: StorageKey
    get() {
      if (store.containsKey(SYNC_STORAGE_KEY)) {
        return StorageKey(getBlob(SYNC_STORAGE_KEY, null))
      }
      return SignalStore.svr.masterKey.deriveStorageServiceKey()
    }

  @Synchronized
  fun setStorageKeyFromPrimary(storageKey: StorageKey) {
    Preconditions.checkState(SignalStore.account.isLinkedDevice, "Can only set storage key directly on linked devices")
    putBlob(SYNC_STORAGE_KEY, storageKey.serialize())
  }

  @Synchronized
  fun clearStorageKeyFromPrimary() {
    Preconditions.checkState(SignalStore.account.isLinkedDevice, "Can only clear storage key directly on linked devices")
    remove(SYNC_STORAGE_KEY)
  }

  var lastSyncTime: Long by longValue(LAST_SYNC_TIME, 0)

  var needsAccountRestore: Boolean by booleanValue(NEEDS_ACCOUNT_RESTORE, false)

  var manifest: SignalStorageManifest
    get() {
      val data = getBlob(MANIFEST, null)

      return if (data != null) {
        SignalStorageManifest.deserialize(data)
      } else {
        SignalStorageManifest.EMPTY
      }
    }
    set(manifest) {
      putBlob(MANIFEST, manifest.serialize())
    }
}
