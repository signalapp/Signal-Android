package org.thoughtcrime.securesms.components.mention;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Annotation;
import android.text.Layout;
import android.text.Spanned;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.DrawableUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * Encapsulates the logic for determining the type of mention rendering needed (single vs multi-line) and then
 * passing that information to the appropriate {@link MentionRenderer}.
 * <p></p>
 * Ported and modified from https://github.com/googlearchive/android-text/tree/master/RoundedBackground-Kotlin
 */
public class MentionRendererDelegate {

  private final MentionRenderer single;
  private final MentionRenderer multi;
  private final int             horizontalPadding;

  public MentionRendererDelegate(@NonNull Context context, @ColorInt int tint) {
    this.horizontalPadding = ViewUtil.dpToPx(2);

    Drawable drawable     = ContextCompat.getDrawable(context, R.drawable.mention_text_bg);
    Drawable drawableLeft = ContextCompat.getDrawable(context, R.drawable.mention_text_bg_left);
    Drawable drawableMid  = ContextCompat.getDrawable(context, R.drawable.mention_text_bg_mid);
    Drawable drawableEnd  = ContextCompat.getDrawable(context, R.drawable.mention_text_bg_right);

    //noinspection ConstantConditions
    single = new MentionRenderer.SingleLineMentionRenderer(horizontalPadding,
                                                           0,
                                                           DrawableUtil.tint(drawable, tint));
    //noinspection ConstantConditions
    multi = new MentionRenderer.MultiLineMentionRenderer(horizontalPadding,
                                                         0,
                                                         DrawableUtil.tint(drawableLeft, tint),
                                                         DrawableUtil.tint(drawableMid, tint),
                                                         DrawableUtil.tint(drawableEnd, tint));
  }

  public void draw(@NonNull Canvas canvas, @NonNull Spanned text, @NonNull Layout layout) {
    Annotation[] annotations = text.getSpans(0, text.length(), Annotation.class);
    for (Annotation annotation : annotations) {
      if (MentionAnnotation.isMentionAnnotation(annotation)) {
        int spanStart = text.getSpanStart(annotation);
        int spanEnd   = text.getSpanEnd(annotation);
        int startLine = layout.getLineForOffset(spanStart);
        int endLine   = layout.getLineForOffset(spanEnd);

        int startOffset = (int) (layout.getPrimaryHorizontal(spanStart) + -1 * layout.getParagraphDirection(startLine) * horizontalPadding);
        int endOffset   = (int) (layout.getPrimaryHorizontal(spanEnd) + layout.getParagraphDirection(endLine) * horizontalPadding);

        MentionRenderer renderer = (startLine == endLine) ? single : multi;
        renderer.draw(canvas, layout, startLine, endLine, startOffset, endOffset);
      }
    }
  }
}
