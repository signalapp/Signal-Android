package org.thoughtcrime.securesms.database.model

/**
 * Represents a pair of values that can be used to find a message. Because we have two tables,
 * that means this has both the primary key and a boolean indicating which table it's in.
 */
data class MessageId(
  val id: Long,
  @get:JvmName("isMms") val mms: Boolean
)
