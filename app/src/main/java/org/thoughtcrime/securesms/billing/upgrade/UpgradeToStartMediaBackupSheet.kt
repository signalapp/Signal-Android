/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.billing.upgrade

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsTypeBlock
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsTypeIconColors
import org.thoughtcrime.securesms.backup.v2.ui.subscription.testBackupTypes
import org.thoughtcrime.securesms.payments.FiatMoneyUtil

/**
 * Bottom sheet notifying user that the media they selected is no longer available. This
 * can occur when a user had a paid tier in the past and had storage optimization enabled,
 * but did not download their media within 30 days of canceling their subscription.
 */
class UpgradeToStartMediaBackupSheet : UpgradeToPaidTierBottomSheet() {
  @Composable
  override fun UpgradeSheetContent(
    paidBackupType: MessageBackupsType.Paid,
    freeBackupType: MessageBackupsType.Free,
    isSubscribeEnabled: Boolean,
    onSubscribeClick: () -> Unit
  ) {
    UpgradeToStartMediaBackupSheetContent(
      paidBackupType = paidBackupType,
      freeBackupType = freeBackupType,
      isSubscribeEnabled = isSubscribeEnabled,
      onSubscribeClick = onSubscribeClick,
      onCancelClick = {
        dismissAllowingStateLoss()
      }
    )
  }
}

@Composable
private fun UpgradeToStartMediaBackupSheetContent(
  paidBackupType: MessageBackupsType.Paid,
  freeBackupType: MessageBackupsType.Free,
  isSubscribeEnabled: Boolean,
  onSubscribeClick: () -> Unit = {},
  onCancelClick: () -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = dimensionResource(R.dimen.core_ui__gutter))
  ) {
    BottomSheets.Handle()

    Image(
      painter = painterResource(R.drawable.image_signal_backups_media),
      contentDescription = null,
      modifier = Modifier
        .size(80.dp)
    )

    Text(
      text = stringResource(R.string.UpgradeToStartMediaBackupSheet__this_media_is_no_longer_available),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(vertical = 16.dp)
    )

    Text(
      text = pluralStringResource(R.plurals.UpgradeToStartMediaBackupSheet__your_current_signal_backup_plan_includes, freeBackupType.mediaRetentionDays, freeBackupType.mediaRetentionDays),
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    MessageBackupsTypeBlock(
      messageBackupsType = paidBackupType,
      isCurrent = false,
      isSelected = false,
      onSelected = {},
      enabled = false,
      modifier = Modifier.padding(top = 24.dp, bottom = 32.dp),
      iconColors = MessageBackupsTypeIconColors.default().let {
        it.copy(iconColorNormal = it.iconColorSelected)
      }
    )

    Buttons.LargePrimary(
      enabled = isSubscribeEnabled,
      onClick = onSubscribeClick,
      modifier = Modifier
        .defaultMinSize(minWidth = 256.dp)
        .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
        .padding(bottom = 8.dp)
    ) {
      val resources = LocalContext.current.resources
      val formattedPrice = remember(paidBackupType.pricePerMonth) {
        FiatMoneyUtil.format(resources, paidBackupType.pricePerMonth, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
      }

      Text(
        text = stringResource(id = R.string.UpgradeToStartMediaBackupSheet__subscribe_for_s_month, formattedPrice)
      )
    }

    TextButton(
      enabled = isSubscribeEnabled,
      onClick = onCancelClick,
      modifier = Modifier
        .defaultMinSize(minWidth = 256.dp)
        .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
        .padding(bottom = 16.dp)
    ) {
      Text(
        text = stringResource(id = android.R.string.cancel)
      )
    }
  }
}

@SignalPreview
@Composable
private fun UpgradeToStartMediaBackupSheetContentPreview() {
  Previews.Preview {
    UpgradeToStartMediaBackupSheetContent(
      paidBackupType = testBackupTypes()[1] as MessageBackupsType.Paid,
      freeBackupType = testBackupTypes()[0] as MessageBackupsType.Free,
      isSubscribeEnabled = true
    )
  }
}
