package org.thoughtcrime.securesms.recipients.ui.bottomsheet

import androidx.annotation.ColorInt
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabel

data class RecipientDetailsState(
  val memberLabel: StyledMemberLabel?,
  val aboutText: String?
)

data class StyledMemberLabel(
  val label: MemberLabel,
  @param:ColorInt val tintColor: Int
)
