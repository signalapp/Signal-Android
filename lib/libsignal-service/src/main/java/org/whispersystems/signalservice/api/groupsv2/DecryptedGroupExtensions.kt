/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.groupsv2

import org.signal.core.models.ServiceId
import org.signal.core.models.ServiceId.ACI
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup
import org.signal.storageservice.storage.protos.groups.local.DecryptedMember
import org.signal.storageservice.storage.protos.groups.local.DecryptedModifyMemberLabel
import org.signal.storageservice.storage.protos.groups.local.DecryptedPendingMember
import org.signal.storageservice.storage.protos.groups.local.DecryptedRequestingMember
import java.util.Optional

fun Collection<DecryptedMember>.toAciListWithUnknowns(): List<ACI> {
  return DecryptedGroupUtil.toAciListWithUnknowns(this)
}

fun Collection<DecryptedMember>.toAciList(): List<ACI> {
  return DecryptedGroupUtil.toAciList(this)
}

fun Collection<DecryptedMember>.findMemberByAci(aci: ACI): Optional<DecryptedMember> {
  return DecryptedGroupUtil.findMemberByAci(this, aci)
}

fun Collection<DecryptedRequestingMember>.findRequestingByAci(aci: ACI): Optional<DecryptedRequestingMember> {
  return DecryptedGroupUtil.findRequestingByAci(this, aci)
}

fun Collection<DecryptedPendingMember>.findPendingByServiceId(serviceId: ServiceId): Optional<DecryptedPendingMember> {
  return DecryptedGroupUtil.findPendingByServiceId(this, serviceId)
}

@Throws(NotAbleToApplyGroupV2ChangeException::class)
fun DecryptedGroup.Builder.setModifyMemberLabelActions(
  actions: List<DecryptedModifyMemberLabel>
) {
  val updatedMembers = members.toMutableList()
  actions.forEach { action ->
    val modifiedMemberIndex = updatedMembers.indexOfFirst { it.aciBytes == action.aciBytes }
    if (modifiedMemberIndex < 0) {
      throw NotAbleToApplyGroupV2ChangeException()
    }

    updatedMembers[modifiedMemberIndex] = updatedMembers[modifiedMemberIndex]
      .newBuilder()
      .labelEmoji(action.labelEmoji)
      .labelString(action.labelString)
      .build()
  }

  members = updatedMembers
}
