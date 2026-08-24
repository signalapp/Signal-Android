package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.whispersystems.signalservice.api.SignalServiceDataStore;
import org.signal.core.models.ServiceId;

public final class SignalServiceDataStoreImpl implements SignalServiceDataStore {

  private final Context                           context;
  private final SignalServiceAccountDataStoreImpl aciStore;
  private final SignalServiceAccountDataStoreImpl pniStore;

  public SignalServiceDataStoreImpl(@NonNull Context context,
                                    @NonNull SignalServiceAccountDataStoreImpl aciStore,
                                    @Nullable SignalServiceAccountDataStoreImpl pniStore)
  {
    this.context  = context;
    this.aciStore = aciStore;
    this.pniStore = pniStore;
  }

  @Override
  public SignalServiceAccountDataStoreImpl get(@NonNull ServiceId accountIdentifier) {
    if (accountIdentifier.equals(SignalStore.account().getAci())) {
      return aciStore;
    } else if (accountIdentifier.equals(SignalStore.account().getPni())) {
      return pni();
    } else {
      throw new IllegalArgumentException("No matching store found for " + accountIdentifier);
    }
  }

  @Override
  public SignalServiceAccountDataStoreImpl aci() {
    return aciStore;
  }

  @Override
  public @NonNull SignalServiceAccountDataStoreImpl pni() {
    if (pniStore == null) {
      throw new IllegalStateException("No PNI store! Account has no PNI. Use pniOrNull() on paths that tolerate a phone-number-less account.");
    }
    return pniStore;
  }

  @Override
  public @Nullable SignalServiceAccountDataStoreImpl pniOrNull() {
    return pniStore;
  }

  @Override
  public boolean isMultiDevice() {
    return SignalStore.account().isMultiDevice();
  }
}
