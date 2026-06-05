/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.util.money.FiatMoney
import org.signal.donations.DonateWithGooglePayButton
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImage112
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toDecimalValue
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.ui.IdealWeroButton
import org.thoughtcrime.securesms.components.settings.app.subscription.ui.PayPalButton
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.model.databaseprotos.BadgeList
import org.thoughtcrime.securesms.database.model.databaseprotos.FiatValue
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.recipients.Recipient
import java.math.BigDecimal
import java.util.Currency
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun GatewaySelectorBottomSheetContent(
  state: GatewaySelectorState,
  onEvent: (GatewaySelectorBottomSheetEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .testTag(GatewaySelectorTestTags.CONTAINER)
      .verticalScroll(scrollState)
      .horizontalGutters()
      .fillMaxWidth()
  ) {
    BottomSheets.Handle()

    when (state) {
      GatewaySelectorState.Loading -> Loading()
      is GatewaySelectorState.Ready -> Ready(state, onEvent)
    }
  }
}

@Composable
private fun Loading() {
  CircularProgressIndicator(
    modifier = Modifier.padding(vertical = 16.dp)
  )
}

@Composable
private fun Ready(state: GatewaySelectorState.Ready, onEvent: (GatewaySelectorBottomSheetEvent) -> Unit) {
  BadgeImage112(
    badge = state.inAppPayment.data.badge!!.let { Badges.fromDatabaseBadge(it) },
    modifier = Modifier.size(112.dp)
  )

  Spacer(modifier = Modifier.size(12.dp))

  TitleAndSubtitle(state.inAppPayment)

  Spacer(modifier = Modifier.size(16.dp))

  var isGatewaySelected by remember { mutableStateOf(false) }
  val onGatewaySelected: (GatewaySelectorBottomSheetEvent) -> Unit = remember(onEvent) {
    {
      if (!isGatewaySelected) {
        isGatewaySelected = true
        onEvent(it)
      }
    }
  }

  state.gatewayOrderStrategy.orderedGateways.forEach {
    when (it) {
      InAppPaymentData.PaymentMethodType.UNKNOWN -> error("Unsupported payment method.")
      InAppPaymentData.PaymentMethodType.GOOGLE_PAY -> {
        if (state.isGooglePayAvailable) {
          DonateWithGooglePayButton(
            onClick = { onGatewaySelected(GatewaySelectorBottomSheetEvent.GOOGLE_PAY_SELECTED) },
            enabled = !isGatewaySelected,
            modifier = Modifier
              .testTag(GatewaySelectorTestTags.GOOGLE_PAY_BUTTON)
              .padding(top = 16.dp)
              .fillMaxWidth()
              .height(44.dp)
          )
        }
      }

      InAppPaymentData.PaymentMethodType.CARD -> {
        if (state.isCreditCardAvailable) {
          Buttons.LargePrimary(
            onClick = { onGatewaySelected(GatewaySelectorBottomSheetEvent.CREDIT_CARD_SELECTED) },
            enabled = !isGatewaySelected,
            modifier = Modifier
              .testTag(GatewaySelectorTestTags.CREDIT_CARD_BUTTON)
              .padding(top = 16.dp)
              .fillMaxWidth()
              .height(44.dp)
          ) {
            Row(
              horizontalArrangement = spacedBy(8.dp)
            ) {
              Icon(
                imageVector = ImageVector.vectorResource(R.drawable.credit_card),
                contentDescription = null
              )

              Text(
                text = stringResource(R.string.GatewaySelectorBottomSheet__credit_or_debit_card)
              )
            }
          }
        }
      }

      InAppPaymentData.PaymentMethodType.SEPA_DEBIT -> {
        if (state.isSEPADebitAvailable) {
          Buttons.LargeTonal(
            onClick = { onGatewaySelected(GatewaySelectorBottomSheetEvent.SEPA_SELECTED) },
            enabled = !isGatewaySelected,
            modifier = Modifier
              .testTag(GatewaySelectorTestTags.SEPA_BUTTON)
              .padding(top = 16.dp)
              .fillMaxWidth()
              .height(44.dp)
          ) {
            Row(
              horizontalArrangement = spacedBy(8.dp)
            ) {
              Icon(
                imageVector = ImageVector.vectorResource(R.drawable.bank_transfer),
                contentDescription = null
              )

              Text(
                text = stringResource(R.string.GatewaySelectorBottomSheet__bank_transfer)
              )
            }
          }
        }
      }

      InAppPaymentData.PaymentMethodType.IDEAL -> {
        if (state.isIDEALAvailable) {
          IdealWeroButton(
            onClick = { onGatewaySelected(GatewaySelectorBottomSheetEvent.IDEAL_SELECTED) },
            enabled = !isGatewaySelected,
            modifier = Modifier
              .testTag(GatewaySelectorTestTags.IDEAL_BUTTON)
              .padding(top = 16.dp)
              .height(44.dp)
              .fillMaxWidth()
          )
        }
      }

      InAppPaymentData.PaymentMethodType.PAYPAL -> {
        if (state.isPayPalAvailable) {
          PayPalButton(
            enabled = !isGatewaySelected,
            onClick = { onGatewaySelected(GatewaySelectorBottomSheetEvent.PAYPAL_SELECTED) },
            modifier = Modifier
              .testTag(GatewaySelectorTestTags.PAYPAL_BUTTON)
              .padding(top = 16.dp)
              .height(44.dp)
              .fillMaxWidth()
          )
        }
      }

      InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING -> error("Unsupported payment method.")
    }
  }

  Spacer(modifier = Modifier.size(16.dp))
}

