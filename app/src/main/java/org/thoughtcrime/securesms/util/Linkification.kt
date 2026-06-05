/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import android.text.Spannable
import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.core.text.util.LinkifyCompat
import org.signal.core.util.Linkifier
import org.signal.core.util.Linkifier.DetectedLink
import org.signal.core.util.addDetectedLinks

/**
 * Temporary abstraction while we switch over to the new [Linkifier] via remote config.
 * When the remote config is off, we fallback to the pre-existing android link logic.
 */
object Linkification {

  /**
   * Adds [URLSpan]s for web URLs in [spannable]. Returns `true` if at least one span was added.
   */
  @JvmStatic
  fun applyWebUrlSpans(spannable: Spannable): Boolean {
    return if (RemoteConfig.useNewLinkifier) {
      spannable.addDetectedLinks()
    } else {
      LinkifyCompat.addLinks(spannable, Linkify.WEB_URLS)
    }
  }

  /**
   * Finds web URLs in [text].
   */
  @JvmStatic
  fun findWebLinks(text: CharSequence): List<DetectedLink> {
    if (RemoteConfig.useNewLinkifier) {
      return Linkifier.findLinks(text)
    }

    val spannable = SpannableString(text)
    if (!LinkifyCompat.addLinks(spannable, Linkify.WEB_URLS)) {
      return emptyList()
    }

    return spannable.getSpans(0, spannable.length, URLSpan::class.java).map { span ->
      DetectedLink(
        start = spannable.getSpanStart(span),
        end = spannable.getSpanEnd(span),
        url = span.url
      )
    }
  }
}
