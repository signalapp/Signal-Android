package org.thoughtcrime.securesms.reactions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.adapter.AlwaysChangedDiffUtil;

import java.util.List;

import network.loki.messenger.R;

/**
 * ReactionViewPagerAdapter provides pages to a ViewPager2 which contains the reactions on a given message.
 */
class ReactionViewPagerAdapter extends ListAdapter<EmojiCount, ReactionViewPagerAdapter.ViewHolder> {

  private Listener callback;
  private int selectedPosition = 0;
  private MessageId messageId = null;
  private boolean isUserModerator = false;

  protected ReactionViewPagerAdapter(Listener callback) {
    super(new AlwaysChangedDiffUtil<>());
    this.callback = callback;
  }

  public void setIsUserModerator(boolean isUserModerator) {
    this.isUserModerator = isUserModerator;
  }

  public void setMessageId(MessageId messageId) {
    this.messageId = messageId;
  }

  @Override
  public int getItemCount() {
    return super.getItemCount();
  }

  @NonNull EmojiCount getEmojiCount(int position) {
    return getItem(position);
  }

  void enableNestedScrollingForPosition(int position) {
    selectedPosition = position;

    notifyItemRangeChanged(0, getItemCount(), new Object());
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ViewHolder(callback,
            LayoutInflater.from(parent.getContext()).inflate(R.layout.reactions_bottom_sheet_dialog_fragment_recycler, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
    if (payloads.isEmpty()) {
      onBindViewHolder(holder, position);
    } else {
      holder.setSelected(selectedPosition);
    }
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.onBind(messageId, getItem(position), isUserModerator);
  }

  @Override
  public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
    recyclerView.setNestedScrollingEnabled(false);
    ViewGroup.LayoutParams params = recyclerView.getLayoutParams();
    params.height = (int) (recyclerView.getResources().getDisplayMetrics().heightPixels * 0.80);
    recyclerView.setLayoutParams(params);
    recyclerView.setHasFixedSize(true);
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    private final RecyclerView              recycler;
    private final ReactionRecipientsAdapter adapter;

    public ViewHolder(Listener callback, @NonNull View itemView) {
      super(itemView);
      adapter = new ReactionRecipientsAdapter(callback);
      recycler = itemView.findViewById(R.id.reactions_bottom_view_recipient_recycler);

      ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                 ViewGroup.LayoutParams.MATCH_PARENT);

      recycler.setLayoutParams(params);
      DividerItemDecoration decoration = new DividerItemDecoration(itemView.getContext(), LinearLayoutManager.VERTICAL);
      decoration.setDrawable(ContextUtil.requireDrawable(itemView.getContext(), R.drawable.vertical_divider));
      recycler.addItemDecoration(decoration);
      recycler.setAdapter(adapter);
    }

    public void onBind(MessageId messageId, @NonNull EmojiCount emojiCount, boolean isUserModerator) {
      adapter.updateData(messageId, emojiCount, isUserModerator);
    }

    public void setSelected(int position) {
      recycler.setNestedScrollingEnabled(getAdapterPosition() == position);
    }
  }

  public interface Listener {
    void onRemoveReaction(@NonNull String emoji, @NonNull MessageId messageId, long timestamp);

    void onClearAll(@NonNull String emoji, @NonNull MessageId messageId);
  }

}
