package org.thoughtcrime.securesms.components.emoji;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;

public class EmojiSpan extends AnimatingImageSpan {

  private final float SHIFT_FACTOR = 1.5f;

  private final int            size;
  private final FontMetricsInt fm;

  public EmojiSpan(@NonNull Drawable drawable, @NonNull TextView tv) {
    super(drawable, tv);
    fm   = tv.getPaint().getFontMetricsInt();
    size = fm != null ? Math.abs(fm.descent) + Math.abs(fm.ascent)
                      : tv.getResources().getDimensionPixelSize(R.dimen.conversation_item_body_text_size);
    getDrawable().setBounds(0, 0, size, size);
  }

  @Override
  public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, FontMetricsInt fm) {
    if (fm != null && this.fm != null) {
      fm.ascent  = this.fm.ascent;
      fm.descent = this.fm.descent;
      fm.top     = this.fm.top;
      fm.bottom  = this.fm.bottom;
      fm.leading = this.fm.leading;
      return size;
    } else {
      return super.getSize(paint, text, start, end, fm);
    }
  }

  @Override
  public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
    int height          = bottom - top;
    int centeringMargin = (height - size) / 2;
    int adjustedMargin  = (int) (centeringMargin * SHIFT_FACTOR);
    super.draw(canvas, text, start, end, x, top, y, bottom - adjustedMargin, paint);
  }
}
