package org.thoughtcrime.securesms.conversation.colors.ui

import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel

class ChatColorMappingModel(
  val chatColors: ChatColors,
  val isSelected: Boolean,
  val isAuto: Boolean
) : MappingModel<ChatColorMappingModel> {

  val isCustom: Boolean = chatColors.id is ChatColors.Id.Custom

  override fun areItemsTheSame(newItem: ChatColorMappingModel): Boolean {
    return chatColors == newItem.chatColors && isAuto == newItem.isAuto
  }

  override fun areContentsTheSame(newItem: ChatColorMappingModel): Boolean {
    return areItemsTheSame(newItem) && isSelected == newItem.isSelected
  }
}
