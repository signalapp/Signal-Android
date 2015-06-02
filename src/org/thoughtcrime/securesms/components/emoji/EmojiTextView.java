package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable.Callback;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;

public class EmojiTextView extends AppCompatTextView {
  private final Callback callback = new PostInvalidateCallback(this);

  public EmojiTextView(Context context) {
    super(context);
  }

  public EmojiTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public EmojiTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override public void setText(CharSequence text, BufferType type) {
    super.setText(EmojiProvider.getInstance(getContext()).emojify(text,
                                                                  EmojiProvider.EMOJI_SMALL,
                                                                  callback),
                  BufferType.SPANNABLE);
  }
}
