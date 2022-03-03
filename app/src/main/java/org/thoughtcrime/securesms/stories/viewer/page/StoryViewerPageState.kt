package org.thoughtcrime.securesms.stories.viewer.page

data class StoryViewerPageState(
  val posts: List<StoryPost> = emptyList(),
  val selectedPostIndex: Int = 0,
  val replyState: ReplyState = ReplyState.NONE
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
    GROUP_SELF;

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
