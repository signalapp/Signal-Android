package org.whispersystems.textsecure.storage;

import android.content.Context;

public interface CanonicalRecipientAddress {
  public long getCanonicalAddress(Context context);
}
