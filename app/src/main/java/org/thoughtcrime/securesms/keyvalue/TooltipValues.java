package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import org.whispersystems.signalservice.api.storage.StorageKey;

public class TooltipValues {

  private static final String BLUR_HUD_ICON   = "tooltip.blur_hud_icon";

  private final KeyValueStore store;

  TooltipValues(@NonNull KeyValueStore store) {
    this.store = store;
  }

  public void onFirstEverAppLaunch() {
  }

  public boolean hasSeenBlurHudIconTooltip() {
    return store.getBoolean(BLUR_HUD_ICON, false);
  }

  public void markBlurHudIconTooltipSeen() {
    store.beginWrite().putBoolean(BLUR_HUD_ICON, true).apply();
  }
}
