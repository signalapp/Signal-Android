package org.thoughtcrime.securesms.components.emoji;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder;

public class EmojiPageViewGridAdapter extends MappingAdapter implements PopupWindow.OnDismissListener {

  private final VariationSelectorListener variationSelectorListener;

  public EmojiPageViewGridAdapter(@NonNull EmojiVariationSelectorPopup popup,
                                  @NonNull EmojiEventListener emojiEventListener,
                                  @NonNull VariationSelectorListener variationSelectorListener,
                                  boolean allowVariations,
                                  @LayoutRes int displayEmojiLayoutResId,
                                  @LayoutRes int displayEmoticonLayoutResId)
  {
    this.variationSelectorListener = variationSelectorListener;

    popup.setOnDismissListener(this);

    registerFactory(EmojiHeader.class, new LayoutFactory<>(EmojiHeaderViewHolder::new, R.layout.emoji_grid_header));
    registerFactory(EmojiModel.class, new LayoutFactory<>(v -> new EmojiViewHolder(v, emojiEventListener, variationSelectorListener, popup, allowVariations), displayEmojiLayoutResId));
    registerFactory(EmojiTextModel.class, new LayoutFactory<>(v -> new EmojiTextViewHolder(v, emojiEventListener), displayEmoticonLayoutResId));
    registerFactory(EmojiNoResultsModel.class, new LayoutFactory<>(MappingViewHolder.SimpleViewHolder::new, R.layout.emoji_grid_no_results));
  }

  @Override
  public void onDismiss() {
    variationSelectorListener.onVariationSelectorStateChanged(false);
  }

  public static class EmojiHeader implements MappingModel<EmojiHeader>, HasKey {

    private final String key;
    private final int    title;

    public EmojiHeader(@NonNull String key, int title) {
      this.key   = key;
      this.title = title;
    }

    @Override
    public @NonNull String getKey() {
      return key;
    }

    @Override
    public boolean areItemsTheSame(@NonNull EmojiHeader newItem) {
      return title == newItem.title;
    }

    @Override
    public boolean areContentsTheSame(@NonNull EmojiHeader newItem) {
      return areItemsTheSame(newItem);
    }
  }

  static class EmojiHeaderViewHolder extends MappingViewHolder<EmojiHeader> {

    private final TextView title;

    public EmojiHeaderViewHolder(@NonNull View itemView) {
      super(itemView);
      title = findViewById(R.id.emoji_grid_header_title);
    }

    @Override
    public void bind(@NonNull EmojiHeader model) {
      title.setText(model.title);
    }
  }

  public static class EmojiModel implements MappingModel<EmojiModel>, HasKey {

    private final String key;
    private final Emoji  emoji;

    public EmojiModel(@NonNull String key, @NonNull Emoji emoji) {
      this.key   = key;
      this.emoji = emoji;
    }

    @Override
    public @NonNull String getKey() {
      return key;
    }

    public @NonNull Emoji getEmoji() {
      return emoji;
    }

    @Override
    public boolean areItemsTheSame(@NonNull EmojiModel newItem) {
      return newItem.emoji.getValue().equals(emoji.getValue());
    }

    @Override
    public boolean areContentsTheSame(@NonNull EmojiModel newItem) {
      return areItemsTheSame(newItem);
    }
  }

  static class EmojiViewHolder extends MappingViewHolder<EmojiModel> {

    private final EmojiVariationSelectorPopup popup;
    private final VariationSelectorListener   variationSelectorListener;
    private final EmojiEventListener          emojiEventListener;
    private final boolean                     allowVariations;

    private final ImageView imageView;

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
    }

    @Override
    public void bind(@NonNull EmojiModel model) {
      final Drawable drawable = EmojiProvider.getEmojiDrawable(imageView.getContext(), model.emoji.getValue());

      imageView.setContentDescription(model.emoji.getValue());
      if (drawable != null) {
        imageView.setVisibility(View.VISIBLE);
        imageView.setImageDrawable(drawable);
      }

      itemView.setOnClickListener(v -> {
        emojiEventListener.onEmojiSelected(model.emoji.getValue());
      });

      if (allowVariations && model.emoji.hasMultipleVariations()) {
        itemView.setOnLongClickListener(v -> {
          popup.dismiss();
          popup.setVariations(model.emoji.getVariations());
          popup.showAsDropDown(itemView, 0, -(2 * itemView.getHeight()));
          variationSelectorListener.onVariationSelectorStateChanged(true);
          return true;
        });
      } else {
        itemView.setOnLongClickListener(null);
      }
    }
  }

  public static class EmojiTextModel implements MappingModel<EmojiTextModel>, HasKey {
    private final String key;
    private final Emoji  emoji;

    public EmojiTextModel(@NonNull String key, @NonNull Emoji emoji) {
      this.key   = key;
      this.emoji = emoji;
    }

    @Override
    public @NonNull String getKey() {
      return key;
    }

    public @NonNull Emoji getEmoji() {
      return emoji;
    }

    @Override
    public boolean areItemsTheSame(@NonNull EmojiTextModel newItem) {
      return newItem.emoji.getValue().equals(emoji.getValue());
    }

    @Override
    public boolean areContentsTheSame(@NonNull EmojiTextModel newItem) {
      return areItemsTheSame(newItem);
    }
  }

  static class EmojiTextViewHolder extends MappingViewHolder<EmojiTextModel> {

    private final EmojiEventListener emojiEventListener;
    private final AsciiEmojiView     textView;

    public EmojiTextViewHolder(@NonNull View itemView,
                               @NonNull EmojiEventListener emojiEventListener)
    {
      super(itemView);

      this.emojiEventListener = emojiEventListener;
      this.textView           = itemView.findViewById(R.id.emoji_text);
    }

    @Override
    public void bind(@NonNull EmojiTextModel model) {
      textView.setEmoji(model.emoji.getValue());

      itemView.setOnClickListener(v -> {
        emojiEventListener.onEmojiSelected(model.emoji.getValue());
      });
    }
  }

  public static class EmojiNoResultsModel implements MappingModel<EmojiNoResultsModel> {
    @Override
    public boolean areItemsTheSame(@NonNull EmojiNoResultsModel newItem) {
      return true;
    }

    @Override
    public boolean areContentsTheSame(@NonNull EmojiNoResultsModel newItem) {
      return true;
    }
  }

  public interface HasKey {
    @NonNull String getKey();
  }

  public interface VariationSelectorListener {
    void onVariationSelectorStateChanged(boolean open);
  }
}
