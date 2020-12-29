package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

public class TooltipValues extends SignalStoreValues {

  private static final int GROUP_CALLING_MAX_TOOLTIP_DISPLAY_COUNT = 3;

  private static final String BLUR_HUD_ICON                    = "tooltip.blur_hud_icon";
  private static final String GROUP_CALL_SPEAKER_VIEW          = "tooltip.group_call_speaker_view";
  private static final String GROUP_CALL_TOOLTIP_DISPLAY_COUNT = "tooltip.group_call_tooltip_display_count";


  TooltipValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  public void onFirstEverAppLaunch() {
  }

  public boolean hasSeenBlurHudIconTooltip() {
    return getBoolean(BLUR_HUD_ICON, false);
  }

  public void markBlurHudIconTooltipSeen() {
    putBoolean(BLUR_HUD_ICON, true);
  }

  public boolean hasSeenGroupCallSpeakerView() {
    return getBoolean(GROUP_CALL_SPEAKER_VIEW, false);
  }

  public void markGroupCallSpeakerViewSeen() {
    putBoolean(GROUP_CALL_SPEAKER_VIEW, true);
  }

  public boolean shouldShowGroupCallingTooltip() {
    return getInteger(GROUP_CALL_TOOLTIP_DISPLAY_COUNT, 0) < GROUP_CALLING_MAX_TOOLTIP_DISPLAY_COUNT;
  }

  public void markGroupCallingTooltipSeen() {
    putInteger(GROUP_CALL_TOOLTIP_DISPLAY_COUNT, getInteger(GROUP_CALL_TOOLTIP_DISPLAY_COUNT, 0) + 1);
  }

  public void markGroupCallingLobbyEntered() {
    putInteger(GROUP_CALL_TOOLTIP_DISPLAY_COUNT, Integer.MAX_VALUE);
  }
}
