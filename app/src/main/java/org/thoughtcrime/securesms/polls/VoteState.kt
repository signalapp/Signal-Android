package org.thoughtcrime.securesms.polls

/**
 * Tracks general state information when a user votes in a poll. Vote states are specific to an option in a poll
 * eg. in a poll with three options, each option can have a different states like (PENDING_ADD, ADDED, NONE)
 */
enum class VoteState(val value: Int) {

  /** We have no information on the vote state */
  NONE(0),

  /** Vote is in the process of being removed */
  PENDING_REMOVE(1),

  /** Vote is in the process of being added */
  PENDING_ADD(2),

  /** Vote was removed */
  REMOVED(3),

  /** Vote was added */
  ADDED(4);

  companion object {
    fun fromValue(value: Int) = VoteState.entries.first { it.value == value }
  }
}
