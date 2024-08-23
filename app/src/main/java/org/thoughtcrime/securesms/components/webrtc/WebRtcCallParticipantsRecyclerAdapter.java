package org.thoughtcrime.securesms.components.webrtc;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.core.util.DimensionUnit;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.webrtc.RendererCommon;

public class WebRtcCallParticipantsRecyclerAdapter extends ListAdapter<CallParticipant, WebRtcCallParticipantsRecyclerAdapter.ViewHolder> {

  private static final int PARTICIPANT = 0;
  private static final int EMPTY       = 1;

  public WebRtcCallParticipantsRecyclerAdapter() {
    super(new DiffCallback());
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    if (viewType == PARTICIPANT) {
      return new ParticipantViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.webrtc_call_participant_recycler_item, parent, false));
    } else {
      return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.webrtc_call_participant_recycler_empty_item, parent, false));
    }
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.bind(getItem(position));
  }

  @Override
  public int getItemViewType(int position) {
    return getItem(position) == CallParticipant.EMPTY ? EMPTY : PARTICIPANT;
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    ViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    void bind(@NonNull CallParticipant callParticipant) {}
  }

  private static class ParticipantViewHolder extends ViewHolder {

    private final CallParticipantView callParticipantView;

    ParticipantViewHolder(@NonNull View itemView) {
      super(itemView);
      callParticipantView = itemView.findViewById(R.id.call_participant);

      View audioIndicator       = callParticipantView.findViewById(R.id.call_participant_audio_indicator);
      int  audioIndicatorMargin = (int) DimensionUnit.DP.toPixels(8f);
      ViewUtil.setLeftMargin(audioIndicator, audioIndicatorMargin);
      ViewUtil.setBottomMargin(audioIndicator, audioIndicatorMargin);
    }

    @Override
    void bind(@NonNull CallParticipant callParticipant) {
      callParticipantView.setCallParticipant(callParticipant);
      callParticipantView.setRenderInPip(true);
      callParticipantView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
    }
  }

  private static class DiffCallback extends DiffUtil.ItemCallback<CallParticipant> {

    @Override
    public boolean areItemsTheSame(@NonNull CallParticipant oldItem, @NonNull CallParticipant newItem) {
      return oldItem.getRecipient().equals(newItem.getRecipient());
    }

    @Override
    public boolean areContentsTheSame(@NonNull CallParticipant oldItem, @NonNull CallParticipant newItem) {
      return oldItem.equals(newItem);
    }
  }

}
