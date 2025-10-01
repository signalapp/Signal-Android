package org.thoughtcrime.securesms.polls

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data class representing a poll entry in the db, its options, and any voting
 */
@Parcelize
data class PollRecord(
  val id: Long,
  val question: String,
  val pollOptions: List<PollOption>,
  val allowMultipleVotes: Boolean,
  val hasEnded: Boolean,
  val authorId: Long,
  val messageId: Long
) : Parcelable
