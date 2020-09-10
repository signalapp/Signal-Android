package org.thoughtcrime.securesms.components.webrtc;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.events.CallParticipant;

class WebRtcCallParticipantsRecyclerAdapter extends ListAdapter<CallParticipant, WebRtcCallParticipantsRecyclerAdapter.ViewHolder> {

  protected WebRtcCallParticipantsRecyclerAdapter() {
    super(new DiffCallback());
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.webrtc_call_participant_recycler_item, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.bind(getItem(position));
  }

  static class ViewHolder extends RecyclerView.ViewHolder {

    private final CallParticipantView callParticipantView;

    ViewHolder(@NonNull View itemView) {
      super(itemView);
      callParticipantView = itemView.findViewById(R.id.call_participant);
    }

    void bind(@NonNull CallParticipant callParticipant) {
      callParticipantView.setCallParticipant(callParticipant);
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
