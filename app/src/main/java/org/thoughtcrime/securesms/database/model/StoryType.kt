package org.thoughtcrime.securesms.database.model

/**
 * Represents whether a given story can be replied to.
 */
enum class StoryType(val code: Int) {
  /**
   * Not a story.
   */
  NONE(0),

  /**
   * User can send replies to this story.
   */
  STORY_WITH_REPLIES(1),

  /**
   * User cannot send replies to this story.
   */
  STORY_WITHOUT_REPLIES(2);

  val isStory get() = this == STORY_WITH_REPLIES || this == STORY_WITHOUT_REPLIES

  val isStoryWithReplies get() = this == STORY_WITH_REPLIES

  companion object {
    @JvmStatic
    fun fromCode(code: Int): StoryType {
      return when (code) {
        1 -> STORY_WITH_REPLIES
        2 -> STORY_WITHOUT_REPLIES
        else -> NONE
      }
    }
  }
}
