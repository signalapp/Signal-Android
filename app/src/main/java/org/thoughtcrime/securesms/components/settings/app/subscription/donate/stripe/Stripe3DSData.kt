/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.signal.donations.PaymentSourceType
import org.signal.donations.StripeIntentAccessor
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toBigDecimal
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toDecimalValue
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalType
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayRequest
import org.thoughtcrime.securesms.database.model.databaseprotos.ExternalLaunchTransactionState
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Encapsulates the data required to complete a pending external transaction
 */
@Parcelize
data class Stripe3DSData(
  val stripeIntentAccessor: StripeIntentAccessor,
  val gatewayRequest: GatewayRequest,
  private val rawPaymentSourceType: String
) : Parcelable {
  @IgnoredOnParcel
  val paymentSourceType: PaymentSourceType = PaymentSourceType.fromCode(rawPaymentSourceType)

  @IgnoredOnParcel
  val isLongRunning: Boolean = paymentSourceType == PaymentSourceType.Stripe.SEPADebit || (gatewayRequest.donateToSignalType == DonateToSignalType.MONTHLY && paymentSourceType.isBankTransfer)

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
        donateToSignalType = when (gatewayRequest.donateToSignalType) {
          DonateToSignalType.ONE_TIME -> ExternalLaunchTransactionState.GatewayRequest.DonateToSignalType.ONE_TIME
          DonateToSignalType.MONTHLY -> ExternalLaunchTransactionState.GatewayRequest.DonateToSignalType.MONTHLY
          DonateToSignalType.GIFT -> ExternalLaunchTransactionState.GatewayRequest.DonateToSignalType.GIFT
        },
        badge = Badges.toDatabaseBadge(gatewayRequest.badge),
        label = gatewayRequest.label,
        price = gatewayRequest.price.toDecimalValue(),
        currencyCode = gatewayRequest.currencyCode,
        level = gatewayRequest.level,
        recipient_id = gatewayRequest.recipientId.toLong(),
        additionalMessage = gatewayRequest.additionalMessage ?: ""
      ),
      paymentSourceType = paymentSourceType.code
    ).encode()
  }

  companion object {
    fun fromProtoBytes(byteArray: ByteArray, uiSessionKey: Long): Stripe3DSData {
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
        gatewayRequest = GatewayRequest(
          uiSessionKey = uiSessionKey,
          donateToSignalType = when (proto.gatewayRequest!!.donateToSignalType) {
            ExternalLaunchTransactionState.GatewayRequest.DonateToSignalType.MONTHLY -> DonateToSignalType.MONTHLY
            ExternalLaunchTransactionState.GatewayRequest.DonateToSignalType.ONE_TIME -> DonateToSignalType.ONE_TIME
            ExternalLaunchTransactionState.GatewayRequest.DonateToSignalType.GIFT -> DonateToSignalType.GIFT
          },
          badge = Badges.fromDatabaseBadge(proto.gatewayRequest.badge!!),
          label = proto.gatewayRequest.label,
          price = proto.gatewayRequest.price!!.toBigDecimal(),
          currencyCode = proto.gatewayRequest.currencyCode,
          level = proto.gatewayRequest.level,
          recipientId = RecipientId.from(proto.gatewayRequest.recipient_id),
          additionalMessage = proto.gatewayRequest.additionalMessage.takeIf { it.isNotBlank() }
        ),
        rawPaymentSourceType = proto.paymentSourceType
      )
    }
  }
}
