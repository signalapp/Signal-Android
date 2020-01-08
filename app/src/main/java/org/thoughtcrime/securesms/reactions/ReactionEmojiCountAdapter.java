package org.thoughtcrime.securesms.reactions;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.util.ThemeUtil;

import java.util.Collections;
import java.util.List;

final class ReactionEmojiCountAdapter extends RecyclerView.Adapter<ReactionEmojiCountAdapter.ViewHolder> {

  private List<EmojiCount> emojiCountList   = Collections.emptyList();
  private int              selectedPosition = -1;

  private final OnEmojiCountSelectedListener onEmojiCountSelectedListener;

  ReactionEmojiCountAdapter(@NonNull OnEmojiCountSelectedListener onEmojiCountSelectedListener) {
    this.onEmojiCountSelectedListener = onEmojiCountSelectedListener;
  }

  void updateData(@NonNull List<EmojiCount> newEmojiCount) {
    if (selectedPosition != -1) {
      EmojiCount oldSelection = emojiCountList.get(selectedPosition);
      int        newPosition  = -1;

      for (int i = 0; i < newEmojiCount.size(); i++) {
        if (newEmojiCount.get(i).getEmoji().equals(oldSelection.getEmoji())) {
          newPosition = i;
          break;
        }
      }

      if (newPosition == -1 && !newEmojiCount.isEmpty()) {
        selectedPosition = 0;
        onEmojiCountSelectedListener.onSelected(newEmojiCount.get(0));
      } else {
        selectedPosition = newPosition;
      }
    } else if (!newEmojiCount.isEmpty()) {
      selectedPosition = 0;
      onEmojiCountSelectedListener.onSelected(newEmojiCount.get(0));
    }

    this.emojiCountList = newEmojiCount;

    notifyDataSetChanged();
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.reactions_bottom_sheet_dialog_fragment_emoji_item, parent, false), position -> {
      if (position != -1 && position != selectedPosition) {
        onEmojiCountSelectedListener.onSelected(emojiCountList.get(position));

        int oldPosition  = selectedPosition;
        selectedPosition = position;

        notifyItemChanged(oldPosition);
        notifyItemChanged(position);
      }
    });
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.bind(emojiCountList.get(position), selectedPosition);
  }

  @Override
  public int getItemCount() {
    return emojiCountList.size();
  }

  static final class ViewHolder extends RecyclerView.ViewHolder {

    private final Drawable      selected;
    private final EmojiTextView emojiView;
    private final TextView      countView;

    public ViewHolder(@NonNull View itemView, @NonNull OnViewHolderClickListener onClickListener) {
      super(itemView);
      emojiView = itemView.findViewById(R.id.reactions_bottom_view_emoji_item_emoji);
      countView = itemView.findViewById(R.id.reactions_bottom_view_emoji_item_text);
      selected  = ThemeUtil.getThemedDrawable(itemView.getContext(), R.attr.reactions_bottom_dialog_fragment_emoji_selected);

      itemView.setOnClickListener(v -> onClickListener.onClick(getAdapterPosition()));
    }

    void bind(@NonNull EmojiCount emojiCount, int selectedPosition) {
      emojiView.setText(emojiCount.getEmoji());
      countView.setText(String.valueOf(emojiCount.getCount()));
      itemView.setBackground(getAdapterPosition() == selectedPosition ? selected : null);
    }
  }

  interface OnViewHolderClickListener {
    void onClick(int position);
  }

  interface OnEmojiCountSelectedListener {
    void onSelected(@NonNull EmojiCount emojiCount);
  }

}
