/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import org.signal.donations.StripeIntentAccessor
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentMethodType
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.model.databaseprotos.ExternalLaunchTransactionState
import org.thoughtcrime.securesms.database.model.databaseprotos.FiatValue
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.recipients.Recipient
import kotlin.time.Duration.Companion.milliseconds

/**
 * Encapsulates the data required to complete a pending external transaction
 */
@Parcelize
data class Stripe3DSData(
  val stripeIntentAccessor: StripeIntentAccessor,
  val inAppPayment: InAppPaymentTable.InAppPayment,
  private val rawPaymentSourceType: String
) : Parcelable {
  @IgnoredOnParcel
  val paymentSourceType: PaymentSourceType = PaymentSourceType.fromCode(rawPaymentSourceType)

  @IgnoredOnParcel
  val isLongRunning: Boolean = paymentSourceType == PaymentSourceType.Stripe.SEPADebit || (inAppPayment.type.recurring && paymentSourceType.isBankTransfer)

  fun toProtoBytes(): ByteArray {
    return ExternalLaunchTransactionState(
      stripeIntentAccessor = ExternalLaunchTransactionState.StripeIntentAccessor(
        type = when (stripeIntentAccessor.objectType) {
          StripeIntentAccessor.ObjectType.NONE, StripeIntentAccessor.ObjectType.PAYMENT_INTENT -> ExternalLaunchTransactionState.StripeIntentAccessor.Type.PAYMENT_INTENT
          StripeIntentAccessor.ObjectType.SETUP_INTENT -> ExternalLaunchTransactionState.StripeIntentAccessor.Type.SETUP_INTENT
        },
        intentId = stripeIntentAccessor.intentId,
        intentClientSecret = stripeIntentAccessor.intentClientSecret
      ),
      gatewayRequest = ExternalLaunchTransactionState.GatewayRequest(
        inAppPaymentType = when (inAppPayment.type) {
          InAppPaymentType.UNKNOWN -> error("Unsupported type UNKNOWN")
          InAppPaymentType.ONE_TIME_DONATION -> ExternalLaunchTransactionState.GatewayRequest.InAppPaymentType.ONE_TIME_DONATION
          InAppPaymentType.RECURRING_DONATION -> ExternalLaunchTransactionState.GatewayRequest.InAppPaymentType.RECURRING_DONATION
          InAppPaymentType.ONE_TIME_GIFT -> ExternalLaunchTransactionState.GatewayRequest.InAppPaymentType.ONE_TIME_GIFT
          InAppPaymentType.RECURRING_BACKUP -> ExternalLaunchTransactionState.GatewayRequest.InAppPaymentType.RECURRING_BACKUPS
        },
        badge = inAppPayment.data.badge,
        price = inAppPayment.data.amount!!.amount,
        currencyCode = inAppPayment.data.amount.currencyCode,
        level = inAppPayment.data.level,
        recipient_id = inAppPayment.data.recipientId?.toLong() ?: Recipient.self().id.toLong(),
        additionalMessage = inAppPayment.data.additionalMessage ?: ""
      ),
      paymentSourceType = paymentSourceType.code
    ).encode()
  }

  companion object {
    fun fromProtoBytes(byteArray: ByteArray): Stripe3DSData {
      val proto = ExternalLaunchTransactionState.ADAPTER.decode(byteArray)
      return Stripe3DSData(
        stripeIntentAccessor = StripeIntentAccessor(
          objectType = when (proto.stripeIntentAccessor!!.type) {
            ExternalLaunchTransactionState.StripeIntentAccessor.Type.PAYMENT_INTENT -> StripeIntentAccessor.ObjectType.PAYMENT_INTENT
            ExternalLaunchTransactionState.StripeIntentAccessor.Type.SETUP_INTENT -> StripeIntentAccessor.ObjectType.SETUP_INTENT
          },
          intentId = proto.stripeIntentAccessor.intentId,
          intentClientSecret = proto.stripeIntentAccessor.intentClientSecret
        ),
        inAppPayment = InAppPaymentTable.InAppPayment(
          id = InAppPaymentTable.InAppPaymentId(-1), // TODO [alex] -- can we start writing this in for new transactions?
          type = when (proto.gatewayRequest!!.inAppPaymentType) {
            ExternalLaunchTransactionState.GatewayRequest.InAppPaymentType.RECURRING_DONATION -> InAppPaymentType.RECURRING_DONATION
            ExternalLaunchTransactionState.GatewayRequest.InAppPaymentType.ONE_TIME_DONATION -> InAppPaymentType.ONE_TIME_DONATION
            ExternalLaunchTransactionState.GatewayRequest.InAppPaymentType.ONE_TIME_GIFT -> InAppPaymentType.ONE_TIME_GIFT
            ExternalLaunchTransactionState.GatewayRequest.InAppPaymentType.RECURRING_BACKUPS -> InAppPaymentType.RECURRING_BACKUP
          },
          endOfPeriod = 0.milliseconds,
          updatedAt = 0.milliseconds,
          insertedAt = 0.milliseconds,
          notified = true,
          state = InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION,
          subscriberId = null,
          data = InAppPaymentData(
            paymentMethodType = PaymentSourceType.fromCode(proto.paymentSourceType).toPaymentMethodType(),
            badge = proto.gatewayRequest.badge,
            amount = FiatValue(amount = proto.gatewayRequest.price, currencyCode = proto.gatewayRequest.currencyCode),
            level = proto.gatewayRequest.level,
            recipientId = null,
            additionalMessage = "",
            waitForAuth = InAppPaymentData.WaitingForAuthorizationState(
              stripeClientSecret = proto.stripeIntentAccessor.intentClientSecret,
              stripeIntentId = proto.stripeIntentAccessor.intentId
            )
          )
        ),
        rawPaymentSourceType = proto.paymentSourceType
      )
    }
  }
}
