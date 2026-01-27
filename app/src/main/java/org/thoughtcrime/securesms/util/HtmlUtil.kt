package org.thoughtcrime.securesms.util

import android.text.Html

object HtmlUtil {
  @JvmStatic
  fun bold(target: String): String {
    return "<b>${Html.escapeHtml(target)}</b>"
  }
}
