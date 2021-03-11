package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BulletSpan;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;

public class SpanUtil {

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

  public static CharSequence clickSubstring(@NonNull Context context, @NonNull CharSequence fullString, @NonNull CharSequence substring, @NonNull View.OnClickListener clickListener) {
    ClickableSpan clickable = new ClickableSpan() {
      @Override
      public void updateDrawState(@NonNull TextPaint ds) {
        super.updateDrawState(ds);
        ds.setUnderlineText(false);
        ds.setColor(ContextCompat.getColor(context, R.color.signal_accent_primary));
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
}
