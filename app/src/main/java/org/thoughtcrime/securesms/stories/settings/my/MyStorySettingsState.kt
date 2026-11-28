package org.thoughtcrime.securesms.stories.settings.my

import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode

/**
 * Everything [MyStorySettingsScreen] needs to render.
 */
data class MyStorySettingsState(
  val myStoryPrivacyState: MyStoryPrivacyState = MyStoryPrivacyState(),
  val areRepliesAndReactionsEnabled: Boolean = false,
  val allSignalConnectionsCount: Int = 0,
  val hasUserPerformedManualSelection: Boolean = false
) {

  /** The privacy mode to show as selected. Nothing is selected until the user has made a choice. */
  val selectedPrivacyMode: DistributionListPrivacyMode?
    get() = myStoryPrivacyState.privacyMode.takeIf { hasUserPerformedManualSelection }
}
