package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;

import java.util.List;

public class EmojiVariationSelectorPopup extends PopupWindow {

  private final Context            context;
  private final ViewGroup          list;
  private final EmojiEventListener listener;

  public EmojiVariationSelectorPopup(@NonNull Context context, @NonNull EmojiEventListener listener) {
    super(LayoutInflater.from(context).inflate(R.layout.emoji_variation_selector, null),
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT);
    this.context  = context;
    this.listener = listener;
    this.list     = (ViewGroup) getContentView().findViewById(R.id.emoji_variation_container);

    setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.emoji_variation_selector_background));
    setOutsideTouchable(true);

    setElevation(20);
  }

  public void setVariations(List<String> variations) {
    list.removeAllViews();

    for (String variation : variations) {
      ImageView imageView = (ImageView) LayoutInflater.from(context).inflate(R.layout.emoji_variation_selector_item, list, false);
      imageView.setImageDrawable(EmojiProvider.getEmojiDrawable(context, variation));
      imageView.setOnClickListener(v -> {
        listener.onEmojiSelected(variation);
        dismiss();
      });
      list.addView(imageView);
    }
  }
}
