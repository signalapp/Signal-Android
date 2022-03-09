package org.thoughtcrime.securesms.stories.viewer.text

import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.linkpreview.LinkPreview

data class StoryTextPostState(
  val storyTextPost: StoryTextPost? = null,
  val linkPreview: LinkPreview? = null,
  val loadState: LoadState = LoadState.INIT
) {
  enum class LoadState {
    INIT,
    LOADED,
    FAILED
  }
}
