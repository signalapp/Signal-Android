package org.thoughtcrime.securesms.stories.viewer.page

data class StoryViewerPageState(
  val posts: List<StoryPost> = emptyList(),
  val selectedPostIndex: Int = 0,
  val replyState: ReplyState = ReplyState.NONE,
  val isFirstPage: Boolean = false,
  val isDisplayingInitialState: Boolean = false,
  val isReady: Boolean = false,
  val isReceiptsEnabled: Boolean
) {
  /**
   * Indicates which Reply method is available when the user swipes on the dialog
   */
  enum class ReplyState {
    /**
     * Disabled state
     */
    NONE,

    /**
     * Story is from self and not in a group
     */
    SELF,

    /**
     * Story is not from self and in a group
     */
    GROUP,

    /**
     * Story is not from self and not in a group
     */
    PRIVATE,

    /**
     * Story is from self and in a group
     */
    GROUP_SELF,

    /**
     * Story was not sent to all recipients.
     */
    PARTIAL_SEND,

    /**
     * Story failed to send.
     */
    SEND_FAILURE,

    /**
     * Story is currently being sent.
     */
    SENDING;

    companion object {
      fun resolve(isFromSelf: Boolean, isToGroup: Boolean): ReplyState {
        return when {
          isFromSelf && isToGroup -> GROUP_SELF
          isFromSelf && !isToGroup -> SELF
          !isFromSelf && isToGroup -> GROUP
          !isFromSelf && !isToGroup -> PRIVATE
          else -> NONE
        }
      }
    }
  }
}
