package org.thoughtcrime.securesms.util.dualsim;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class SubscriptionInfoCompat {

  private final int subscriptionId;
  private final @Nullable CharSequence displayName;

  public SubscriptionInfoCompat(int subscriptionId, @Nullable  CharSequence displayName) {
    this.subscriptionId = subscriptionId;
    this.displayName    = displayName;
  }

  public @NonNull CharSequence getDisplayName() {
    return displayName != null ? displayName : "";
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }
}
