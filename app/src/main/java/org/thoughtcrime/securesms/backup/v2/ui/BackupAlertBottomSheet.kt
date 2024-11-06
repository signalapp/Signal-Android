/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import android.content.DialogInterface
import android.os.Parcelable
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.billing.launchManageBackupsSubscription
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.jobs.BackupRestoreMediaJob
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.signal.core.ui.R as CoreUiR

/**
 * Notifies the user of an issue with their backup.
 */
class BackupAlertBottomSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    private const val ARG_ALERT = "alert"

    @JvmStatic
    fun create(backupAlert: BackupAlert): BackupAlertBottomSheet {
      return BackupAlertBottomSheet().apply {
        arguments = bundleOf(ARG_ALERT to backupAlert)
      }
    }
  }

  private val backupAlert: BackupAlert by lazy(LazyThreadSafetyMode.NONE) {
    BundleCompat.getParcelable(requireArguments(), ARG_ALERT, BackupAlert::class.java)!!
  }

  @Composable
  override fun SheetContent() {
    var pricePerMonth by remember { mutableStateOf("-") }
    val resources = LocalContext.current.resources

    LaunchedEffect(Unit) {
      val price = AppDependencies.billingApi.queryProduct()?.price ?: return@LaunchedEffect
      pricePerMonth = FiatMoneyUtil.format(resources, price, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
    }

    BackupAlertSheetContent(
      backupAlert = backupAlert,
      onPrimaryActionClick = this::performPrimaryAction,
      onSecondaryActionClick = this::performSecondaryAction
    )
  }

  @Stable
  private fun performPrimaryAction() {
    when (backupAlert) {
      is BackupAlert.CouldNotCompleteBackup -> {
        BackupMessagesJob.enqueue()
        startActivity(AppSettingsActivity.remoteBackups(requireContext()))
      }

      BackupAlert.FailedToRenew -> launchManageBackupsSubscription()
      BackupAlert.MediaBackupsAreOff, BackupAlert.MediaWillBeDeletedToday -> {
        performFullMediaDownload()
      }

      is BackupAlert.DiskFull -> Unit
    }

    dismissAllowingStateLoss()
  }

  @Stable
  private fun performSecondaryAction() {
    when (backupAlert) {
      is BackupAlert.CouldNotCompleteBackup -> Unit
      BackupAlert.FailedToRenew -> Unit
      BackupAlert.MediaBackupsAreOff -> {
        // TODO [backups] - Silence and remind on last day
      }

      BackupAlert.MediaWillBeDeletedToday -> {
        displayLastChanceDialog()
      }

      is BackupAlert.DiskFull -> {
        displaySkipRestoreDialog()
      }
    }

    dismissAllowingStateLoss()
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)

    when (backupAlert) {
      is BackupAlert.CouldNotCompleteBackup -> BackupRepository.markBackupFailedSheetDismissed()
      else -> Unit
    }
  }

  private fun displayLastChanceDialog() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.BackupAlertBottomSheet__media_will_be_deleted)
      .setMessage(R.string.BackupAlertBottomSheet__the_media_stored_in_your_backup)
      .setPositiveButton(R.string.BackupAlertBottomSheet__download) { _, _ ->
        performFullMediaDownload()
      }
      .setNegativeButton(R.string.BackupAlertBottomSheet__dont_download, null)
      .show()
  }

  private fun displaySkipRestoreDialog() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle((R.string.BackupAlertBottomSheet__skip_restore_question))
      .setMessage(R.string.BackupAlertBottomSheet__if_you_skip_restore)
      .setPositiveButton(R.string.BackupAlertBottomSheet__skip) { _, _ ->
        BackupRepository.skipMediaRestore()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .create()
      .apply {
        setOnShowListener {
          getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.signal_colorError))
        }
      }
      .show()
  }

  private fun performFullMediaDownload() {
    // TODO [backups] -- We need to force this to download everything
    AppDependencies.jobManager.add(BackupRestoreMediaJob())
  }
}

