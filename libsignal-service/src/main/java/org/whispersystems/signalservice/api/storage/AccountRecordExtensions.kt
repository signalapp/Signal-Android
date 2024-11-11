/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.storage

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.util.isNotEmpty
import org.whispersystems.signalservice.api.payments.PaymentsConstants
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.storage.StorageRecordProtoUtil.defaultAccountRecord
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord
import org.whispersystems.signalservice.internal.storage.protos.Payments

fun AccountRecord.Builder.safeSetPayments(enabled: Boolean, entropy: ByteArray?): AccountRecord.Builder {
  val paymentsBuilder = Payments.Builder()
  val entropyPresent = entropy != null && entropy.size == PaymentsConstants.PAYMENTS_ENTROPY_LENGTH

  paymentsBuilder.enabled(enabled && entropyPresent)

  if (entropyPresent) {
    paymentsBuilder.entropy(entropy!!.toByteString())
  }

  this.payments = paymentsBuilder.build()

  return this
}
fun AccountRecord.Builder.safeSetSubscriber(subscriberId: ByteString, subscriberCurrencyCode: String): AccountRecord.Builder {
  if (subscriberId.isNotEmpty() && subscriberId.size == 32 && subscriberCurrencyCode.isNotBlank()) {
    this.subscriberId = subscriberId
    this.subscriberCurrencyCode = subscriberCurrencyCode
  } else {
    this.subscriberId = defaultAccountRecord.subscriberId
    this.subscriberCurrencyCode = defaultAccountRecord.subscriberCurrencyCode
  }

  return this
}

fun AccountRecord.Builder.safeSetBackupsSubscriber(subscriberId: ByteString, subscriberCurrencyCode: String): AccountRecord.Builder {
  if (subscriberId.isNotEmpty() && subscriberId.size == 32 && subscriberCurrencyCode.isNotBlank()) {
    this.backupsSubscriberId = subscriberId
    this.backupsSubscriberCurrencyCode = subscriberCurrencyCode
  } else {
    this.backupsSubscriberId = defaultAccountRecord.backupsSubscriberId
    this.backupsSubscriberCurrencyCode = defaultAccountRecord.backupsSubscriberCurrencyCode
  }

  return this
}

fun AccountRecord.PinnedConversation.Contact.toSignalServiceAddress(): SignalServiceAddress {
  val serviceId = ServiceId.parseOrNull(this.serviceId)
  return SignalServiceAddress(serviceId, this.e164)
}
