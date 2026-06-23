package org.thoughtcrime.securesms.database.model

data class IssueEntry(
  val createdAt: Long,
  val version: String,
  val name: String,
  val description: String,
  val stackTrace: String?,
  val priority: IssuePriority,
  val duration: Long?
)
