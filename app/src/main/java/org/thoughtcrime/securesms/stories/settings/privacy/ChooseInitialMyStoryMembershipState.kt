package org.thoughtcrime.securesms.stories.settings.privacy

import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.stories.settings.my.MyStoryPrivacyState

data class ChooseInitialMyStoryMembershipState(
  val recipientId: RecipientId? = null,
  val privacyState: MyStoryPrivacyState = MyStoryPrivacyState(),
  val allSignalConnectionsCount: Int = 0,
  val hasUserPerformedManualSelection: Boolean = false
)
