package org.thoughtcrime.securesms.keyvalue

import org.signal.core.util.logging.Log
import org.whispersystems.signalservice.api.storage.SignalStorageManifest
import org.whispersystems.signalservice.api.storage.StorageKey

class StorageServiceValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {
  companion object {
    private val TAG = Log.tag(StorageServiceValues::class)

    private const val LAST_SYNC_TIME = "storage.last_sync_time"
    private const val NEEDS_ACCOUNT_RESTORE = "storage.needs_account_restore"
    private const val MANIFEST = "storage.manifest"
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = emptyList()

  val storageKey: StorageKey
    get() {
      return SignalStore.svr.masterKey.deriveStorageServiceKey()
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
   * Once there has been *any* write to storage service, [SvrValues.masterKeyForInitialDataRestore] needs to be cleared.
   */
  val storageKeyForInitialDataRestore: StorageKey?
    get() = SignalStore.svr.masterKeyForInitialDataRestore?.deriveStorageServiceKey()
}
