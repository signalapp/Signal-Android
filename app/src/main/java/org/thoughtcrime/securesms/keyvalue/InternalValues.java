package org.thoughtcrime.securesms.keyvalue;

import org.thoughtcrime.securesms.util.FeatureFlags;

public final class InternalValues extends SignalStoreValues {

  public static final String GV2_FORCE_INVITES = "internal.gv2.force_invites";

  InternalValues(KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
  }

  public synchronized boolean forceGv2Invites() {
    return FeatureFlags.internalUser() && getBoolean(GV2_FORCE_INVITES, false);
  }
}
