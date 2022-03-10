package org.thoughtcrime.securesms.stories

import androidx.fragment.app.FragmentManager
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.HeaderAction
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.v2.stories.ChooseStoryTypeBottomSheet
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.FeatureFlags

object Stories {
  @JvmStatic
  fun isFeatureAvailable(): Boolean {
    return FeatureFlags.stories() && Recipient.self().storiesCapability == Recipient.Capability.SUPPORTED
  }

  @JvmStatic
  fun isFeatureEnabled(): Boolean {
    return isFeatureAvailable() && !SignalStore.storyValues().isFeatureDisabled
  }

  fun getHeaderAction(fragmentManager: FragmentManager): HeaderAction {
    return HeaderAction(
      R.string.ContactsCursorLoader_new_story,
      R.drawable.ic_plus_20
    ) {
      ChooseStoryTypeBottomSheet().show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }
}
