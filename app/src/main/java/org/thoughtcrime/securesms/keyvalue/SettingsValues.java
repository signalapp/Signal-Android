package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

public final class SettingsValues extends SignalStoreValues {

  public static final String LINK_PREVIEWS = "settings.link_previews";

  SettingsValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    getStore().beginWrite()
              .putBoolean(LINK_PREVIEWS, true)
              .apply();
  }

  public boolean isLinkPreviewsEnabled() {
    return getBoolean(LINK_PREVIEWS, false);
  }

  public void setLinkPreviewsEnabled(boolean enabled) {
    putBoolean(LINK_PREVIEWS, enabled);
  }
}
