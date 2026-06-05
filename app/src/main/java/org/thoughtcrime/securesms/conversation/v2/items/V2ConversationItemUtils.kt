/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.content.Context
import android.text.Spannable
import android.text.Spanned
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.core.text.util.LinkifyCompat
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.util.InterceptableLongClickCopyLinkSpan
import org.thoughtcrime.securesms.util.LinkUtil
import org.thoughtcrime.securesms.util.Linkification
import org.thoughtcrime.securesms.util.UrlClickHandler
import org.thoughtcrime.securesms.util.hasOnlyThumbnail

/**
 * Utilities for presenting the body of a conversation message.
 */
object V2ConversationItemUtils {

  fun MessageRecord.isThumbnailAtBottomOfBubble(context: Context): Boolean {
    return hasOnlyThumbnail(context) && isDisplayBodyEmpty(context)
  }

  @JvmStatic
  fun linkifyUrlLinks(messageBody: Spannable, shouldLinkifyAllLinks: Boolean, urlClickHandler: UrlClickHandler) {
    if (!shouldLinkifyAllLinks) {
      return
    }

    LinkifyCompat.addLinks(messageBody, Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS)
    Linkification.applyWebUrlSpans(messageBody)

    messageBody.getSpans(0, messageBody.length, URLSpan::class.java).forEach { urlSpan ->
      val url = urlSpan.url
      val start = messageBody.getSpanStart(urlSpan)
      val end = messageBody.getSpanEnd(urlSpan)
      messageBody.removeSpan(urlSpan)
      if (LinkUtil.isLegalUrl(url)) {
        messageBody.setSpan(InterceptableLongClickCopyLinkSpan(url, urlClickHandler), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
    }
  }
}
