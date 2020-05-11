package org.thoughtcrime.securesms.components.emoji;

import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider.EmojiEventListener;

import java.util.ArrayList;
import java.util.List;

public class EmojiPageViewGridAdapter extends RecyclerView.Adapter<EmojiPageViewGridAdapter.EmojiViewHolder> implements PopupWindow.OnDismissListener {

  private final List<Emoji>                 emojiList;
  private final EmojiProvider               emojiProvider;
  private final EmojiVariationSelectorPopup popup;
  private final VariationSelectorListener   variationSelectorListener;
  private final EmojiEventListener          emojiEventListener;

  public EmojiPageViewGridAdapter(@NonNull EmojiProvider emojiProvider,
                                  @NonNull EmojiVariationSelectorPopup popup,
                                  @NonNull EmojiEventListener emojiEventListener,
                                  @NonNull VariationSelectorListener variationSelectorListener)
  {
    this.emojiList                 = new ArrayList<>();
    this.emojiProvider             = emojiProvider;
    this.popup                     = popup;
    this.emojiEventListener        = emojiEventListener;
    this.variationSelectorListener = variationSelectorListener;

    popup.setOnDismissListener(this);
  }

  @NonNull
  @Override
  public EmojiViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new EmojiViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.emoji_display_item, viewGroup, false));
  }

  @Override
  public void onBindViewHolder(@NonNull EmojiViewHolder viewHolder, int i) {
    Emoji emoji = emojiList.get(i);

    Drawable drawable = emojiProvider.getEmojiDrawable(emoji.getValue());

    if (drawable != null) {
      viewHolder.textView.setVisibility(View.GONE);
      viewHolder.imageView.setVisibility(View.VISIBLE);

      viewHolder.imageView.setImageDrawable(drawable);
    } else {
      viewHolder.textView.setVisibility(View.VISIBLE);
      viewHolder.imageView.setVisibility(View.GONE);

      viewHolder.textView.setEmoji(emoji.getValue());
    }

    viewHolder.itemView.setOnClickListener(v -> {
      emojiEventListener.onEmojiSelected(emoji.getValue());
    });

    if (emoji.getVariations().size() > 1) {
      viewHolder.itemView.setOnLongClickListener(v -> {
        popup.dismiss();
        popup.setVariations(emoji.getVariations());
        popup.showAsDropDown(viewHolder.itemView, 0, -(2 * viewHolder.itemView.getHeight()));
        variationSelectorListener.onVariationSelectorStateChanged(true);
        return true;
      });
      viewHolder.hintCorner.setVisibility(View.VISIBLE);
    } else {
      viewHolder.itemView.setOnLongClickListener(null);
      viewHolder.hintCorner.setVisibility(View.GONE);
    }
  }

  @Override
  public int getItemCount() {
    return emojiList.size();
  }

  public void setEmoji(@NonNull List<Emoji> emojiList) {
    this.emojiList.clear();
    this.emojiList.addAll(emojiList);
    notifyDataSetChanged();
  }

  @Override
  public void onDismiss() {
    variationSelectorListener.onVariationSelectorStateChanged(false);
  }

  static class EmojiViewHolder extends RecyclerView.ViewHolder {

    private final ImageView      imageView;
    private final AsciiEmojiView textView;
    private final ImageView      hintCorner;

    public EmojiViewHolder(@NonNull View itemView) {
      super(itemView);
      this.imageView  = itemView.findViewById(R.id.emoji_image);
      this.textView   = itemView.findViewById(R.id.emoji_text);
      this.hintCorner = itemView.findViewById(R.id.emoji_variation_hint);
    }
  }

  public interface VariationSelectorListener {
    void onVariationSelectorStateChanged(boolean open);
  }
}
