package org.thoughtcrime.securesms.database.model

data class LogEntry(
  val createdAt: Long,
  val keepLonger: Boolean,
  val body: String
)
