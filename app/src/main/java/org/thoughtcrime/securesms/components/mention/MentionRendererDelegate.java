package org.thoughtcrime.securesms.components.mention;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Annotation;
import android.text.Layout;
import android.text.Spanned;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.DrawableUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;
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

  public MentionRendererDelegate(@NonNull Context context) {
    //noinspection ConstantConditions
    this(ViewUtil.dpToPx(2),
         ViewUtil.dpToPx(2),
         ContextCompat.getDrawable(context, R.drawable.mention_text_bg),
         ContextCompat.getDrawable(context, R.drawable.mention_text_bg_left),
         ContextCompat.getDrawable(context, R.drawable.mention_text_bg_mid),
         ContextCompat.getDrawable(context, R.drawable.mention_text_bg_right),
         ThemeUtil.getThemedColor(context, R.attr.conversation_mention_background_color));
  }

  public MentionRendererDelegate(int horizontalPadding,
                                 int verticalPadding,
                                 @NonNull Drawable drawable,
                                 @NonNull Drawable drawableLeft,
                                 @NonNull Drawable drawableMid,
                                 @NonNull Drawable drawableEnd,
                                 @ColorInt int tint)
  {
    this.horizontalPadding = horizontalPadding;
    single                 = new MentionRenderer.SingleLineMentionRenderer(horizontalPadding,
                                                                           verticalPadding,
                                                                           DrawableUtil.tint(drawable, tint));
    multi                  = new MentionRenderer.MultiLineMentionRenderer(horizontalPadding,
                                                                          verticalPadding,
                                                                          DrawableUtil.tint(drawableLeft, tint),
                                                                          DrawableUtil.tint(drawableMid, tint),
                                                                          DrawableUtil.tint(drawableEnd, tint));
  }

  public void draw(@NonNull Canvas canvas, @NonNull Spanned text, @NonNull Layout layout) {
    Annotation[] spans = text.getSpans(0, text.length(), Annotation.class);
    for (Annotation span : spans) {
      if (MentionAnnotation.MENTION_ANNOTATION.equals(span.getKey())) {
        int spanStart = text.getSpanStart(span);
        int spanEnd   = text.getSpanEnd(span);
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
