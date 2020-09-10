package org.thoughtcrime.securesms.components.webrtc.participantslist;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.viewholders.RecipientMappingModel;

public final class CallParticipantViewState extends RecipientMappingModel<CallParticipantViewState> {

  private final CallParticipant callParticipant;

  CallParticipantViewState(@NonNull CallParticipant callParticipant) {
    this.callParticipant = callParticipant;
  }

  @Override
  public @NonNull Recipient getRecipient() {
    return callParticipant.getRecipient();
  }
}
