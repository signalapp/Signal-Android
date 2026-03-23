package org.thoughtcrime.securesms.database

import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras

/**
 * Utility functions to track the different collapsing types and what type a message is
 */
object CollapsibleEvents {

  @JvmStatic
  fun isCollapsibleType(type: Long, messageExtras: MessageExtras?): Boolean {
    return getCollapsibleType(type, messageExtras) != null
  }

  @JvmStatic
  fun getCollapsibleType(type: Long, messageExtras: MessageExtras?): CollapsibleType? {
    if (MessageTypes.isCallLog(type)) {
      return CollapsibleType.CALL_EVENT
    }

    if (MessageTypes.isExpirationTimerUpdate(type)) {
      return CollapsibleType.DISAPPEARING_TIMER
    }

    if (messageExtras?.gv2UpdateDescription != null) {
      val groupChangeUpdate = messageExtras.gv2UpdateDescription.groupChangeUpdate
      return if (groupChangeUpdate?.updates?.any { it.groupExpirationTimerUpdate != null } == true) {
        CollapsibleType.DISAPPEARING_TIMER
      } else if (groupChangeUpdate?.updates?.none { it.groupTerminateChangeUpdate != null } == true) {
        CollapsibleType.CHAT_UPDATE
      } else {
        null
      }
    }

    if (MessageTypes.isProfileChange(type)) {
      return CollapsibleType.CHAT_UPDATE
    }

    if (MessageTypes.isIdentityUpdate(type) || MessageTypes.isIdentityVerified(type) || MessageTypes.isIdentityDefault(type)) {
      return CollapsibleType.CHAT_UPDATE
    }

    return null
  }

  enum class CollapsibleType {
    DISAPPEARING_TIMER,
    CHAT_UPDATE,
    CALL_EVENT
  }
}
