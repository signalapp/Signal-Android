package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import org.whispersystems.signalservice.api.storage.StorageKey;

import java.util.Collections;
import java.util.List;

public class StorageServiceValues extends SignalStoreValues {

  private static final String LAST_SYNC_TIME        = "storage.last_sync_time";
  private static final String NEEDS_ACCOUNT_RESTORE = "storage.needs_account_restore";

  StorageServiceValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Collections.emptyList();
  }

  public synchronized StorageKey getOrCreateStorageKey() {
    return SignalStore.kbsValues().getOrCreateMasterKey().deriveStorageServiceKey();
  }

  public long getLastSyncTime() {
    return getLong(LAST_SYNC_TIME, 0);
  }

  public void onSyncCompleted() {
    putLong(LAST_SYNC_TIME, System.currentTimeMillis());
  }

  public boolean needsAccountRestore() {
    return getBoolean(NEEDS_ACCOUNT_RESTORE, false);
  }

  public void setNeedsAccountRestore(boolean value) {
    putBoolean(NEEDS_ACCOUNT_RESTORE, value);
  }
}
