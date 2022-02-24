package org.thoughtcrime.securesms.stories.settings.custom.viewers

import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.stories.settings.select.BaseStoryRecipientSelectionFragment

/**
 * Allows user to manage users that can view a story for a given distribution list.
 */
class AddViewersFragment : BaseStoryRecipientSelectionFragment() {
  override val actionButtonLabel: Int = R.string.HideStoryFromFragment__done
  override val distributionListId: DistributionListId
    get() = AddViewersFragmentArgs.fromBundle(requireArguments()).distributionListId
}
