package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.FeatureFlags;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.storage.StorageKey;

import java.security.SecureRandom;

public class StorageServiceValues extends SignalStoreValues {

  private static final String LAST_SYNC_TIME        = "storage.last_sync_time";
  private static final String NEEDS_ACCOUNT_RESTORE = "storage.needs_account_restore";

  StorageServiceValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
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
