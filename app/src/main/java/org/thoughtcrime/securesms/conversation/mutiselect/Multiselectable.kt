package org.thoughtcrime.securesms.conversation.mutiselect

import android.view.View
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.colors.Colorizable

interface Multiselectable : Colorizable {
  val conversationMessage: ConversationMessage

  fun getTopBoundaryOfMultiselectPart(multiselectPart: MultiselectPart): Int

  fun getBottomBoundaryOfMultiselectPart(multiselectPart: MultiselectPart): Int

  fun getMultiselectPartForLatestTouch(): MultiselectPart

  fun getHorizontalTranslationTarget(): View?

  fun hasNonSelectableMedia(): Boolean
}
