package org.thoughtcrime.securesms.conversation.mutiselect.forward

import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sharing.ShareContact

data class MultiselectForwardState(
  val selectedContacts: List<ShareContact> = emptyList(),
  val stage: Stage = Stage.Selection
) {
  sealed class Stage {
    object Selection : Stage()
    object FirstConfirmation : Stage()
    object LoadingIdentities : Stage()
    data class SafetyConfirmation(val identities: List<IdentityRecord>) : Stage()
    object SendPending : Stage()
    object SomeFailed : Stage()
    object AllFailed : Stage()
    object Success : Stage()
    data class SelectionConfirmed(val recipients: List<RecipientId>) : Stage()
  }
}
