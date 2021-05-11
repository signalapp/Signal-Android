package org.thoughtcrime.securesms.groups.v2;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil;

public final class GroupDescriptionUtil {

  public static final int MAX_DESCRIPTION_LENGTH = 80;

  /**
   * Style a group description.
   *
   * @param description full description
   * @param linkify     flag indicating if web urls should be linkified
   * @param moreClick   Callback for when truncating and need to show more via another means. Required to enable truncating.
   * @return styled group description
   */
  public static @NonNull Spannable style(@NonNull Context context, @NonNull String description, boolean linkify, @Nullable Runnable moreClick) {
    SpannableString descriptionSpannable = new SpannableString(description);

    if (linkify) {
      int     linkPattern = Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS;
      boolean hasLinks    = Linkify.addLinks(descriptionSpannable, linkPattern);

      if (hasLinks) {
        Stream.of(descriptionSpannable.getSpans(0, descriptionSpannable.length(), URLSpan.class))
              .filterNot(url -> LinkPreviewUtil.isLegalUrl(url.getURL()))
              .forEach(descriptionSpannable::removeSpan);
      }
    }

    if (moreClick != null && descriptionSpannable.length() > MAX_DESCRIPTION_LENGTH) {
      ClickableSpan style = new ClickableSpan() {
        @Override
        public void onClick(@NonNull View widget) {
          moreClick.run();
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
          ds.setTypeface(Typeface.DEFAULT_BOLD);
        }
      };

      SpannableStringBuilder builder = new SpannableStringBuilder(descriptionSpannable.subSequence(0, MAX_DESCRIPTION_LENGTH)).append(context.getString(R.string.ManageGroupActivity_more));
      builder.setSpan(style, MAX_DESCRIPTION_LENGTH + 1, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      return builder;
    }

    return descriptionSpannable;
  }
}
