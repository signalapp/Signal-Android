package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.FeatureFlags;
import org.whispersystems.signalservice.api.kbs.MasterKey;

import java.security.SecureRandom;

public class StorageServiceValues {

  private static final String STORAGE_MASTER_KEY = "storage.storage_master_key";
  private static final String LAST_SYNC_TIME     = "storage.last_sync_time";

  private final KeyValueStore store;

  StorageServiceValues(@NonNull KeyValueStore store) {
    this.store = store;
  }

  public synchronized MasterKey getOrCreateStorageMasterKey() {
    byte[] blob = store.getBlob(STORAGE_MASTER_KEY, null);

    if (blob == null) {
      store.beginWrite()
           .putBlob(STORAGE_MASTER_KEY, MasterKey.createNew(new SecureRandom()).serialize())
           .commit();
      blob = store.getBlob(STORAGE_MASTER_KEY, null);
    }

    return new MasterKey(blob);
  }

  public synchronized void rotateStorageMasterKey() {
    store.beginWrite()
         .putBlob(STORAGE_MASTER_KEY, MasterKey.createNew(new SecureRandom()).serialize())
         .commit();
  }

  public long getLastSyncTime() {
    return store.getLong(LAST_SYNC_TIME, 0);
  }

  public void onSyncCompleted() {
    store.beginWrite().putLong(LAST_SYNC_TIME, System.currentTimeMillis()).apply();
  }
}
