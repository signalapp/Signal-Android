package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

public class EmojiImageView extends AppCompatImageView {
  public EmojiImageView(Context context) {
    super(context);
  }

  public EmojiImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setImageEmoji(CharSequence emoji) {
    setImageDrawable(EmojiProvider.getInstance(getContext()).getEmojiDrawable(emoji));
  }
}
