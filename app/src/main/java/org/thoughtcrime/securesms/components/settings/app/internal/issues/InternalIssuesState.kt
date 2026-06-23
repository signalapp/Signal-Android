/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.issues

import org.thoughtcrime.securesms.database.LogDatabase.IssueTable.IssueRecord
import org.thoughtcrime.securesms.database.model.IssuePriority

data class InternalIssuesState(
  val loading: Boolean = true,
  val issues: List<IssueRecord> = emptyList(),
  val names: List<String> = emptyList(),
  val nameFilter: String? = null,
  val sortOrder: IssueSortOrder = IssueSortOrder.CREATED_DESC,
  val expandedIds: Set<Long> = emptySet(),
  val notificationPriority: IssuePriority = IssuePriority.HIGH
)

enum class IssueSortOrder(val label: String) {
  CREATED_DESC("Newest first"),
  CREATED_ASC("Oldest first"),
  DURATION_DESC("Longest duration"),
  DURATION_ASC("Shortest duration"),
  PRIORITY_DESC("Highest priority"),
  PRIORITY_ASC("Lowest priority")
}

sealed interface InternalIssuesScreenEvent {
  data object Load : InternalIssuesScreenEvent
  data object ClearAll : InternalIssuesScreenEvent
  data class ToggleExpanded(val id: Long) : InternalIssuesScreenEvent
  data class SetNotificationPriority(val priority: IssuePriority) : InternalIssuesScreenEvent
  data class SetNameFilter(val name: String?) : InternalIssuesScreenEvent
  data class SetSortOrder(val order: IssueSortOrder) : InternalIssuesScreenEvent
}
