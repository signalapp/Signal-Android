package org.thoughtcrime.securesms.components.webrtc;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.events.CallParticipant;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

class WebRtcCallParticipantsPage {

  private final List<CallParticipant> callParticipants;
  private final CallParticipant       focusedParticipant;
  private final boolean               isSpeaker;
  private final boolean               isRenderInPip;
  private final boolean               isPortrait;
  private final boolean               isLandscapeEnabled;
  private final boolean               hideAvatar;
  private final int                   navBarBottomInset;

  static WebRtcCallParticipantsPage forMultipleParticipants(@NonNull List<CallParticipant> callParticipants,
                                                            @NonNull CallParticipant focusedParticipant,
                                                            boolean isRenderInPip,
                                                            boolean isPortrait,
                                                            boolean isLandscapeEnabled,
                                                            boolean hideAvatar,
                                                            int navBarBottomInset)
  {
    return new WebRtcCallParticipantsPage(callParticipants, focusedParticipant, false, isRenderInPip, isPortrait, isLandscapeEnabled, hideAvatar, navBarBottomInset);
  }

  static WebRtcCallParticipantsPage forSingleParticipant(@NonNull CallParticipant singleParticipant,
                                                         boolean isRenderInPip,
                                                         boolean isPortrait,
                                                         boolean isLandscapeEnabled)
  {
    return new WebRtcCallParticipantsPage(Collections.singletonList(singleParticipant), singleParticipant, true, isRenderInPip, isPortrait, isLandscapeEnabled, false, 0);
  }

  private WebRtcCallParticipantsPage(@NonNull List<CallParticipant> callParticipants,
                                     @NonNull CallParticipant focusedParticipant,
                                     boolean isSpeaker,
                                     boolean isRenderInPip,
                                     boolean isPortrait,
                                     boolean isLandscapeEnabled,
                                     boolean hideAvatar,
                                     int navBarBottomInset)
  {
    this.callParticipants   = callParticipants;
    this.focusedParticipant = focusedParticipant;
    this.isSpeaker          = isSpeaker;
    this.isRenderInPip      = isRenderInPip;
    this.isPortrait         = isPortrait;
    this.isLandscapeEnabled = isLandscapeEnabled;
    this.hideAvatar         = hideAvatar;
    this.navBarBottomInset  = navBarBottomInset;
  }

  public @NonNull List<CallParticipant> getCallParticipants() {
    return callParticipants;
  }

  public @NonNull CallParticipant getFocusedParticipant() {
    return focusedParticipant;
  }

  public boolean isRenderInPip() {
    return isRenderInPip;
  }

  public boolean isSpeaker() {
    return isSpeaker;
  }

  public boolean isPortrait() {
    return isPortrait;
  }

  public boolean shouldHideAvatar() {
    return hideAvatar;
  }

  public int getNavBarBottomInset() {
    return navBarBottomInset;
  }

  public @NonNull CallParticipantsLayout.LayoutStrategy getLayoutStrategy() {
    return CallParticipantsLayoutStrategies.getStrategy(isPortrait, isLandscapeEnabled);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final WebRtcCallParticipantsPage that = (WebRtcCallParticipantsPage) o;
    return isSpeaker == that.isSpeaker &&
           isRenderInPip == that.isRenderInPip &&
           isPortrait == that.isPortrait &&
           isLandscapeEnabled == that.isLandscapeEnabled &&
           hideAvatar == that.hideAvatar &&
           callParticipants.equals(that.callParticipants) &&
           focusedParticipant.equals(that.focusedParticipant) &&
           navBarBottomInset == that.navBarBottomInset;
  }

  @Override
  public int hashCode() {
    return Objects.hash(callParticipants, focusedParticipant, isSpeaker, isRenderInPip, isPortrait, isLandscapeEnabled, hideAvatar, navBarBottomInset);
  }
}
