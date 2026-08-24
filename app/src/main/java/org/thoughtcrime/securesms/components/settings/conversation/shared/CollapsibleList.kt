/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

/**
 * The lists on the settings screens that start out truncated behind a "see all" row -- group members, and groups in
 * common.
 *
 * We only collapse past [COLLAPSE_THRESHOLD] so that hiding a single entry is never the reason for the extra tap.
 */
object CollapsibleList {
  private const val COLLAPSE_THRESHOLD = 6
  private const val COLLAPSED_COUNT = 5

  fun canExpand(all: List<*>, expanded: Boolean): Boolean = !expanded && all.size > COLLAPSE_THRESHOLD

  fun <T> collapse(all: List<T>, expanded: Boolean): List<T> {
    return if (canExpand(all, expanded)) all.take(COLLAPSED_COUNT) else all
  }
}
