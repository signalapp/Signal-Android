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
 * Sheet describing how users must upgrade to enable optimized storage.
 */
class UpgradeToEnableOptimizedStorageSheet : UpgradeToPaidTierBottomSheet() {

  @Composable
  override fun UpgradeSheetContent(
    paidBackupType: MessageBackupsType.Paid,
    freeBackupType: MessageBackupsType.Free,
    isSubscribeEnabled: Boolean,
    onSubscribeClick: () -> Unit
  ) {
    UpgradeToEnableOptimizedStorageSheetContent(
      messageBackupsType = paidBackupType,
      isSubscribeEnabled = isSubscribeEnabled,
      onSubscribeClick = onSubscribeClick,
      onCancelClick = {
        dismissAllowingStateLoss()
      }
    )
  }
}

@Composable
private fun UpgradeToEnableOptimizedStorageSheetContent(
  messageBackupsType: MessageBackupsType.Paid,
  isSubscribeEnabled: Boolean,
  onSubscribeClick: () -> Unit = {},
  onCancelClick: () -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
  ) {
    BottomSheets.Handle()

    Image(
      painter = painterResource(id = R.drawable.image_signal_backups),
      contentDescription = null,
      modifier = Modifier
        .padding(top = 8.dp, bottom = 16.dp)
        .size(80.dp)
    )

    Text(
      text = stringResource(id = R.string.UpgradeToEnableOptimizedStorageSheet__upgrade_to_enable_this_feature),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
    )

    Text(
      text = stringResource(id = R.string.UpgradeToEnableOptimizedStorageSheet__storage_optimization_can_only_be_used),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .padding(top = 10.dp, bottom = 28.dp)
        .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
    )

    MessageBackupsTypeBlock(
      messageBackupsType = messageBackupsType,
      isCurrent = false,
      isSelected = false,
      onSelected = {},
      enabled = false,
      iconColors = MessageBackupsTypeIconColors.default().let {
        it.copy(iconColorNormal = it.iconColorSelected)
      },
      modifier = Modifier
        .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
        .padding(bottom = 50.dp)
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
      val formattedPrice = remember(messageBackupsType.pricePerMonth) {
        FiatMoneyUtil.format(resources, messageBackupsType.pricePerMonth, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
      }

      Text(
        text = stringResource(id = R.string.UpgradeToEnableOptimizedStorageSheet__subscribe_for_s_month, formattedPrice)
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
private fun UpgradeToEnableOptimizedStorageSheetContentPreview() {
  Previews.BottomSheetPreview {
    UpgradeToEnableOptimizedStorageSheetContent(
      messageBackupsType = testBackupTypes()[1] as MessageBackupsType.Paid,
      isSubscribeEnabled = true
    )
  }
}
