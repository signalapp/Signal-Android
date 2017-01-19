package org.thoughtcrime.securesms.util;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

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
}
