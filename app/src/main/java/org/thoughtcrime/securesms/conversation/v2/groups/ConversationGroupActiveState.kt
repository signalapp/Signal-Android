package org.thoughtcrime.securesms.conversation.v2.groups

/**
 * Represents the 'active' state of a group.
 */
data class ConversationGroupActiveState(
  val isActive: Boolean,
  private val isV2: Boolean
) {
  val isActiveV2: Boolean = isActive && isV2
}
