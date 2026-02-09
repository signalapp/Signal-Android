package org.thoughtcrime.securesms.recipients.ui.bottomsheet

import org.thoughtcrime.securesms.groups.memberlabel.StyledMemberLabel

data class RecipientDetailsState(
  val memberLabel: StyledMemberLabel?,
  val aboutText: String?
)
