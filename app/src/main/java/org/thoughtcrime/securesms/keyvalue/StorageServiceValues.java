package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.FeatureFlags;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.storage.StorageKey;

import java.security.SecureRandom;

public class StorageServiceValues {

  private static final String LAST_SYNC_TIME        = "storage.last_sync_time";
  private static final String NEEDS_ACCOUNT_RESTORE = "storage.needs_account_restore";

  private final KeyValueStore store;

  StorageServiceValues(@NonNull KeyValueStore store) {
    this.store = store;
  }

  public synchronized StorageKey getOrCreateStorageKey() {
    return SignalStore.kbsValues().getOrCreateMasterKey().deriveStorageServiceKey();
  }

  public long getLastSyncTime() {
    return store.getLong(LAST_SYNC_TIME, 0);
  }

  public void onSyncCompleted() {
    store.beginWrite().putLong(LAST_SYNC_TIME, System.currentTimeMillis()).apply();
  }

  public boolean needsAccountRestore() {
    return store.getBoolean(NEEDS_ACCOUNT_RESTORE, false);
  }

  public void setNeedsAccountRestore(boolean value) {
    store.beginWrite().putBoolean(NEEDS_ACCOUNT_RESTORE, value).apply();
  }
}
