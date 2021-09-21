package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

import org.thoughtcrime.securesms.R;

public class EmojiImageView extends AppCompatImageView {
  public EmojiImageView(Context context) {
    super(context);
  }

  public EmojiImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setImageEmoji(CharSequence emoji) {
    if (isInEditMode()) {
      setImageResource(R.drawable.ic_emoji);
    } else {
      setImageDrawable(EmojiProvider.getEmojiDrawable(getContext(), emoji));
    }
  }
}
