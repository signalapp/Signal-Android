package org.thoughtcrime.securesms.components.emoji;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.Space;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider.EmojiEventListener;
import org.thoughtcrime.securesms.keyboard.emoji.KeyboardPageSearchView;
import org.thoughtcrime.securesms.util.MappingAdapter;
import org.thoughtcrime.securesms.util.MappingModel;
import org.thoughtcrime.securesms.util.MappingViewHolder;

public class EmojiPageViewGridAdapter extends MappingAdapter implements PopupWindow.OnDismissListener {

  private final VariationSelectorListener   variationSelectorListener;

  public EmojiPageViewGridAdapter(@NonNull EmojiVariationSelectorPopup popup,
                                  @NonNull EmojiEventListener emojiEventListener,
                                  @NonNull VariationSelectorListener variationSelectorListener,
                                  boolean allowVariations,
                                  @LayoutRes int displayItemLayoutResId)
  {
    this.variationSelectorListener = variationSelectorListener;

    popup.setOnDismissListener(this);

    registerFactory(EmojiModel.class, new LayoutFactory<>(v -> new EmojiViewHolder(v, emojiEventListener, variationSelectorListener, popup, allowVariations), displayItemLayoutResId));
  }

  @Override
  public void onDismiss() {
    variationSelectorListener.onVariationSelectorStateChanged(false);
  }

  static class EmojiModel implements MappingModel<EmojiModel> {

    private final Emoji emoji;

    EmojiModel(@NonNull Emoji emoji) {
      this.emoji = emoji;
    }

    @Override
    public boolean areItemsTheSame(@NonNull @NotNull EmojiModel newItem) {
      return newItem.emoji.getValue().equals(emoji.getValue());
    }

    @Override
    public boolean areContentsTheSame(@NonNull @NotNull EmojiModel newItem) {
      return areItemsTheSame(newItem);
    }
  }

  static class EmojiViewHolder extends MappingViewHolder<EmojiModel> {

    private final EmojiVariationSelectorPopup popup;
    private final VariationSelectorListener   variationSelectorListener;
    private final EmojiEventListener          emojiEventListener;
    private final boolean                     allowVariations;

    private final ImageView      imageView;
    private final AsciiEmojiView textView;
    private final ImageView      hintCorner;

    public EmojiViewHolder(@NonNull View itemView,
                           @NonNull EmojiEventListener emojiEventListener,
                           @NonNull VariationSelectorListener variationSelectorListener,
                           @NonNull EmojiVariationSelectorPopup popup,
                           boolean allowVariations)
    {
      super(itemView);

      this.popup                     = popup;
      this.variationSelectorListener = variationSelectorListener;
      this.emojiEventListener        = emojiEventListener;
      this.allowVariations           = allowVariations;

      this.imageView  = itemView.findViewById(R.id.emoji_image);
      this.textView   = itemView.findViewById(R.id.emoji_text);
      this.hintCorner = itemView.findViewById(R.id.emoji_variation_hint);
    }

    @Override
    public void bind(@NonNull @NotNull EmojiModel model) {
      final Drawable drawable = EmojiProvider.getEmojiDrawable(imageView.getContext(), model.emoji.getValue());

      if (drawable != null) {
        textView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);

        imageView.setImageDrawable(drawable);
      } else {
        textView.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);

        textView.setEmoji(model.emoji.getValue());
      }

      itemView.setOnClickListener(v -> {
        emojiEventListener.onEmojiSelected(model.emoji.getValue());
      });

      if (allowVariations && model.emoji.getVariations().size() > 1) {
        itemView.setOnLongClickListener(v -> {
          popup.dismiss();
          popup.setVariations(model.emoji.getVariations());
          popup.showAsDropDown(itemView, 0, -(2 * itemView.getHeight()));
          variationSelectorListener.onVariationSelectorStateChanged(true);
          return true;
        });
        hintCorner.setVisibility(View.VISIBLE);
      } else {
        itemView.setOnLongClickListener(null);
        hintCorner.setVisibility(View.GONE);
      }
    }
  }

  public interface VariationSelectorListener {
    void onVariationSelectorStateChanged(boolean open);
  }
}
