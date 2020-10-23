package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;

import androidx.annotation.DrawableRes;
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

  public static CharSequence color(int color, CharSequence sequence) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new ForegroundColorSpan(color), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  public static CharSequence buildImageSpan(@NonNull Drawable drawable) {
    SpannableString imageSpan = new SpannableString(" ");

    int flag = Build.VERSION.SDK_INT >= 29 ? DynamicDrawableSpan.ALIGN_CENTER : DynamicDrawableSpan.ALIGN_BASELINE;

    imageSpan.setSpan(new ImageSpan(drawable, flag), 0, imageSpan.length(), 0);

    return imageSpan;
  }
}
