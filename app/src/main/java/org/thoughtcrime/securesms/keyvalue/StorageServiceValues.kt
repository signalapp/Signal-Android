package org.thoughtcrime.securesms.keyvalue

import org.signal.core.util.logging.Log
import org.whispersystems.signalservice.api.storage.SignalStorageManifest
import org.whispersystems.signalservice.api.storage.StorageKey
import org.whispersystems.signalservice.api.util.Preconditions

class StorageServiceValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {
  companion object {
    private val TAG = Log.tag(StorageServiceValues::class)

    private const val LAST_SYNC_TIME = "storage.last_sync_time"
    private const val NEEDS_ACCOUNT_RESTORE = "storage.needs_account_restore"
    private const val MANIFEST = "storage.manifest"

    // TODO [linked-device] No need to track this separately -- we'd get the AEP from the primary
    private const val SYNC_STORAGE_KEY = "storage.syncStorageKey"
    private const val INITIAL_RESTORE_STORAGE_KEY = "storage.initialRestoreStorageKey"
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = emptyList()

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

  /**
   * The [StorageKey] that should be used for our initial storage service data restore.
   * The presence of this value indicates that it hasn't been used yet.
   * Once there has been *any* write to storage service, this value needs to be cleared.
   */
  @get:Synchronized
  @set:Synchronized
  var storageKeyForInitialDataRestore: StorageKey?
    get() {
      return getBlob(INITIAL_RESTORE_STORAGE_KEY, null)?.let { StorageKey(it) }
    }
    set(value) {
      if (value != storageKeyForInitialDataRestore) {
        if (value == storageKey) {
          Log.w(TAG, "The key already matches the one derived from the AEP! All good, no need to store it.")
          store.beginWrite().putBlob(INITIAL_RESTORE_STORAGE_KEY, null).commit()
        } else if (value != null) {
          Log.w(TAG, "Setting initial restore key!", Throwable())
          store.beginWrite().putBlob(INITIAL_RESTORE_STORAGE_KEY, value.serialize()).commit()
        } else {
          Log.w(TAG, "Clearing initial restore key!", Throwable())
          store.beginWrite().putBlob(INITIAL_RESTORE_STORAGE_KEY, null).commit()
        }
      }
    }
}
