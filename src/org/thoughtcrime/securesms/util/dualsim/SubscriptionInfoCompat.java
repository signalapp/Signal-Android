package org.thoughtcrime.securesms.util.dualsim;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class SubscriptionInfoCompat {

  private final int subscriptionId;
  private final int mcc;
  private final int mnc;
  private final @Nullable CharSequence displayName;

  public SubscriptionInfoCompat(int subscriptionId, @Nullable  CharSequence displayName, int mcc, int mnc) {
    this.subscriptionId = subscriptionId;
    this.displayName    = displayName;
    this.mcc            = mcc;
    this.mnc            = mnc;
  }

  public @NonNull CharSequence getDisplayName() {
    return displayName != null ? displayName : "";
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public int getMnc() {
    return mnc;
  }

  public int getMcc() {
    return mcc;
  }
}