@Composable
private fun BackupAlertSheetContent(
  backupAlert: BackupAlert,
  pricePerMonth: String = "",
  onPrimaryActionClick: () -> Unit = {},
  onSecondaryActionClick: () -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter))
  ) {
    BottomSheets.Handle()

    Spacer(modifier = Modifier.size(26.dp))

    when (backupAlert) {
      BackupAlert.FailedToRenew, BackupAlert.MediaBackupsAreOff -> {
        Box {
          Image(
            painter = painterResource(id = R.drawable.image_signal_backups),
            contentDescription = null,
            modifier = Modifier
              .size(80.dp)
              .padding(2.dp)
          )
          Icon(
            painter = painterResource(R.drawable.symbol_error_circle_fill_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.align(Alignment.TopEnd)
          )
        }
      }

      else -> {
        val iconColors = rememberBackupsIconColors(backupAlert = backupAlert)
        Icon(
          painter = painterResource(id = R.drawable.symbol_backup_light),
          contentDescription = null,
          tint = iconColors.foreground,
          modifier = Modifier
            .size(80.dp)
            .background(color = iconColors.background, shape = CircleShape)
            .padding(20.dp)
        )
      }
    }

    Text(
      text = titleString(backupAlert = backupAlert),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )

    when (backupAlert) {
      is BackupAlert.CouldNotCompleteBackup -> CouldNotCompleteBackup(
        daysSinceLastBackup = backupAlert.daysSinceLastBackup
      )

      BackupAlert.FailedToRenew -> PaymentProcessingBody()
      BackupAlert.MediaBackupsAreOff -> MediaBackupsAreOffBody(30) // TODO [backups] -- Get this value from backend
      BackupAlert.MediaWillBeDeletedToday -> MediaWillBeDeletedTodayBody()
      is BackupAlert.DiskFull -> DiskFullBody(requiredSpace = backupAlert.requiredSpace)
    }

    val secondaryActionResource = rememberSecondaryActionResource(backupAlert = backupAlert)
    val padBottom = if (secondaryActionResource > 0) 16.dp else 56.dp

    Buttons.LargeTonal(
      onClick = onPrimaryActionClick,
      modifier = Modifier
        .defaultMinSize(minWidth = 220.dp)
        .padding(bottom = padBottom)
    ) {
      Text(text = primaryActionString(backupAlert = backupAlert, pricePerMonth = pricePerMonth))
    }

    if (secondaryActionResource > 0) {
      TextButton(onClick = onSecondaryActionClick, modifier = Modifier.padding(bottom = 32.dp)) {
        Text(text = stringResource(id = secondaryActionResource))
      }
    }
  }
}

@Composable
private fun CouldNotCompleteBackup(
  daysSinceLastBackup: Int
) {
  Text(
    text = stringResource(id = R.string.BackupAlertBottomSheet__your_device_hasnt, daysSinceLastBackup),
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 60.dp)
  )
}

@Composable
private fun PaymentProcessingBody() {
  Text(
    text = stringResource(id = R.string.BackupAlertBottomSheet__check_to_make_sure_your_payment_method),
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 60.dp)
  )
}

@Composable
private fun MediaBackupsAreOffBody(
  daysUntilDeletion: Long
) {
  Text(
    text = pluralStringResource(id = R.plurals.BackupAlertBottomSheet__your_backup_plan_has_expired, daysUntilDeletion.toInt(), daysUntilDeletion),
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 24.dp)
  )

  Text(
    text = stringResource(id = R.string.BackupAlertBottomSheet__you_can_begin_paying_for_backups_again),
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 36.dp)
  )
}

@Composable
private fun MediaWillBeDeletedTodayBody() {
  Text(
    text = stringResource(id = R.string.BackupAlertBottomSheet__your_signal_media_backup_plan_has_been),
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 24.dp)
  )

  Text(
    text = stringResource(id = R.string.BackupAlertBottomSheet__you_can_begin_paying_for_backups_again),
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 36.dp)
  )
}

