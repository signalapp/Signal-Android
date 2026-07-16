/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.RtlPreview
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPendingBottomSheetContentPreview
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayOrderStrategy
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewaySelectorBottomSheetContent
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewaySelectorState
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.rememberGatewaySelectorBottomSheetContentPreviewState
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.details.BankTransferDetailsContent
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.details.BankTransferDetailsState
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.details.IBANValidator
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.ideal.IdealTransferDetailsContentPreview
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.mandate.BankTransferScreenPreview
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData.PaymentMethodType

class DonationCheckoutScreenshotTests {
  @PreviewTest
  @DayNightPreviews
  @RtlPreview
  @Composable
  fun GatewaySelectorLoading() {
    GatewaySelectorPreview(GatewaySelectorState.Loading)
  }

  @PreviewTest
  @DayNightPreviews
  @RtlPreview
  @Composable
  fun GatewaySelectorNorthAmerica() {
    GatewaySelectorPreview(
      state = rememberGatewaySelectorBottomSheetContentPreviewState(
        type = InAppPaymentType.ONE_TIME_DONATION,
        gatewayOrderStrategy = GatewayOrderStrategy.forTesting(
          PaymentMethodType.GOOGLE_PAY,
          PaymentMethodType.PAYPAL,
          PaymentMethodType.CARD,
          PaymentMethodType.SEPA_DEBIT,
          PaymentMethodType.IDEAL
        ),
        availableGateways = setOf(
          PaymentMethodType.GOOGLE_PAY,
          PaymentMethodType.PAYPAL,
          PaymentMethodType.CARD
        )
      )
    )
  }

  @PreviewTest
  @DayNightPreviews
  @RtlPreview
  @Composable
  fun GatewaySelectorSepa() {
    GatewaySelectorPreview(
      state = rememberGatewaySelectorBottomSheetContentPreviewState(
        type = InAppPaymentType.RECURRING_DONATION,
        gatewayOrderStrategy = GatewayOrderStrategy.forTesting(
          PaymentMethodType.CARD,
          PaymentMethodType.PAYPAL,
          PaymentMethodType.GOOGLE_PAY,
          PaymentMethodType.SEPA_DEBIT,
          PaymentMethodType.IDEAL
        ),
        availableGateways = setOf(
          PaymentMethodType.CARD,
          PaymentMethodType.PAYPAL,
          PaymentMethodType.GOOGLE_PAY,
          PaymentMethodType.SEPA_DEBIT
        )
      )
    )
  }

  @PreviewTest
  @DayNightPreviews
  @RtlPreview
  @Composable
  fun GatewaySelectorNetherlands() {
    GatewaySelectorPreview(
      state = rememberGatewaySelectorBottomSheetContentPreviewState(
        type = InAppPaymentType.RECURRING_DONATION,
        gatewayOrderStrategy = GatewayOrderStrategy.forTesting(
          PaymentMethodType.IDEAL,
          PaymentMethodType.PAYPAL,
          PaymentMethodType.GOOGLE_PAY,
          PaymentMethodType.CARD,
          PaymentMethodType.SEPA_DEBIT
        ),
        availableGateways = setOf(
          PaymentMethodType.IDEAL,
          PaymentMethodType.PAYPAL,
          PaymentMethodType.GOOGLE_PAY,
          PaymentMethodType.CARD,
          PaymentMethodType.SEPA_DEBIT
        )
      )
    )
  }

  @PreviewTest
  @DayNightPreviews
  @RtlPreview
  @Composable
  fun BankTransferMandate() {
    BankTransferScreenPreview()
  }

  @PreviewTest
  @DayNightPreviews
  @RtlPreview
  @Composable
  fun BankTransferDetails() {
    BankTransferDetailsPreview(displayFindAccountInfoSheet = false)
  }

  @PreviewTest
  @DayNightPreviews
  @RtlPreview
  @Composable
  fun FindAccountInfoSheet() {
    BankTransferDetailsPreview(displayFindAccountInfoSheet = true)
  }

  @PreviewTest
  @DayNightPreviews
  @RtlPreview
  @Composable
  fun IdealTransferDetails() {
    IdealTransferDetailsContentPreview()
  }

  @PreviewTest
  @DayNightPreviews
  @RtlPreview
  @Composable
  fun DonationPending() {
    DonationPendingBottomSheetContentPreview()
  }

  @Composable
  private fun GatewaySelectorPreview(state: GatewaySelectorState) {
    Previews.BottomSheetContentPreview {
      GatewaySelectorBottomSheetContent(
        state = state,
        onEvent = {}
      )
    }
  }

  @Composable
  private fun BankTransferDetailsPreview(displayFindAccountInfoSheet: Boolean) {
    Previews.Preview {
      BankTransferDetailsContent(
        state = BankTransferDetailsState(
          name = "Miles Morales",
          iban = "DE89370400440532013000",
          email = "miles@example.com",
          ibanValidity = IBANValidator.Validity.COMPLETELY_VALID,
          displayFindAccountInfoSheet = displayFindAccountInfoSheet
        ),
        onNavigationClick = {},
        onNameChanged = {},
        onIBANChanged = {},
        onEmailChanged = {},
        setDisplayFindAccountInfoSheet = {},
        onLearnMoreClick = {},
        onDonateClick = {},
        onFocusChanged = { _, _ -> },
        donateLabel = "Donate $5/month"
      )
    }
  }
}
