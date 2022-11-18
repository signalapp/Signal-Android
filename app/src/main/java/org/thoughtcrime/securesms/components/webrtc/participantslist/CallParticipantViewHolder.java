package org.thoughtcrime.securesms.components.webrtc.participantslist;

import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.viewholders.RecipientViewHolder;

public class CallParticipantViewHolder extends RecipientViewHolder<CallParticipantViewState> {

  private final View videoMuted;
  private final View audioMuted;
  private final View screenSharing;

  public CallParticipantViewHolder(@NonNull View itemView) {
    super(itemView, null);

    videoMuted    = findViewById(R.id.call_participant_video_muted);
    audioMuted    = findViewById(R.id.call_participant_audio_muted);
    screenSharing = findViewById(R.id.call_participant_screen_sharing);
  }

  @Override
  public void bind(@NonNull CallParticipantViewState model) {
    super.bind(model);

    videoMuted.setVisibility(model.getVideoMutedVisibility());
    audioMuted.setVisibility(model.getAudioMutedVisibility());
    screenSharing.setVisibility(model.getScreenSharingVisibility());
  }
}
