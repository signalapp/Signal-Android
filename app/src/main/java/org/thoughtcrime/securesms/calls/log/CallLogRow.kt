/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.log

import org.thoughtcrime.securesms.database.CallLinkTable
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.model.databaseprotos.GroupCallUpdateDetails
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.service.webrtc.CallLinkPeekInfo
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId

/**
 * A row to be displayed in the call log
 */
sealed class CallLogRow {

  abstract val id: Id

  /**
   * A call link with no "active" events.
   */
  data class CallLink(
    val record: CallLinkTable.CallLink,
    val recipient: Recipient,
    val searchQuery: String?,
    val callLinkPeekInfo: CallLinkPeekInfo?,
    override val id: Id = Id.CallLink(record.roomId)
  ) : CallLogRow()

  /**
   * An incoming, outgoing, or missed call.
   */
  data class Call(
    val record: CallTable.Call,
    val peer: Recipient,
    val date: Long,
    val groupCallState: GroupCallState,
    val children: Set<Long>,
    val searchQuery: String?,
    val callLinkPeekInfo: CallLinkPeekInfo?,
    override val id: Id = Id.Call(children)
  ) : CallLogRow()

  /**
   * A row which can be used to clear the current filter.
   */
  object ClearFilter : CallLogRow() {
    override val id: Id = Id.ClearFilter
  }

  object CreateCallLink : CallLogRow() {
    override val id: Id = Id.CreateCallLink
  }

  sealed class Id {
    data class Call(val children: Set<Long>) : Id()
    data class CallLink(val roomId: CallLinkRoomId) : Id()
    object ClearFilter : Id()
    object CreateCallLink : Id()
  }

  enum class GroupCallState {
    /**
     * No group call available.
     */
    NONE,

    /**
     * Active, but the local user is not in the call.
     */
    ACTIVE,

    /**
     * Active and the local user is in the call
     */
    LOCAL_USER_JOINED,

    /**
     * Active but the call is full.
     */
    FULL;

    companion object {
      fun fromDetails(groupCallUpdateDetails: GroupCallUpdateDetails?): GroupCallState {
        if (groupCallUpdateDetails == null) {
          return NONE
        }

        if (groupCallUpdateDetails.isCallFull) {
          return FULL
        }

        if (groupCallUpdateDetails.inCallUuidsList.contains(Recipient.self().requireAci().rawUuid.toString())) {
          return LOCAL_USER_JOINED
        }

        return if (groupCallUpdateDetails.inCallUuidsCount > 0) {
          ACTIVE
        } else {
          NONE
        }
      }
    }
  }
}
