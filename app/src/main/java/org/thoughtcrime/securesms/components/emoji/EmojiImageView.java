package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import network.loki.messenger.R;

public class EmojiImageView extends AppCompatImageView {

  private final boolean forceJumboEmoji;

  public EmojiImageView(Context context) {
    this(context, null);
  }

  public EmojiImageView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public EmojiImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.EmojiImageView, 0, 0);
    forceJumboEmoji = a.getBoolean(R.styleable.EmojiImageView_forceJumbo, false);
    a.recycle();
  }

  public void setImageEmoji(CharSequence emoji) {
    if (isInEditMode()) {
      setImageResource(R.drawable.ic_emoji);
    } else {
      Drawable emojiDrawable = EmojiProvider.getEmojiDrawable(getContext(), emoji);
      if (emojiDrawable == null) {
        // fallback
        setImageResource(R.drawable.ic_outline_disabled_by_default_24);
      } else {
        setImageDrawable(emojiDrawable);
      }
    }
  }
}