@Composable
private fun DiskFullBody(requiredSpace: String) {
  Text(
    text = stringResource(id = R.string.BackupAlertBottomSheet__to_finish_downloading_your_signal_backup, requiredSpace),
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 24.dp)
  )

  Text(
    text = stringResource(R.string.BackupAlertBottomSheet__to_free_up_space_offload),
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 36.dp)
  )
}

@Composable
private fun rememberBackupsIconColors(backupAlert: BackupAlert): BackupsIconColors {
  return remember(backupAlert) {
    when (backupAlert) {
      BackupAlert.FailedToRenew, BackupAlert.MediaBackupsAreOff -> error("Not icon-based options.")
      is BackupAlert.CouldNotCompleteBackup, is BackupAlert.DiskFull -> BackupsIconColors.Warning
      BackupAlert.MediaWillBeDeletedToday -> BackupsIconColors.Error
    }
  }
}

@Composable
private fun titleString(backupAlert: BackupAlert): String {
  return when (backupAlert) {
    is BackupAlert.CouldNotCompleteBackup -> stringResource(R.string.BackupAlertBottomSheet__couldnt_complete_backup)
    BackupAlert.FailedToRenew -> stringResource(R.string.BackupAlertBottomSheet__your_backups_subscription_failed_to_renew)
    BackupAlert.MediaBackupsAreOff -> stringResource(R.string.BackupAlertBottomSheet__your_backups_subscription_expired)
    BackupAlert.MediaWillBeDeletedToday -> stringResource(R.string.BackupAlertBottomSheet__your_media_will_be_deleted_today)
    is BackupAlert.DiskFull -> stringResource(R.string.BackupAlertBottomSheet__free_up_s_on_this_device, backupAlert.requiredSpace)
  }
}

@Composable
private fun primaryActionString(
  backupAlert: BackupAlert,
  pricePerMonth: String
): String {
  return when (backupAlert) {
    is BackupAlert.CouldNotCompleteBackup -> stringResource(R.string.BackupAlertBottomSheet__back_up_now)
    BackupAlert.FailedToRenew -> stringResource(R.string.BackupAlertBottomSheet__manage_subscription)
    BackupAlert.MediaBackupsAreOff -> stringResource(R.string.BackupAlertBottomSheet__subscribe_for_s_month, pricePerMonth)
    BackupAlert.MediaWillBeDeletedToday -> stringResource(R.string.BackupAlertBottomSheet__download_media_now)
    is BackupAlert.DiskFull -> stringResource(R.string.BackupAlertBottomSheet__got_it)
  }
}

@Composable
private fun rememberSecondaryActionResource(backupAlert: BackupAlert): Int {
  return remember(backupAlert) {
    when (backupAlert) {
      is BackupAlert.CouldNotCompleteBackup -> R.string.BackupAlertBottomSheet__try_later
      BackupAlert.FailedToRenew -> R.string.BackupAlertBottomSheet__not_now
      BackupAlert.MediaBackupsAreOff -> R.string.BackupAlertBottomSheet__not_now
      BackupAlert.MediaWillBeDeletedToday -> R.string.BackupAlertBottomSheet__dont_download_media
      is BackupAlert.DiskFull -> R.string.BackupAlertBottomSheet__skip_restore
    }
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewGeneric() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.CouldNotCompleteBackup(daysSinceLastBackup = 7)
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewPayment() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.FailedToRenew
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewMedia() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.MediaBackupsAreOff,
      pricePerMonth = "$2.99"
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewDelete() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.MediaWillBeDeletedToday
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewDiskFull() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.DiskFull(requiredSpace = "12GB")
    )
  }
}

/**
 * All necessary information to display the sheet should be handed in through the specific alert.
 */
@Parcelize
sealed class BackupAlert : Parcelable {

  data class CouldNotCompleteBackup(
    val daysSinceLastBackup: Int
  ) : BackupAlert()

  data object FailedToRenew : BackupAlert()

  data object MediaBackupsAreOff : BackupAlert()

  data object MediaWillBeDeletedToday : BackupAlert()

  /**
   * The disk is full. Contains a value representing the amount of space that must be freed.
   *
   */
  data class DiskFull(val requiredSpace: String) : BackupAlert()
}
