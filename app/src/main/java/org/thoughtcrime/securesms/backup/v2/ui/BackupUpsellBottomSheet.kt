/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.gibiBytes
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.billing.upgrade.UpgradeToPaidTierBottomSheet
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import java.math.BigDecimal
import java.util.Currency
import kotlin.time.Duration.Companion.days
import org.signal.core.ui.R as CoreUiR

/**
 * Bottom sheet that upsells paid backup plans to users.
 */
class BackupUpsellBottomSheet : UpgradeToPaidTierBottomSheet() {

  companion object {
    private const val ARG_SHOW_POST_PAYMENT = "show_post_payment"

    @JvmStatic
    fun create(showPostPaymentSheet: Boolean): DialogFragment {
      return BackupUpsellBottomSheet().apply {
        arguments = bundleOf(ARG_SHOW_POST_PAYMENT to showPostPaymentSheet)
      }
    }
  }

  private val showPostPaymentSheet: Boolean by lazy(LazyThreadSafetyMode.NONE) {
    requireArguments().getBoolean(ARG_SHOW_POST_PAYMENT, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    if (showPostPaymentSheet) {
      parentFragmentManager.setFragmentResultListener(RESULT_KEY, requireActivity()) { _, bundle ->
        if (bundle.getBoolean(RESULT_KEY, false)) {
          BackupSetupCompleteBottomSheet().show(parentFragmentManager, "backup_setup_complete")
        }
      }
    }
  }

  @Composable
  override fun UpgradeSheetContent(
    paidBackupType: MessageBackupsType.Paid,
    freeBackupType: MessageBackupsType.Free,
    isSubscribeEnabled: Boolean,
    onSubscribeClick: () -> Unit
  ) {
    UpsellSheetContent(
      paidBackupType = paidBackupType,
      isSubscribeEnabled = isSubscribeEnabled,
      onSubscribeClick = onSubscribeClick,
      onNoThanksClick = { dismissAllowingStateLoss() }
    )
  }
}

@Composable
private fun UpsellSheetContent(
  paidBackupType: MessageBackupsType.Paid,
  isSubscribeEnabled: Boolean,
  onSubscribeClick: () -> Unit,
  onNoThanksClick: () -> Unit
) {
  val resources = LocalContext.current.resources
  val pricePerMonth = remember(paidBackupType) {
    FiatMoneyUtil.format(resources, paidBackupType.pricePerMonth, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter))
  ) {
    BottomSheets.Handle()

    Spacer(modifier = Modifier.size(26.dp))

    Image(
      imageVector = ImageVector.vectorResource(id = R.drawable.image_signal_backups),
      contentDescription = null,
      modifier = Modifier
        .size(80.dp)
        .padding(2.dp)
    )

    Text(
      text = stringResource(R.string.BackupUpsellBottomSheet__title),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
    )

    Text(
      text = stringResource(R.string.BackupUpsellBottomSheet__body),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 24.dp)
    )

    FeatureCard(pricePerMonth = pricePerMonth)

    Buttons.LargeTonal(
      enabled = isSubscribeEnabled,
      onClick = onSubscribeClick,
      modifier = Modifier
        .defaultMinSize(minWidth = 220.dp)
        .padding(bottom = 16.dp)
    ) {
      Text(text = stringResource(R.string.BackupUpsellBottomSheet__subscribe_for, pricePerMonth))
    }

    TextButton(
      enabled = isSubscribeEnabled,
      onClick = onNoThanksClick,
      modifier = Modifier.padding(bottom = 32.dp)
    ) {
      Text(text = stringResource(R.string.BackupUpsellBottomSheet__no_thanks))
    }
  }
}

@Composable
private fun FeatureCard(pricePerMonth: String) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 24.dp)
      .background(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
      )
      .padding(16.dp)
  ) {
    Text(
      text = stringResource(R.string.BackupUpsellBottomSheet__price_per_month, pricePerMonth),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )

    Text(
      text = stringResource(R.string.BackupUpsellBottomSheet__text_and_all_media),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 12.dp)
    )

    FeatureBullet(text = stringResource(R.string.BackupUpsellBottomSheet__full_text_media_backup))
    FeatureBullet(text = stringResource(R.string.BackupUpsellBottomSheet__storage_100gb))
    FeatureBullet(text = stringResource(R.string.BackupUpsellBottomSheet__save_on_device_storage))
    FeatureBullet(text = stringResource(R.string.BackupUpsellBottomSheet__thanks_for_supporting))
  }
}

@Composable
private fun FeatureBullet(text: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.padding(vertical = 2.dp)
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(id = CoreUiR.drawable.symbol_check_24),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp)
    )
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@DayNightPreviews
@Composable
private fun BackupUpsellBottomSheetPreview() {
  Previews.BottomSheetContentPreview {
    UpsellSheetContent(
      paidBackupType = MessageBackupsType.Paid(
        pricePerMonth = FiatMoney(BigDecimal("1.99"), Currency.getInstance("USD")),
        mediaTtl = 30.days,
        storageAllowanceBytes = 100.gibiBytes.inWholeBytes
      ),
      isSubscribeEnabled = true,
      onSubscribeClick = {},
      onNoThanksClick = {}
    )
  }
}
