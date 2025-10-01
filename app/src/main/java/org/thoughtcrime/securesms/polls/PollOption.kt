package org.thoughtcrime.securesms.polls

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a poll option and a list of recipients who have voted for that option
 */
@Parcelize
data class PollOption(
  val id: Long,
  val text: String,
  val voterIds: List<Long>,
  val isSelected: Boolean = false,
  val isPending: Boolean = false
) : Parcelable
