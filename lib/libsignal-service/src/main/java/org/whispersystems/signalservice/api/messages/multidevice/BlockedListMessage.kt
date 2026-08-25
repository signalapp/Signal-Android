package org.whispersystems.signalservice.api.messages.multidevice

import org.signal.core.models.ServiceId

class BlockedListMessage(
  @JvmField val individuals: List<Individual>,
  @JvmField val groups: List<Group>
) {
  data class Individual(
    val aci: ServiceId.ACI?,
    val e164: String?,
    val blockedAt: Long
  ) {
    init {
      check(aci != null || e164 != null)
    }
  }

  data class Group(
    val groupId: ByteArray,
    val blockedAt: Long
  )
}
