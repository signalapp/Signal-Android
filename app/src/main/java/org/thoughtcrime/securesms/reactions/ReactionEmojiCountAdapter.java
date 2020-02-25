package org.thoughtcrime.securesms.reactions;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.util.ThemeUtil;

import java.util.Collections;
import java.util.List;

final class ReactionEmojiCountAdapter extends RecyclerView.Adapter<ReactionEmojiCountAdapter.ViewHolder> {

  private List<EmojiCount> emojiCountList   = Collections.emptyList();
  private int              totalCount       = 0;
  private int              selectedPosition = -1;

  private final OnEmojiCountSelectedListener onEmojiCountSelectedListener;

  ReactionEmojiCountAdapter(@NonNull OnEmojiCountSelectedListener onEmojiCountSelectedListener) {
    this.onEmojiCountSelectedListener = onEmojiCountSelectedListener;
  }

  void updateData(@NonNull List<EmojiCount> newEmojiCount) {
    if (selectedPosition != -1 && selectedPosition != 0) {
      int        emojiPosition = selectedPosition - 1;
      EmojiCount oldSelection  = emojiCountList.get(emojiPosition);
      int        newPosition   = -1;

      for (int i = 0; i < newEmojiCount.size(); i++) {
        if (newEmojiCount.get(i).getEmoji().equals(oldSelection.getEmoji())) {
          newPosition = i;
          break;
        }
      }

      if (newPosition == -1 && !newEmojiCount.isEmpty()) {
        selectedPosition = 0;
        onEmojiCountSelectedListener.onSelected(null);
      } else {
        selectedPosition = newPosition + 1;
      }
    } else if (!newEmojiCount.isEmpty()) {
      selectedPosition = 0;
      onEmojiCountSelectedListener.onSelected(null);
    }

    this.emojiCountList = newEmojiCount;

    this.totalCount = Stream.of(emojiCountList).reduce(0, (sum, e) -> sum + e.getCount());

    notifyDataSetChanged();
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.reactions_bottom_sheet_dialog_fragment_emoji_item, parent, false), position -> {
      if (position != -1 && position != selectedPosition) {
        onEmojiCountSelectedListener.onSelected(position == 0 ? null : emojiCountList.get(position - 1).getEmoji());

        int oldPosition  = selectedPosition;
        selectedPosition = position;

        notifyItemChanged(oldPosition);
        notifyItemChanged(position);
      }
    });
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    if (position == 0) {
      holder.bind(null, totalCount, selectedPosition == position);
    } else {
      EmojiCount item = emojiCountList.get(position - 1);
      holder.bind(item.getEmoji(), item.getCount(), selectedPosition == position);
    }
  }

  @Override
  public int getItemCount() {
    return 1 + emojiCountList.size();
  }

  static final class ViewHolder extends RecyclerView.ViewHolder {

    private final Drawable      selectedBackground;
    private final EmojiTextView emojiView;
    private final TextView      countView;

    ViewHolder(@NonNull View itemView, @NonNull OnViewHolderClickListener onClickListener) {
      super(itemView);
      emojiView          = itemView.findViewById(R.id.reactions_bottom_view_emoji_item_emoji);
      countView          = itemView.findViewById(R.id.reactions_bottom_view_emoji_item_text );
      selectedBackground = ThemeUtil.getThemedDrawable(itemView.getContext(), R.attr.reactions_bottom_dialog_fragment_emoji_selected);

      itemView.setOnClickListener(v -> onClickListener.onClick(getAdapterPosition()));
    }

    void bind(@Nullable String emoji, int count, boolean selected) {
      if (emoji != null) {
        emojiView.setVisibility(View.VISIBLE);
        emojiView.setText(emoji);
        countView.setText(String.valueOf(count));
      } else {
        emojiView.setVisibility(View.GONE);
        countView.setText(itemView.getContext().getString(R.string.ReactionsBottomSheetDialogFragment_all, count));
      }
      itemView.setBackground(selected ? selectedBackground : null);
    }
  }

  interface OnViewHolderClickListener {
    void onClick(int position);
  }

  interface OnEmojiCountSelectedListener {
    void onSelected(@Nullable String emoji);
  }
}
