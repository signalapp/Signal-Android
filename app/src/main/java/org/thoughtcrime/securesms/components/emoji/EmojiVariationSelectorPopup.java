package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider.EmojiEventListener;

import java.util.List;

import network.loki.messenger.R;

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
    this.list     = (ViewGroup) getContentView();

    setBackgroundDrawable(null);
    setOutsideTouchable(true);
  }

  public void setVariations(List<String> variations) {
    list.removeAllViews();

    for (String variation : variations) {
      ImageView imageView = (ImageView) LayoutInflater.from(context).inflate(R.layout.emoji_variation_selector_item, list, false);
      imageView.setImageDrawable(EmojiProvider.getInstance(context).getEmojiDrawable(variation));
      imageView.setOnClickListener(v -> {
        listener.onEmojiSelected(variation);
        dismiss();
      });
      list.addView(imageView);
    }
  }
}
