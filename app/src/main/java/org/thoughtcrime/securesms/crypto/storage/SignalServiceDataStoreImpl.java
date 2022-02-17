package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceDataStore;
import org.whispersystems.signalservice.api.push.ServiceId;

public final class SignalServiceDataStoreImpl implements SignalServiceDataStore {

  private final Context                           context;
  private final SignalServiceAccountDataStoreImpl aciStore;
  private final SignalServiceAccountDataStoreImpl pniStore;

  public SignalServiceDataStoreImpl(@NonNull Context context,
                                    @NonNull SignalServiceAccountDataStoreImpl aciStore,
                                    @NonNull SignalServiceAccountDataStoreImpl pniStore)
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
      throw new AssertionError("Not to be used yet!");
    } else {
      throw new IllegalArgumentException("No matching store found for " + accountIdentifier);
    }
  }

  @Override
  public SignalServiceAccountDataStoreImpl aci() {
    return aciStore;
  }

  @Override
  public SignalServiceAccountDataStoreImpl pni() {
    return pniStore;
  }

  @Override
  public boolean isMultiDevice() {
    return TextSecurePreferences.isMultiDevice(context);
  }
}
