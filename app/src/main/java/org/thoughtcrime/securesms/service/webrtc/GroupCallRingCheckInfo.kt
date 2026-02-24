package org.thoughtcrime.securesms.service.webrtc

import org.signal.core.models.ServiceId.ACI
import org.signal.ringrtc.CallManager
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.RecipientId

data class GroupCallRingCheckInfo(
  val recipientId: RecipientId,
  val groupId: GroupId.V2,
  val ringId: Long,
  val ringerAci: ACI,
  val ringUpdate: CallManager.RingUpdate
)
