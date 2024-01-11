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
import org.webrtc.RendererCommon;

class WebRtcCallParticipantsPagerAdapter extends ListAdapter<WebRtcCallParticipantsPage, WebRtcCallParticipantsPagerAdapter.ViewHolder> {

  private static final int VIEW_TYPE_MULTI  = 0;
  private static final int VIEW_TYPE_SINGLE = 1;

  private final Runnable onPageClicked;

  WebRtcCallParticipantsPagerAdapter(@NonNull Runnable onPageClicked) {
    super(new DiffCallback());
    this.onPageClicked = onPageClicked;
  }

  @Override
  public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
    super.onAttachedToRecyclerView(recyclerView);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    final ViewHolder viewHolder;

    switch (viewType) {
      case VIEW_TYPE_SINGLE:
        viewHolder = new SingleParticipantViewHolder((CallParticipantView) LayoutInflater.from(parent.getContext())
            .inflate(R.layout.call_participant_item,
                parent,
                false));
        break;
      case VIEW_TYPE_MULTI:
        viewHolder = new MultipleParticipantViewHolder((CallParticipantsLayout) LayoutInflater.from(parent.getContext())
            .inflate(R.layout.webrtc_call_participants_layout,
                parent,
                false));
        break;
      default:
        throw new IllegalArgumentException("Unsupported viewType: " + viewType);
    }

    viewHolder.itemView.setOnClickListener(unused -> onPageClicked.run());

    return viewHolder;
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.bind(getItem(position));
  }

  @Override
  public int getItemViewType(int position) {
    return getItem(position).isSpeaker() ? VIEW_TYPE_SINGLE : VIEW_TYPE_MULTI;
  }

  static abstract class ViewHolder extends RecyclerView.ViewHolder {
    public ViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    abstract void bind(WebRtcCallParticipantsPage page);
  }

  private static class MultipleParticipantViewHolder extends ViewHolder {

    private final CallParticipantsLayout callParticipantsLayout;

    private MultipleParticipantViewHolder(@NonNull CallParticipantsLayout callParticipantsLayout) {
      super(callParticipantsLayout);
      this.callParticipantsLayout = callParticipantsLayout;
    }

    @Override
    void bind(WebRtcCallParticipantsPage page) {
      callParticipantsLayout.update(page.getCallParticipants(), page.getFocusedParticipant(), page.isRenderInPip(), page.isPortrait(), page.isIncomingRing(), page.getNavBarBottomInset(), page.getLayoutStrategy());
    }
  }

  private static class SingleParticipantViewHolder extends ViewHolder {

    private final CallParticipantView callParticipantView;

    private SingleParticipantViewHolder(CallParticipantView callParticipantView) {
      super(callParticipantView);
      this.callParticipantView = callParticipantView;

      ViewGroup.LayoutParams params = callParticipantView.getLayoutParams();

      params.height = ViewGroup.LayoutParams.MATCH_PARENT;
      params.width  = ViewGroup.LayoutParams.MATCH_PARENT;

      callParticipantView.setLayoutParams(params);
    }


    @Override
    void bind(WebRtcCallParticipantsPage page) {
      CallParticipant participant = page.getCallParticipants().get(0);
      callParticipantView.setCallParticipant(participant);
      callParticipantView.setRenderInPip(page.isRenderInPip());
      callParticipantView.setRaiseHandAllowed(false);
      if (participant.isScreenSharing()) {
        callParticipantView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
      } else {
        callParticipantView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
      }
    }
  }

  private static final class DiffCallback extends DiffUtil.ItemCallback<WebRtcCallParticipantsPage> {
    @Override
    public boolean areItemsTheSame(@NonNull WebRtcCallParticipantsPage oldItem, @NonNull WebRtcCallParticipantsPage newItem) {
      return oldItem.isSpeaker() == newItem.isSpeaker();
    }

    @Override
    public boolean areContentsTheSame(@NonNull WebRtcCallParticipantsPage oldItem, @NonNull WebRtcCallParticipantsPage newItem) {
      return oldItem.equals(newItem);
    }
  }

}
