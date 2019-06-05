package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ResUtil;

public class AsciiEmojiView extends View {

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

  private String emoji;

  public AsciiEmojiView(Context context) {
    super(context);
  }

  public AsciiEmojiView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public void setEmoji(String emoji) {
    this.emoji = emoji;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (TextUtils.isEmpty(emoji)) {
      return;
    }

    float targetFontSize = 0.75f * getHeight() - getPaddingTop() - getPaddingBottom();

    paint.setTextSize(targetFontSize);
    paint.setColor(ResUtil.getColor(getContext(), R.attr.emoji_text_color));
    paint.setTextAlign(Paint.Align.CENTER);

    int xPos = (getWidth() / 2);
    int yPos = (int) ((getHeight() / 2) - ((paint.descent() + paint.ascent()) / 2));

    float overflow = paint.measureText(emoji) / (getWidth() - getPaddingLeft() - getPaddingRight());
    if (overflow > 1f) {
      paint.setTextSize(targetFontSize / overflow);
      yPos = (int) ((canvas.getHeight() / 2) - ((paint.descent() + paint.ascent()) / 2));
    }
    canvas.drawText(emoji, xPos, yPos, paint);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, widthMeasureSpec);
  }
}
