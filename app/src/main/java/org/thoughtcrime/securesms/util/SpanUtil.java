package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.BulletSpan;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;

public final class SpanUtil {

  private SpanUtil() {}

  public static final String SPAN_PLACE_HOLDER = "<<<SPAN>>>";

  private final static Typeface MEDIUM_BOLD_TYPEFACE = Typeface.create("sans-serif-medium", Typeface.BOLD);
  private final static Typeface BOLD_TYPEFACE        = Typeface.create("sans-serif-medium", Typeface.NORMAL);
  private final static Typeface LIGHT_TYPEFACE       = Typeface.create("sans-serif", Typeface.NORMAL);

  public static CharSequence center(@NonNull CharSequence sequence) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  public static CharSequence textAppearance(@NonNull Context context, @StyleRes int textAppearance, @NonNull CharSequence sequence) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new TextAppearanceSpan(context, textAppearance), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  public static CharSequence italic(CharSequence sequence) {
    return italic(sequence, sequence.length());
  }

  public static CharSequence italic(CharSequence sequence, int length) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  public static CharSequence small(CharSequence sequence) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new RelativeSizeSpan(0.9f), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  public static CharSequence ofSize(CharSequence sequence, int size) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new AbsoluteSizeSpan(size, true), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  public static CharSequence bold(CharSequence sequence) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  public static CharSequence boldSubstring(CharSequence fullString, CharSequence substring) {
    SpannableString spannable = new SpannableString(fullString);
    int             start     = TextUtils.indexOf(fullString, substring);
    int             end       = start + substring.length();

    if (start >= 0 && end <= fullString.length()) {
      spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return spannable;
  }

  public static CharSequence color(int color, CharSequence sequence) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new ForegroundColorSpan(color), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  public static @NonNull CharSequence bullet(@NonNull CharSequence sequence) {
    return bullet(sequence, BulletSpan.STANDARD_GAP_WIDTH);
  }

  public static @NonNull CharSequence bullet(@NonNull CharSequence sequence, int gapWidth) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new BulletSpan(gapWidth), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  public static CharSequence buildImageSpan(@NonNull Drawable drawable) {
    SpannableString imageSpan = new SpannableString(" ");

    int flag = Build.VERSION.SDK_INT >= 29 ? DynamicDrawableSpan.ALIGN_CENTER : DynamicDrawableSpan.ALIGN_BASELINE;

    imageSpan.setSpan(new ImageSpan(drawable, flag), 0, imageSpan.length(), 0);

    return imageSpan;
  }

  public static CharSequence buildCenteredImageSpan(@NonNull Drawable drawable) {
    SpannableString imageSpan = new SpannableString(" ");
    imageSpan.setSpan(new CenteredImageSpan(drawable), 0, imageSpan.length(), 0);
    return imageSpan;
  }

  public static void appendCenteredImageSpan(@NonNull SpannableStringBuilder builder, @NonNull Drawable drawable, int width, int height) {
    drawable.setBounds(0, 0, ViewUtil.dpToPx(width), ViewUtil.dpToPx(height));
    builder.append(" ").append(SpanUtil.buildCenteredImageSpan(drawable));
  }

  public static CharSequence learnMore(@NonNull Context context,
                                       @ColorInt int color,
                                       @NonNull View.OnClickListener onLearnMoreClicked)
  {
    String learnMore = context.getString(R.string.LearnMoreTextView_learn_more);
    return clickSubstring(learnMore, learnMore, onLearnMoreClicked, color);
  }

  public static CharSequence readMore(@NonNull Context context,
                                      @ColorInt int color,
                                      @NonNull View.OnClickListener onLearnMoreClicked)
  {
    String readMore = context.getString(R.string.SpanUtil__read_more);
    return clickSubstring(readMore, readMore, onLearnMoreClicked, color);
  }

  public static CharSequence clickable(@NonNull CharSequence text,
                                       @ColorInt int color,
                                       @NonNull View.OnClickListener onLearnMoreClicked)
  {
    return clickSubstring(text, text, onLearnMoreClicked, color);
  }

  public static Spanned urlSubsequence(@NonNull CharSequence fullString, @NonNull CharSequence substring, @NonNull String url) {
    SpannableString spannable = new SpannableString(fullString);
    int             start     = TextUtils.indexOf(fullString, substring);
    int             end       = start + substring.length();

    if (start >= 0 && end <= fullString.length()) {
      spannable.setSpan(new URLSpan(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return spannable;
  }

  /**
   * Takes two resources:
   * - one resource that has a single string placeholder
   * - and another resource for a string you want to put in that placeholder with a click listener.
   *
   * Example:
   *
   * <string name="main_string">This is a %1$s string.</string>
   * <string name="clickable_string">clickable</string>
   *
   * -> This is a clickable string.
   * (where "clickable" is blue and will trigger the provided click listener when clicked)
   */
  public static Spannable clickSubstring(@NonNull Context context, @StringRes int mainString, @StringRes int clickableString, @NonNull View.OnClickListener clickListener) {
    String main      = context.getString(mainString, SPAN_PLACE_HOLDER);
    String clickable = context.getString(clickableString);

    int start = main.indexOf(SPAN_PLACE_HOLDER);
    int end   = start + SPAN_PLACE_HOLDER.length();

    Spannable spannable = new SpannableString(main.substring(0, start) + clickable + main.substring(end));

    spannable.setSpan(new ClickableSpan() {
      @Override
      public void onClick(@NonNull View widget) {
        clickListener.onClick(widget);
      }

      @Override
      public void updateDrawState(@NonNull TextPaint ds) {
        super.updateDrawState(ds);
        ds.setUnderlineText(false);
      }
    }, start, start + clickable.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    return spannable;
  }

  public static CharSequence clickSubstring(@NonNull Context context,
                                            @NonNull CharSequence fullString,
                                            @NonNull CharSequence substring,
                                            @NonNull View.OnClickListener clickListener) {
    return clickSubstring(fullString,
                          substring,
                          clickListener,
                          ContextCompat.getColor(context, R.color.signal_accent_primary));
  }

  public static CharSequence clickSubstring(@NonNull CharSequence fullString,
                                            @NonNull CharSequence substring,
                                            @NonNull View.OnClickListener clickListener,
                                            @ColorInt int linkColor)
  {
    ClickableSpan clickable = new ClickableSpan() {
      @Override
      public void updateDrawState(@NonNull TextPaint ds) {
        super.updateDrawState(ds);
        ds.setUnderlineText(false);
        ds.setColor(linkColor);
      }

      @Override
      public void onClick(@NonNull View widget) {
        clickListener.onClick(widget);
      }
    };

    SpannableString spannable = new SpannableString(fullString);
    int             start     = TextUtils.indexOf(fullString, substring);
    int             end       = start + substring.length();

    if (start >= 0 && end <= fullString.length()) {
      spannable.setSpan(clickable, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    return spannable;
  }

  public static @NonNull CharSequence insertSingleSpan(@NonNull Resources resources, @StringRes int res, @NonNull CharSequence span) {
    return replacePlaceHolder(resources.getString(res, SPAN_PLACE_HOLDER), span);
  }

  public static CharSequence replacePlaceHolder(@NonNull String string, @NonNull CharSequence span) {
    int index = string.indexOf(SpanUtil.SPAN_PLACE_HOLDER);
    if (index == -1) {
      return string;
    }
    SpannableStringBuilder builder = new SpannableStringBuilder(string);
    builder.replace(index, index + SpanUtil.SPAN_PLACE_HOLDER.length(), span);
    return builder;
  }

  public static CharacterStyle getMediumBoldSpan() {
    if (Build.VERSION.SDK_INT >= 28) {
      return new TypefaceSpan(MEDIUM_BOLD_TYPEFACE);
    } else {
      return new StyleSpan(Typeface.BOLD);
    }
  }

  public static CharacterStyle getBoldSpan() {
    if (Build.VERSION.SDK_INT >= 28) {
      return new TypefaceSpan(BOLD_TYPEFACE);
    } else {
      return new StyleSpan(Typeface.BOLD);
    }
  }

  public static CharacterStyle getNormalSpan() {
    if (Build.VERSION.SDK_INT >= 28) {
      return new TypefaceSpan(LIGHT_TYPEFACE);
    } else {
      return new StyleSpan(Typeface.NORMAL);
    }
  }
}
