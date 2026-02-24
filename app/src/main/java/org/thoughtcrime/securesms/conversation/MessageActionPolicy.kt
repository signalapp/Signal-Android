package org.thoughtcrime.securesms.conversation

import org.thoughtcrime.securesms.recipients.Recipient

data class MessageActionPolicyContext(
  val recipient: Recipient,
  val conversationMessage: ConversationMessage,
  val shouldShowMessageRequest: Boolean,
  val isNonAdminInAnnouncementGroup: Boolean,
  val canEditGroupInfo: Boolean,
  val isActionModeStarted: Boolean,
  val hasSelection: Boolean
)

object MessageActionPolicy {

  @JvmStatic
  fun availableActions(context: MessageActionPolicyContext): List<MessageContextAction> {
    if (context.isActionModeStarted || context.hasSelection) {
      return emptyList()
    }

    val menuState = getMenuState(context)
    val actions = mutableListOf<MessageContextAction>()

    if (menuState.shouldShowReplyAction()) {
      actions += MessageContextAction.REPLY
    }

    if (menuState.shouldShowEditAction()) {
      actions += MessageContextAction.EDIT
    }

    if (menuState.shouldShowForwardAction()) {
      actions += MessageContextAction.FORWARD
    }

    if (menuState.shouldShowResendAction()) {
      actions += MessageContextAction.RESEND
    }

    if (menuState.shouldShowSaveAttachmentAction()) {
      actions += MessageContextAction.SAVE
    }

    if (menuState.shouldShowCopyAction()) {
      actions += MessageContextAction.COPY
    }

    if (menuState.shouldShowPaymentDetails()) {
      actions += MessageContextAction.PAYMENT_DETAILS
    }

    actions += MessageContextAction.MULTI_SELECT

    if (menuState.shouldShowDetailsAction()) {
      actions += MessageContextAction.VIEW_INFO
    }

    if (menuState.shouldShowPollTerminateAction()) {
      actions += MessageContextAction.END_POLL
    }

    if (menuState.shouldShowPinMessage()) {
      actions += MessageContextAction.PIN_MESSAGE
    }

    if (menuState.showShowUnpinMessage()) {
      actions += MessageContextAction.UNPIN_MESSAGE
    }

    if (menuState.shouldShowDeleteAction()) {
      actions += MessageContextAction.DELETE
    }

    return actions
  }

  @JvmStatic
  fun shouldShowReactions(context: MessageActionPolicyContext): Boolean {
    if (context.isActionModeStarted || context.hasSelection) {
      return false
    }

    return getMenuState(context).shouldShowReactions()
  }

  private fun getMenuState(context: MessageActionPolicyContext): MenuState {
    return MenuState.getMenuState(
      context.recipient,
      context.conversationMessage.multiselectCollection.toSet(),
      context.shouldShowMessageRequest,
      context.isNonAdminInAnnouncementGroup,
      context.canEditGroupInfo
    )
  }
}
