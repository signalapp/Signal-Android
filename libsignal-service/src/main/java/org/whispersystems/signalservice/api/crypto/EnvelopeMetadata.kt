package org.whispersystems.signalservice.api.crypto

import org.whispersystems.signalservice.api.push.ServiceId

class EnvelopeMetadata(
  val sourceServiceId: ServiceId,
  val sourceE164: String?,
  val sourceDeviceId: Int,
  val sealedSender: Boolean,
  val groupId: ByteArray?,
  val destinationServiceId: ServiceId
)
