package org.thoughtcrime.securesms.database.model

data class LogEntry(
  val createdAt: Long,
  val lifespan: Long,
  val body: String
)