@DayNightPreviews
@Composable
private fun GatewaySelectorBottomSheetContentLoadingPreview() {
  Previews.BottomSheetContentPreview {
    GatewaySelectorBottomSheetContent(
      state = GatewaySelectorState.Loading,
      onEvent = {}
    )
  }
}

@Composable
private fun TitleAndSubtitle(inAppPayment: InAppPaymentTable.InAppPayment) {
  when (inAppPayment.type) {
    InAppPaymentType.UNKNOWN -> error("Unsupported type UNKNOWN")
    InAppPaymentType.ONE_TIME_GIFT -> OneTimeGiftTitleAndSubtitle(inAppPayment)
    InAppPaymentType.ONE_TIME_DONATION -> OneTimeDonationTitleAndSubtitle(inAppPayment)
    InAppPaymentType.RECURRING_DONATION -> RecurringDonationTitleAndSubtitle(inAppPayment)
    InAppPaymentType.RECURRING_BACKUP -> error("This type is not supported")
  }
}

@Composable
private fun RecurringDonationTitleAndSubtitle(inAppPayment: InAppPaymentTable.InAppPayment) {
  Text(
    text = stringResource(R.string.GatewaySelectorBottomSheet__donate_s_month_to_signal, rememberFormattedAmount(inAppPayment)),
    style = MaterialTheme.typography.titleLarge,
    modifier = Modifier.padding(bottom = 6.dp)
  )

  Text(
    text = stringResource(R.string.GatewaySelectorBottomSheet__get_a_s_badge, inAppPayment.data.badge!!.name),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}

@Composable
private fun OneTimeDonationTitleAndSubtitle(inAppPayment: InAppPaymentTable.InAppPayment) {
  Text(
    text = stringResource(R.string.GatewaySelectorBottomSheet__donate_s_to_signal, rememberFormattedAmount(inAppPayment)),
    style = MaterialTheme.typography.titleLarge,
    modifier = Modifier.padding(bottom = 6.dp)
  )

  Text(
    text = pluralStringResource(R.plurals.GatewaySelectorBottomSheet__get_a_s_badge_for_d_days, 30, inAppPayment.data.badge!!.name, 30),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}

@Composable
private fun OneTimeGiftTitleAndSubtitle(inAppPayment: InAppPaymentTable.InAppPayment) {
  Text(
    text = stringResource(R.string.GatewaySelectorBottomSheet__donate_s_to_signal, rememberFormattedAmount(inAppPayment)),
    style = MaterialTheme.typography.titleLarge,
    modifier = Modifier.padding(bottom = 6.dp)
  )

  Text(
    text = stringResource(R.string.GatewaySelectorBottomSheet__donate_for_a_friend),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}

@Composable
private fun rememberFormattedAmount(inAppPayment: InAppPaymentTable.InAppPayment): String {
  val resources = LocalResources.current
  return remember(inAppPayment.data.amount) {
    FiatMoneyUtil.format(resources, inAppPayment.data.amount!!.toFiatMoney())
  }
}

@DayNightPreviews
@Composable
private fun GatewaySelectorBottomSheetContentReadyOneTimeDonationPreview() {
  Previews.BottomSheetContentPreview {
    GatewaySelectorBottomSheetContent(
      state = rememberGatewaySelectorBottomSheetContentPreviewState(InAppPaymentType.ONE_TIME_DONATION),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun GatewaySelectorBottomSheetContentReadyRecurringDonationPreview() {
  Previews.BottomSheetContentPreview {
    GatewaySelectorBottomSheetContent(
      state = rememberGatewaySelectorBottomSheetContentPreviewState(InAppPaymentType.RECURRING_DONATION),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun GatewaySelectorBottomSheetContentReadyOneTimeGiftDonationPreview() {
  Previews.BottomSheetContentPreview {
    GatewaySelectorBottomSheetContent(
      state = rememberGatewaySelectorBottomSheetContentPreviewState(InAppPaymentType.ONE_TIME_GIFT),
      onEvent = {}
    )
  }
}

@Composable
@VisibleForTesting
fun rememberGatewaySelectorBottomSheetContentPreviewState(type: InAppPaymentType): GatewaySelectorState.Ready {
  return remember {
    GatewaySelectorState.Ready(
      inAppPayment = InAppPaymentTable.InAppPayment(
        id = InAppPaymentTable.InAppPaymentId(1),
        type = type,
        state = InAppPaymentTable.State.CREATED,
        insertedAt = 1.milliseconds,
        updatedAt = 1.milliseconds,
        notified = true,
        subscriberId = null,
        endOfPeriod = 0.milliseconds,
        data = InAppPaymentData(
          badge = BadgeList.Badge(
            name = type.name.lowercase()
          ),
          amount = FiatValue(currencyCode = "USD", amount = BigDecimal.TEN.toDecimalValue())
        )
      ),
      gatewayOrderStrategy = GatewayOrderStrategy.getStrategy(
        self = Recipient(
          isResolving = false,
          e164Value = "+15555555555"
        )
      ),
      isGooglePayAvailable = true,
      isPayPalAvailable = true,
      isCreditCardAvailable = true,
      isSEPADebitAvailable = true,
      isIDEALAvailable = true,
      sepaEuroMaximum = FiatMoney(BigDecimal.ONE, Currency.getInstance("USD"))
    )
  }
}
