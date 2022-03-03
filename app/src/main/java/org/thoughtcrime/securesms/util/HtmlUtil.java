package org.thoughtcrime.securesms.util;

import android.text.Html;

import androidx.annotation.NonNull;

public class HtmlUtil {
  public static @NonNull String bold(@NonNull String target) {
    return "<b>" + Html.escapeHtml(target) + "</b>";
  }
}
