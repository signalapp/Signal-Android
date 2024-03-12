package org.thoughtcrime.securesms.messagerequests

/**
 * Data necessary to render message request view.
 */
data class MessageRequestState @JvmOverloads constructor(val state: State = State.NONE, val reportedAsSpam: Boolean = false) {

  companion object {
    @JvmField
    val NONE = MessageRequestState()

    @JvmField
    val DEPRECATED_V1 = MessageRequestState()
  }

  val isAccepted: Boolean
    get() = state == State.NONE || state == State.NONE_HIDDEN

  val isBlocked: Boolean
    get() = state == State.INDIVIDUAL_BLOCKED || state == State.BLOCKED_GROUP

  /**
   * An enum representing the possible message request states a user can be in.
   */
  enum class State {
    /** No message request necessary  */
    NONE,

    /** No message request necessary as the user was hidden after accepting */
    NONE_HIDDEN,

    /** A group is blocked  */
    BLOCKED_GROUP,

    /** An individual conversation that existed pre-message-requests but doesn't have profile sharing enabled  */
    LEGACY_INDIVIDUAL,

    /** A V1 group conversation that is no longer allowed, because we've forced GV2 on.  */
    DEPRECATED_GROUP_V1,

    /** An invite response is needed for a V2 group  */
    GROUP_V2_INVITE,

    /** A message request is needed for a V2 group  */
    GROUP_V2_ADD,

    /** A message request is needed for an individual  */
    INDIVIDUAL,

    /** A user is blocked  */
    INDIVIDUAL_BLOCKED,

    /** A message request is needed for an individual since they have been hidden  */
    INDIVIDUAL_HIDDEN
  }
}
