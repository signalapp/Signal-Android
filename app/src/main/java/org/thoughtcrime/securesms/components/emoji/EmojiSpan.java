package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;

public class EmojiSpan extends AnimatingImageSpan {

  private final float SHIFT_FACTOR = 1.5f;

  private int            size;
  private FontMetricsInt fontMetrics;

  public EmojiSpan(@NonNull Drawable drawable, @NonNull TextView tv) {
    super(drawable, tv);
    fontMetrics = tv.getPaint().getFontMetricsInt();
    size        = fontMetrics != null ? Math.abs(fontMetrics.descent) + Math.abs(fontMetrics.ascent)
                                      : tv.getResources().getDimensionPixelSize(R.dimen.conversation_item_body_text_size);
    getDrawable().setBounds(0, 0, size, size);
  }

  public EmojiSpan(@NonNull Context context, @NonNull Drawable drawable, @NonNull Paint paint) {
    super(drawable, null);
    fontMetrics = paint.getFontMetricsInt();
    size        = fontMetrics != null ? Math.abs(fontMetrics.descent) + Math.abs(fontMetrics.ascent)
                                      : context.getResources().getDimensionPixelSize(R.dimen.conversation_item_body_text_size);

    getDrawable().setBounds(0, 0, size, size);
  }

  @Override
  public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, FontMetricsInt fm) {
    if (fm != null && this.fontMetrics != null) {
      fm.ascent  = this.fontMetrics.ascent;
      fm.descent = this.fontMetrics.descent;
      fm.top     = this.fontMetrics.top;
      fm.bottom  = this.fontMetrics.bottom;
      fm.leading = this.fontMetrics.leading;
    } else {
      this.fontMetrics = paint.getFontMetricsInt();
      this.size        = Math.abs(this.fontMetrics.descent) + Math.abs(this.fontMetrics.ascent);

      getDrawable().setBounds(0, 0, size, size);
    }

    return size;
  }

  @Override
  public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
    if (paint.getColor() == Color.TRANSPARENT) {
      return;
    }

    int height          = bottom - top;
    int centeringMargin = (height - size) / 2;
    int adjustedMargin  = (int) (centeringMargin * SHIFT_FACTOR);
    int adjustedBottom  = bottom - adjustedMargin;
    super.draw(canvas, text, start, end, x, top, y, bottom - adjustedMargin, paint);
  }
}
