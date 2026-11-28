/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaoverview

/**
 * One entry in a tab's paged list: a section header, or a piece of media.
 */
sealed interface MediaOverviewRow {

  /** Starts a section. [firstItemKey] is the item right after it, which keeps header keys unique. */
  data class Header(
    val header: MediaOverviewState.SectionHeader,
    val firstItemKey: MediaOverviewItem.Key
  ) : MediaOverviewRow

  data class Media(val item: MediaOverviewItem) : MediaOverviewRow
}
