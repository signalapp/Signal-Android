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
  STORY_WITHOUT_REPLIES(2),

  /**
   * Text story that allows replies
   */
  TEXT_STORY_WITH_REPLIES(3),

  /**
   * Text story that does not allow replies
   */
  TEXT_STORY_WITHOUT_REPLIES(4);

  val isStory get() = this != NONE

  val isStoryWithReplies get() = this == STORY_WITH_REPLIES || this == TEXT_STORY_WITH_REPLIES

  val isTextStory get() = this == TEXT_STORY_WITHOUT_REPLIES || this == TEXT_STORY_WITH_REPLIES

  fun toTextStoryType(): StoryType {
    return when (this) {
      NONE -> NONE
      STORY_WITH_REPLIES -> TEXT_STORY_WITH_REPLIES
      STORY_WITHOUT_REPLIES -> TEXT_STORY_WITHOUT_REPLIES
      TEXT_STORY_WITH_REPLIES -> TEXT_STORY_WITH_REPLIES
      TEXT_STORY_WITHOUT_REPLIES -> TEXT_STORY_WITHOUT_REPLIES
    }
  }

  companion object {
    @JvmStatic
    fun fromCode(code: Int): StoryType {
      return when (code) {
        1 -> STORY_WITH_REPLIES
        2 -> STORY_WITHOUT_REPLIES
        3 -> TEXT_STORY_WITH_REPLIES
        4 -> TEXT_STORY_WITHOUT_REPLIES
        else -> NONE
      }
    }

    @JvmStatic
    fun withReplies(isTextStory: Boolean): StoryType {
      return if (isTextStory) TEXT_STORY_WITH_REPLIES else STORY_WITH_REPLIES
    }

    @JvmStatic
    fun withoutReplies(isTextStory: Boolean): StoryType {
      return if (isTextStory) TEXT_STORY_WITHOUT_REPLIES else STORY_WITHOUT_REPLIES
    }
  }
}
