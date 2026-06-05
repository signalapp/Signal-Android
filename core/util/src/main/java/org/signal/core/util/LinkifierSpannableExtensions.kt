/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.text.Spannable
import android.text.Spanned
import android.text.style.URLSpan
import org.signal.core.util.Linkifier.DetectedLink

/**
 * Detects web URLs in this [Spannable] and applies a span for each one.
 *
 * Overlapping spans are pruned using the same longest-wins rule. Equal-length overlapping spans are both kept.
 *
 * @param filter       caller-supplied filter; only links for which this returns `true` are added
 * @param spanFactory  builds the span to apply for each link; defaults to a plain [URLSpan]
 *
 * @return `true` if at least one span was added.
 */
@JvmOverloads
fun Spannable.addDetectedLinks(
  filter: (DetectedLink) -> Boolean = { true },
  spanFactory: (DetectedLink) -> Any = { URLSpan(it.url) }
): Boolean {
  val detected = Linkifier.findLinks(this).filter(filter)
  if (detected.isEmpty()) return false

  val regions = ArrayList<Region>(detected.size)
  for (span in getSpans(0, length, URLSpan::class.java)) {
    regions += Region(getSpanStart(span), getSpanEnd(span), existingSpan = span, detected = null)
  }
  for (link in detected) {
    regions += Region(link.start, link.end, existingSpan = null, detected = link)
  }

  // Sort by start ascending, then length descending — matches LinkifyCompat's COMPARATOR.
  regions.sortWith(compareBy({ it.start }, { -(it.end - it.start) }))

  var i = 0
  while (i < regions.size - 1) {
    val current = regions[i]
    val next = regions[i + 1]
    if (current.start <= next.start && current.end > next.start) {
      val currentLength = current.end - current.start
      val nextLength = next.end - next.start
      val remove: Int = when {
        next.end <= current.end -> i + 1
        currentLength > nextLength -> i + 1
        currentLength < nextLength -> i
        else -> -1
      }
      if (remove >= 0) {
        regions[remove].existingSpan?.let { removeSpan(it) }
        regions.removeAt(remove)
        continue
      }
    }
    i++
  }

  var added = false
  for (region in regions) {
    val link = region.detected ?: continue
    setSpan(spanFactory(link), link.start, link.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    added = true
  }
  return added
}

private data class Region(
  val start: Int,
  val end: Int,
  val existingSpan: URLSpan?,
  val detected: DetectedLink?
)
