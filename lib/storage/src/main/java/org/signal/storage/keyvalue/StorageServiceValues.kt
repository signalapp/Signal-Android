package org.signal.storage.keyvalue

import org.signal.core.models.storageservice.StorageKey
import org.signal.storage.protos.StorageSyncLoopState

class StorageServiceValues(store: KeyValueStore, private val svrValues: SvrValues) : SignalStoreValues(store) {
  companion object {
    private const val LAST_SYNC_TIME = "storage.last_sync_time"
    private const val NEEDS_ACCOUNT_RESTORE = "storage.needs_account_restore"
    private const val MANIFEST = "storage.manifest"
    private const val SYNC_LOOP_STATE = "storage.sync_loop_state"
    private const val LAST_BACKGROUND_SYNC = "storage.last_background_sync"
    private const val NEEDS_SYNC_ON_FOREGROUND = "storage.needs_sync_on_foreground"
  }

  public override fun onFirstEverAppLaunch() = Unit

  override val keysToIncludeInBackup: List<String>
    get() = emptyList()

  val storageKey: StorageKey
    get() {
      return svrValues.masterKey.deriveStorageServiceKey()
    }

  var lastSyncTime: Long by longValue(LAST_SYNC_TIME, 0)

  /** The last time we enqueued a storage sync while the app was in the background. */
  var lastBackgroundSyncTime: Long by longValue(LAST_BACKGROUND_SYNC, 0)

  /** Whether or not we should enqueue a storage sync upon foregrounding the application. */
  var needsSyncOnForeground: Boolean by booleanValue(NEEDS_SYNC_ON_FOREGROUND, false)

  var needsAccountRestore: Boolean by booleanValue(NEEDS_ACCOUNT_RESTORE, false)

  /** Owned by StorageSyncLoopDetector */
  var syncLoopState: StorageSyncLoopState by protoValue(SYNC_LOOP_STATE, StorageSyncLoopState(), StorageSyncLoopState.ADAPTER)

  /** The serialized local storage service manifest, or null if we don't have one. */
  var manifestBytes: ByteArray? by nullableBlobValue(MANIFEST, null)

  /**
   * The [StorageKey] that should be used for our initial storage service data restore.
   * The presence of this value indicates that it hasn't been used yet.
   * Once there has been *any* write to storage service, [SvrValues.masterKeyForInitialDataRestore] needs to be cleared.
   */
  val storageKeyForInitialDataRestore: StorageKey?
    get() = svrValues.masterKeyForInitialDataRestore?.deriveStorageServiceKey()
}
