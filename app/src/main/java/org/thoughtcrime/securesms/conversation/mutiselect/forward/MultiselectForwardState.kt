package org.thoughtcrime.securesms.conversation.mutiselect.forward

import org.thoughtcrime.securesms.sharing.ShareContact

data class MultiselectForwardState(
  val selectedContacts: List<ShareContact> = emptyList(),
  val stage: Stage = Stage.SELECTION
) {
  enum class Stage {
    SELECTION,
    SOME_FAILED,
    ALL_FAILED,
    SUCCESS
  }
}
