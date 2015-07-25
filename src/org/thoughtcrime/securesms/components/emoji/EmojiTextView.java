package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatTextView;
import android.text.InputFilter;
import android.util.AttributeSet;

import org.thoughtcrime.securesms.components.emoji.EmojiProvider.EmojiDrawable;

public class EmojiTextView extends AppCompatTextView {

  public EmojiTextView(Context context) {
    this(context, null);
  }

  public EmojiTextView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public EmojiTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setFilters(new InputFilter[]{ new EmojiFilter(this) });
  }

  @Override public void invalidateDrawable(@NonNull Drawable drawable) {
    if (drawable instanceof EmojiDrawable) invalidate();
    else                                   super.invalidateDrawable(drawable);
  }
}
