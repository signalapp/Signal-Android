package org.thoughtcrime.securesms.components.mention;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Annotation;
import android.text.Layout;
import android.text.Spanned;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.spoiler.SpoilerAnnotation;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.DrawableUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.List;

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
  private final Drawable        drawable;
  private final Drawable        drawableLeft;
  private final Drawable        drawableMid;
  private final Drawable        drawableEnd;

  public MentionRendererDelegate(@NonNull Context context, @ColorInt int tint) {
    this.horizontalPadding = ViewUtil.dpToPx(2);

    drawable     = DrawableUtil.tint(ContextUtil.requireDrawable(context, R.drawable.mention_text_bg), tint);
    drawableLeft = DrawableUtil.tint(ContextUtil.requireDrawable(context, R.drawable.mention_text_bg_left), tint);
    drawableMid  = DrawableUtil.tint(ContextUtil.requireDrawable(context, R.drawable.mention_text_bg_mid), tint);
    drawableEnd  = DrawableUtil.tint(ContextUtil.requireDrawable(context, R.drawable.mention_text_bg_right), tint);

    single = new MentionRenderer.SingleLineMentionRenderer(horizontalPadding,
                                                           0,
                                                           drawable);

    multi = new MentionRenderer.MultiLineMentionRenderer(horizontalPadding,
                                                         0,
                                                         drawableLeft,
                                                         drawableMid,
                                                         drawableEnd);
  }

  public void draw(@NonNull Canvas canvas, @NonNull Spanned text, @NonNull Layout layout) {
    Annotation[] annotations = text.getSpans(0, text.length(), Annotation.class);
    for (Annotation annotation : annotations) {
      if (MentionAnnotation.isMentionAnnotation(annotation)) {
        int spanStart = text.getSpanStart(annotation);
        int spanEnd   = text.getSpanEnd(annotation);

        List<Annotation> spoilerAnnotations = SpoilerAnnotation.getSpoilerAnnotations(text, spanStart, spanEnd, true);
        if (Util.hasItems(spoilerAnnotations)) {
          continue;
        }

        int startLine = layout.getLineForOffset(spanStart);
        int endLine   = layout.getLineForOffset(spanEnd);

        int startOffset = (int) (layout.getPrimaryHorizontal(spanStart) + -1 * layout.getParagraphDirection(startLine) * horizontalPadding);
        int endOffset   = (int) (layout.getPrimaryHorizontal(spanEnd) + layout.getParagraphDirection(endLine) * horizontalPadding);

        MentionRenderer renderer = (startLine == endLine) ? single : multi;
        renderer.draw(canvas, layout, startLine, endLine, startOffset, endOffset);
      }
    }
  }

  public void setTint(@ColorInt int tint) {
    DrawableCompat.setTint(drawable, tint);
    DrawableCompat.setTint(drawableLeft, tint);
    DrawableCompat.setTint(drawableMid, tint);
    DrawableCompat.setTint(drawableEnd, tint);
  }
}
