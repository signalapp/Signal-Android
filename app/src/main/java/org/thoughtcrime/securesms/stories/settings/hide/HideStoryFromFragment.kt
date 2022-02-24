package org.thoughtcrime.securesms.stories.settings.hide

import androidx.appcompat.widget.Toolbar
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.stories.settings.select.BaseStoryRecipientSelectionFragment

/**
 * Allows user to select a list of people to exclude from "My Story"
 */
class HideStoryFromFragment : BaseStoryRecipientSelectionFragment() {
  override val actionButtonLabel: Int = R.string.HideStoryFromFragment__done

  override val distributionListId: DistributionListId
    get() = DistributionListId.from(DistributionListId.MY_STORY_ID)

  override val toolbarTitleId: Int = R.string.HideStoryFromFragment__hide_story_from

  override fun presentTitle(toolbar: Toolbar, size: Int) = Unit
}
