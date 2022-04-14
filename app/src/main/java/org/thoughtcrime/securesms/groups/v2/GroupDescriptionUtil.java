package org.thoughtcrime.securesms.groups.v2;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.util.LinkifyCompat;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.util.LinkUtil;
import org.thoughtcrime.securesms.util.LongClickCopySpan;

public final class GroupDescriptionUtil {

  /**
   * Set a group description.
   *
   * @param description   full description
   * @param emojiTextView Text view to update with description
   * @param linkify       flag indicating if web urls should be linkified
   * @param moreClick     Callback for when truncating and need to show more via another means. Required to enable truncating.
   */
  public static void setText(@NonNull Context context, @NonNull EmojiTextView emojiTextView, @NonNull String description, boolean linkify, @Nullable Runnable moreClick) {
    boolean         shouldEllipsize      = moreClick != null;
    String          scrubbedDescription  = shouldEllipsize ? description.replaceAll("\\n", " ") : description;
    SpannableString descriptionSpannable = new SpannableString(scrubbedDescription);

    if (linkify) {
      int     linkPattern = Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS;
      boolean hasLinks    = LinkifyCompat.addLinks(descriptionSpannable, linkPattern);

      if (hasLinks) {
        Stream.of(descriptionSpannable.getSpans(0, descriptionSpannable.length(), URLSpan.class))
              .filterNot(url -> LinkUtil.isLegalUrl(url.getURL()))
              .forEach(descriptionSpannable::removeSpan);

        URLSpan[] urlSpans = descriptionSpannable.getSpans(0, descriptionSpannable.length(), URLSpan.class);

        for (URLSpan urlSpan : urlSpans) {
          int     start = descriptionSpannable.getSpanStart(urlSpan);
          int     end   = descriptionSpannable.getSpanEnd(urlSpan);
          URLSpan span  = new LongClickCopySpan(urlSpan.getURL());
          descriptionSpannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }
    }

    if (shouldEllipsize) {
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

      emojiTextView.setEllipsize(TextUtils.TruncateAt.END);
      emojiTextView.setMaxLines(2);

      SpannableString overflowText = new SpannableString(context.getString(R.string.ManageGroupActivity_more));
      overflowText.setSpan(style, 0, overflowText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      emojiTextView.setOverflowText(overflowText);
    }

    emojiTextView.setText(descriptionSpannable);
  }
}
