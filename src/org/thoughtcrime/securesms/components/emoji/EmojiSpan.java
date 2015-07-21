package org.thoughtcrime.securesms.components.emoji;

import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;

public class EmojiSpan extends AnimatingImageSpan {
  public EmojiSpan(@NonNull Drawable drawable, @NonNull TextView tv) {
    super(drawable, tv);
    FontMetricsInt fm = tv.getPaint().getFontMetricsInt();
    final int size = fm != null ? Math.abs(fm.descent) + Math.abs(fm.ascent)
                                : tv.getResources().getDimensionPixelSize(R.dimen.conversation_item_body_text_size);
    getDrawable().setBounds(0, 0, size, size);
  }

  @Override public int getSize(Paint paint, CharSequence text, int start, int end,
                               FontMetricsInt fm)
  {
    return getDrawable().getBounds().right;
  }
}
