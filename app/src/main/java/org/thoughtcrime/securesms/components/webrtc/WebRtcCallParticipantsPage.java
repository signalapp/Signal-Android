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

  static WebRtcCallParticipantsPage forMultipleParticipants(@NonNull List<CallParticipant> callParticipants,
                                                            @NonNull CallParticipant focusedParticipant,
                                                            boolean isRenderInPip,
                                                            boolean isPortrait)
  {
    return new WebRtcCallParticipantsPage(callParticipants, focusedParticipant, false, isRenderInPip, isPortrait);
  }

  static WebRtcCallParticipantsPage forSingleParticipant(@NonNull CallParticipant singleParticipant,
                                                         boolean isRenderInPip,
                                                         boolean isPortrait)
  {
    return new WebRtcCallParticipantsPage(Collections.singletonList(singleParticipant), singleParticipant, true, isRenderInPip, isPortrait);
  }

  private WebRtcCallParticipantsPage(@NonNull List<CallParticipant> callParticipants,
                                     @NonNull CallParticipant focusedParticipant,
                                     boolean isSpeaker,
                                     boolean isRenderInPip,
                                     boolean isPortrait)
  {
    this.callParticipants   = callParticipants;
    this.focusedParticipant = focusedParticipant;
    this.isSpeaker          = isSpeaker;
    this.isRenderInPip      = isRenderInPip;
    this.isPortrait         = isPortrait;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    WebRtcCallParticipantsPage that = (WebRtcCallParticipantsPage) o;
    return isSpeaker == that.isSpeaker &&
           isRenderInPip == that.isRenderInPip &&
           focusedParticipant.equals(that.focusedParticipant) &&
           callParticipants.equals(that.callParticipants) &&
           isPortrait == that.isPortrait;
  }

  @Override
  public int hashCode() {
    return Objects.hash(callParticipants, isSpeaker, focusedParticipant, isRenderInPip, isPortrait);
  }
}
