/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.BackupsIconColors
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsTypeBlock
import org.thoughtcrime.securesms.backup.v2.ui.subscription.testBackupTypes
import org.thoughtcrime.securesms.components.settings.app.subscription.MessageBackupsCheckoutLauncher.createBackupsCheckoutLauncher
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment

/**
 * Sheet describing how users must upgrade to enable optimized storage.
 */
class UpgradeToEnableOptimizedStorageSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 1f

  private val viewModel: UpgradeToEnableOptimizedStorageViewModel by viewModels()

  private lateinit var checkoutLauncher: ActivityResultLauncher<MessageBackupTier?>

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    checkoutLauncher = createBackupsCheckoutLauncher()
  }

  @Composable
  override fun SheetContent() {
    val type by viewModel.messageBackupsType
    UpgradeToEnableOptimizedStorageSheetContent(
      messageBackupsType = type,
      onUpgradeNowClick = {
        checkoutLauncher.launch(MessageBackupTier.PAID)
        dismissAllowingStateLoss()
      },
      onCancelClick = {
        dismissAllowingStateLoss()
      }
    )
  }
}

@Composable
private fun UpgradeToEnableOptimizedStorageSheetContent(
  messageBackupsType: MessageBackupsType?,
  onUpgradeNowClick: () -> Unit = {},
  onCancelClick: () -> Unit = {}
) {
  if (messageBackupsType == null) {
    // TODO [message-backups] -- network error?
    return
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
  ) {
    BottomSheets.Handle()

    Icon(
      painter = painterResource(id = R.drawable.symbol_backup_light),
      contentDescription = null,
      tint = BackupsIconColors.Normal.foreground,
      modifier = Modifier
        .padding(top = 8.dp, bottom = 12.dp)
        .size(88.dp)
        .background(
          color = BackupsIconColors.Normal.background,
          shape = CircleShape
        )
        .padding(20.dp)
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
      modifier = Modifier
        .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
        .padding(bottom = 50.dp)
    )

    Buttons.LargePrimary(
      onClick = onUpgradeNowClick,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
        .padding(bottom = 8.dp)
    ) {
      Text(
        text = stringResource(id = R.string.UpgradeToEnableOptimizedStorageSheet__upgrade_now)
      )
    }

    TextButton(
      onClick = onCancelClick,
      modifier = Modifier
        .fillMaxWidth()
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
      messageBackupsType = testBackupTypes()[1]
    )
  }
}
