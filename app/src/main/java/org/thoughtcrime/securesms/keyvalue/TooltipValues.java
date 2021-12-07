package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public class TooltipValues extends SignalStoreValues {

  private static final int GROUP_CALLING_MAX_TOOLTIP_DISPLAY_COUNT = 3;

  private static final String BLUR_HUD_ICON                    = "tooltip.blur_hud_icon";
  private static final String GROUP_CALL_SPEAKER_VIEW          = "tooltip.group_call_speaker_view";
  private static final String GROUP_CALL_TOOLTIP_DISPLAY_COUNT = "tooltip.group_call_tooltip_display_count";
  private static final String MULTI_FORWARD_DIALOG             = "tooltip.multi.forward.dialog";
  private static final String BUBBLE_OPT_OUT                   = "tooltip.bubble.opt.out";


  TooltipValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  public void onFirstEverAppLaunch() {
    markMultiForwardDialogSeen();
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Collections.emptyList();
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

  public boolean showMultiForwardDialog() {
    return getBoolean(MULTI_FORWARD_DIALOG, true);
  }

  public void markMultiForwardDialogSeen() {
    putBoolean(MULTI_FORWARD_DIALOG, false);
  }

  public boolean hasSeenBubbleOptOutTooltip() {
    return getBoolean(BUBBLE_OPT_OUT, false);
  }

  public void markBubbleOptOutTooltipSeen() {
    putBoolean(BUBBLE_OPT_OUT, true);
  }
}
