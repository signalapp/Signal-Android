/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import android.os.Parcelable
import androidx.annotation.StringRes
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
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.billing.launchManageBackupsSubscription
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.jobs.BackupRestoreMediaJob
import org.thoughtcrime.securesms.payments.FiatMoneyUtil

/**
 * Notifies the user of an issue with their backup.
 */
class BackupAlertBottomSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    private const val ARG_ALERT = "alert"

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
      BackupAlert.COULD_NOT_COMPLETE_BACKUP -> {
        BackupMessagesJob.enqueue()
        startActivity(AppSettingsActivity.remoteBackups(requireContext()))
      }

      BackupAlert.PAYMENT_PROCESSING -> launchManageBackupsSubscription()
      BackupAlert.MEDIA_BACKUPS_ARE_OFF, BackupAlert.MEDIA_WILL_BE_DELETED_TODAY -> {
        performFullMediaDownload()
      }

      BackupAlert.DISK_FULL -> Unit
    }

    dismissAllowingStateLoss()
  }

  @Stable
  private fun performSecondaryAction() {
    when (backupAlert) {
      BackupAlert.COULD_NOT_COMPLETE_BACKUP -> {
        // TODO [message-backups] - Dismiss and notify later
      }

      BackupAlert.PAYMENT_PROCESSING -> Unit
      BackupAlert.MEDIA_BACKUPS_ARE_OFF -> {
        // TODO [message-backups] - Silence and remind on last day
      }

      BackupAlert.MEDIA_WILL_BE_DELETED_TODAY -> {
        displayLastChanceDialog()
      }

      BackupAlert.DISK_FULL -> Unit
    }

    dismissAllowingStateLoss()
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

  private fun performFullMediaDownload() {
    // TODO [message-backups] -- We need to force this to download everything
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
      .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
  ) {
    BottomSheets.Handle()

    Spacer(modifier = Modifier.size(26.dp))

    when (backupAlert) {
      BackupAlert.PAYMENT_PROCESSING, BackupAlert.MEDIA_BACKUPS_ARE_OFF -> {
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
      text = stringResource(id = rememberTitleResource(backupAlert = backupAlert)),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )

    when (backupAlert) {
      BackupAlert.COULD_NOT_COMPLETE_BACKUP -> CouldNotCompleteBackup(
        daysSinceLastBackup = 7 // TODO [message-backups]
      )

      BackupAlert.PAYMENT_PROCESSING -> PaymentProcessingBody()
      BackupAlert.MEDIA_BACKUPS_ARE_OFF -> MediaBackupsAreOffBody(30) // TODO [message-backups] -- Get this value from backend
      BackupAlert.MEDIA_WILL_BE_DELETED_TODAY -> MediaWillBeDeletedTodayBody()
      BackupAlert.DISK_FULL -> DiskFullBody(
        requiredSpace = "12 GB", // TODO [message-backups] Where does this value come from?
        daysUntilDeletion = 30 // TODO [message-backups] Where does this value come from?
      )
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
private fun DiskFullBody(
  requiredSpace: String,
  daysUntilDeletion: Long
) {
  Text(
    text = stringResource(id = R.string.BackupAlertBottomSheet__your_device_does_not_have_enough_free_space, requiredSpace),
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 24.dp)
  )

  Text(
    text = pluralStringResource(id = R.plurals.BackupAlertBottomSheet__if_you_choose_skip, daysUntilDeletion.toInt(), daysUntilDeletion), // TODO [message-backups] Learn More link
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 36.dp)
  )
}

@Composable
private fun rememberBackupsIconColors(backupAlert: BackupAlert): BackupsIconColors {
  return remember(backupAlert) {
    when (backupAlert) {
      BackupAlert.PAYMENT_PROCESSING, BackupAlert.MEDIA_BACKUPS_ARE_OFF -> error("Not icon-based options.")
      BackupAlert.COULD_NOT_COMPLETE_BACKUP, BackupAlert.DISK_FULL -> BackupsIconColors.Warning
      BackupAlert.MEDIA_WILL_BE_DELETED_TODAY -> BackupsIconColors.Error
    }
  }
}

@Composable
@StringRes
private fun rememberTitleResource(backupAlert: BackupAlert): Int {
  return remember(backupAlert) {
    when (backupAlert) {
      BackupAlert.COULD_NOT_COMPLETE_BACKUP -> R.string.BackupAlertBottomSheet__couldnt_complete_backup
      BackupAlert.PAYMENT_PROCESSING -> R.string.BackupAlertBottomSheet__your_backups_subscription_failed_to_renew
      BackupAlert.MEDIA_BACKUPS_ARE_OFF -> R.string.BackupAlertBottomSheet__your_backups_subscription_expired
      BackupAlert.MEDIA_WILL_BE_DELETED_TODAY -> R.string.BackupAlertBottomSheet__your_media_will_be_deleted_today
      BackupAlert.DISK_FULL -> R.string.BackupAlertBottomSheet__cant_complete_download
    }
  }
}

@Composable
private fun primaryActionString(
  backupAlert: BackupAlert,
  pricePerMonth: String
): String {
  return when (backupAlert) {
    BackupAlert.COULD_NOT_COMPLETE_BACKUP -> stringResource(android.R.string.ok) // TODO [message-backups] -- Finalized copy
    BackupAlert.PAYMENT_PROCESSING -> stringResource(R.string.BackupAlertBottomSheet__manage_subscription)
    BackupAlert.MEDIA_BACKUPS_ARE_OFF -> stringResource(R.string.BackupAlertBottomSheet__subscribe_for_s_month, pricePerMonth)
    BackupAlert.MEDIA_WILL_BE_DELETED_TODAY -> stringResource(R.string.BackupAlertBottomSheet__download_media_now)
    BackupAlert.DISK_FULL -> stringResource(android.R.string.ok)
  }
}

@Composable
private fun rememberSecondaryActionResource(backupAlert: BackupAlert): Int {
  return remember(backupAlert) {
    when (backupAlert) {
      BackupAlert.COULD_NOT_COMPLETE_BACKUP -> android.R.string.cancel // TODO [message-backups] -- Finalized copy
      BackupAlert.PAYMENT_PROCESSING -> R.string.BackupAlertBottomSheet__not_now
      BackupAlert.MEDIA_BACKUPS_ARE_OFF -> R.string.BackupAlertBottomSheet__not_now
      BackupAlert.MEDIA_WILL_BE_DELETED_TODAY -> R.string.BackupAlertBottomSheet__dont_download_media
      BackupAlert.DISK_FULL -> R.string.BackupAlertBottomSheet__skip
    }
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewGeneric() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.COULD_NOT_COMPLETE_BACKUP
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewPayment() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.PAYMENT_PROCESSING
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewMedia() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.MEDIA_BACKUPS_ARE_OFF,
      pricePerMonth = "$2.99"
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewDelete() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.MEDIA_WILL_BE_DELETED_TODAY
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewDiskFull() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.DISK_FULL
    )
  }
}

@Parcelize
enum class BackupAlert : Parcelable {
  COULD_NOT_COMPLETE_BACKUP,
  PAYMENT_PROCESSING,
  MEDIA_BACKUPS_ARE_OFF,
  MEDIA_WILL_BE_DELETED_TODAY,
  DISK_FULL
}
