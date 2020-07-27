package org.thoughtcrime.securesms.components.mention;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Layout;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.LayoutUtil;

/**
 * Handles actually drawing the mention backgrounds for a TextView.
 * <p>
 * Ported and modified from https://github.com/googlearchive/android-text/tree/master/RoundedBackground-Kotlin
 */
public abstract class MentionRenderer {

  protected final int horizontalPadding;
  protected final int verticalPadding;

  public MentionRenderer(int horizontalPadding, int verticalPadding) {
    this.horizontalPadding = horizontalPadding;
    this.verticalPadding   = verticalPadding;
  }

  public abstract void draw(@NonNull Canvas canvas, @NonNull Layout layout, int startLine, int endLine, int startOffset, int endOffset);

  protected int getLineTop(@NonNull Layout layout, int line) {
    return LayoutUtil.getLineTopWithoutPadding(layout, line) - verticalPadding;
  }

  protected int getLineBottom(@NonNull Layout layout, int line) {
    return LayoutUtil.getLineBottomWithoutPadding(layout, line) + verticalPadding;
  }

  public static final class SingleLineMentionRenderer extends MentionRenderer {

    private final Drawable drawable;

    public SingleLineMentionRenderer(int horizontalPadding, int verticalPadding, @NonNull Drawable drawable) {
      super(horizontalPadding, verticalPadding);
      this.drawable = drawable;
    }

    @Override
    public void draw(@NonNull Canvas canvas, @NonNull Layout layout, int startLine, int endLine, int startOffset, int endOffset) {
      int lineTop    = getLineTop(layout, startLine);
      int lineBottom = getLineBottom(layout, startLine);
      int left       = Math.min(startOffset, endOffset);
      int right      = Math.max(startOffset, endOffset);

      drawable.setBounds(left, lineTop, right, lineBottom);
      drawable.draw(canvas);
    }
  }

  public static final class MultiLineMentionRenderer extends MentionRenderer {

    private final Drawable drawableLeft;
    private final Drawable drawableMid;
    private final Drawable drawableRight;

    public MultiLineMentionRenderer(int horizontalPadding, int verticalPadding,
                                    @NonNull Drawable drawableLeft,
                                    @NonNull Drawable drawableMid,
                                    @NonNull Drawable drawableRight)
    {
      super(horizontalPadding, verticalPadding);
      this.drawableLeft  = drawableLeft;
      this.drawableMid   = drawableMid;
      this.drawableRight = drawableRight;
    }

    @Override
    public void draw(@NonNull Canvas canvas, @NonNull Layout layout, int startLine, int endLine, int startOffset, int endOffset) {
      int paragraphDirection = layout.getParagraphDirection(startLine);

      float lineEndOffset;
      if (paragraphDirection == Layout.DIR_RIGHT_TO_LEFT) {
        lineEndOffset = layout.getLineLeft(startLine) - horizontalPadding;
      } else {
        lineEndOffset = layout.getLineRight(startLine) + horizontalPadding;
      }

      int lineBottom = getLineBottom(layout, startLine);
      int lineTop    = getLineTop(layout, startLine);
      drawStart(canvas, startOffset, lineTop, (int) lineEndOffset, lineBottom);

      for (int line = startLine + 1; line < endLine; line++) {
        int left  = (int) layout.getLineLeft(line) - horizontalPadding;
        int right = (int) layout.getLineRight(line) + horizontalPadding;

        lineTop    = getLineTop(layout, line);
        lineBottom = getLineBottom(layout, line);

        drawableMid.setBounds(left, lineTop, right, lineBottom);
        drawableMid.draw(canvas);
      }

      float lineStartOffset;
      if (paragraphDirection == Layout.DIR_RIGHT_TO_LEFT) {
        lineStartOffset = layout.getLineRight(startLine) + horizontalPadding;
      } else {
        lineStartOffset = layout.getLineLeft(startLine) - horizontalPadding;
      }

      lineBottom = getLineBottom(layout, endLine);
      lineTop    = getLineTop(layout, endLine);

      drawEnd(canvas, (int) lineStartOffset, lineTop, endOffset, lineBottom);
    }

    private void drawStart(@NonNull Canvas canvas, int start, int top, int end, int bottom) {
      if (start > end) {
        drawableRight.setBounds(end, top, start, bottom);
        drawableRight.draw(canvas);
      } else {
        drawableLeft.setBounds(start, top, end, bottom);
        drawableLeft.draw(canvas);
      }
    }

    private void drawEnd(@NonNull Canvas canvas, int start, int top, int end, int bottom) {
      if (start > end) {
        drawableLeft.setBounds(end, top, start, bottom);
        drawableLeft.draw(canvas);
      } else {
        drawableRight.setBounds(start, top, end, bottom);
        drawableRight.draw(canvas);
      }
    }
  }
}
