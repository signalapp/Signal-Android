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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.billing.launchManageBackupsSubscription
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.keyvalue.protos.BackupDownloadNotifierState
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.signal.core.ui.R as CoreUiR

/**
 * Notifies the user of an issue with their backup.
 */
class BackupAlertBottomSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.75f

  companion object {
    private const val ARG_ALERT = "alert"

    @JvmStatic
    fun create(backupAlert: BackupAlert): DialogFragment {
      return if (backupAlert is BackupAlert.MediaBackupsAreOff) {
        MediaBackupsAreOffBottomSheet()
      } else {
        BackupAlertBottomSheet()
      }.apply {
        arguments = bundleOf(ARG_ALERT to backupAlert)
      }
    }
  }

  private val backupAlert: BackupAlert by lazy(LazyThreadSafetyMode.NONE) {
    BundleCompat.getParcelable(requireArguments(), ARG_ALERT, BackupAlert::class.java)!!
  }

  @Composable
  override fun SheetContent() {
    val performPrimaryAction = remember(backupAlert) {
      createPrimaryAction()
    }

    BackupAlertSheetContent(
      backupAlert = backupAlert,
      onPrimaryActionClick = performPrimaryAction,
      onSecondaryActionClick = this::performSecondaryAction
    )
  }

  @Stable
  private fun createPrimaryAction(): () -> Unit = {
    when (backupAlert) {
      is BackupAlert.CouldNotCompleteBackup -> {
        BackupMessagesJob.enqueue()
        startActivity(AppSettingsActivity.remoteBackups(requireContext()))
      }

      BackupAlert.FailedToRenew -> launchManageBackupsSubscription()
      is BackupAlert.MediaBackupsAreOff -> error("Use MediaBackupsAreOffBottomSheet instead.")

      is BackupAlert.DownloadYourBackupData -> {
        performFullMediaDownload()
      }

      is BackupAlert.DiskFull -> Unit
      is BackupAlert.BackupFailed ->
        PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext())

      BackupAlert.CouldNotRedeemBackup -> Unit
      BackupAlert.ExpiredAndDowngraded -> {
        startActivity(AppSettingsActivity.remoteBackups(requireContext()))
      }
    }

    dismissAllowingStateLoss()
  }

  @Stable
  private fun performSecondaryAction() {
    when (backupAlert) {
      BackupAlert.ExpiredAndDowngraded -> Unit
      is BackupAlert.CouldNotCompleteBackup -> Unit
      BackupAlert.FailedToRenew -> Unit
      is BackupAlert.MediaBackupsAreOff -> error("Use MediaBackupsAreOffBottomSheet instead.")
      is BackupAlert.DownloadYourBackupData -> Unit
      is BackupAlert.DiskFull -> {
        displaySkipRestoreDialog()
      }
      BackupAlert.BackupFailed -> CommunicationActions.openBrowserLink(requireContext(), requireContext().getString(R.string.backup_failed_support_url))
      BackupAlert.CouldNotRedeemBackup -> CommunicationActions.openBrowserLink(requireContext(), requireContext().getString(R.string.backup_support_url)) // TODO [backups] final url
    }

    dismissAllowingStateLoss()
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)

    when (backupAlert) {
      is BackupAlert.CouldNotCompleteBackup, BackupAlert.BackupFailed -> BackupRepository.markBackupFailedSheetDismissed()
      is BackupAlert.DownloadYourBackupData -> BackupRepository.snoozeDownloadYourBackupData()
      is BackupAlert.ExpiredAndDowngraded -> BackupRepository.markBackupExpiredAndDowngradedSheetDismissed()
      else -> Unit
    }
  }

  private fun displaySkipRestoreDialog() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle((R.string.BackupAlertBottomSheet__skip_restore_question))
      .setMessage(R.string.BackupAlertBottomSheet__if_you_skip_restore_the)
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
    BackupRepository.resumeMediaRestore()
  }
}

@Composable
fun BackupAlertSheetContent(
  backupAlert: BackupAlert,
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
      is BackupAlert.MediaBackupsAreOff -> error("Use MediaBackupsAreOffBottomSheet instead.")
      BackupAlert.FailedToRenew, BackupAlert.ExpiredAndDowngraded -> {
        Box {
          Image(
            imageVector = ImageVector.vectorResource(id = R.drawable.image_signal_backups),
            contentDescription = null,
            modifier = Modifier
              .size(80.dp)
              .padding(2.dp)
          )
          Icon(
            imageVector = ImageVector.vectorResource(R.drawable.symbol_error_circle_fill_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.align(Alignment.TopEnd)
          )
        }
      }

      else -> {
        val iconColors = rememberBackupsIconColors(backupAlert = backupAlert)
        Icon(
          imageVector = ImageVector.vectorResource(id = R.drawable.symbol_backup_light),
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
      is BackupAlert.DownloadYourBackupData -> DownloadYourBackupData(backupAlert.formattedSize)
      is BackupAlert.DiskFull -> DiskFullBody(requiredSpace = backupAlert.requiredSpace)
      BackupAlert.BackupFailed -> BackupFailedBody()
      BackupAlert.CouldNotRedeemBackup -> CouldNotRedeemBackup()
      is BackupAlert.MediaBackupsAreOff -> error("Use MediaBackupsAreOffBottomSheet instead.")
      BackupAlert.ExpiredAndDowngraded -> SubscriptionExpired()
    }

    val secondaryActionResource = rememberSecondaryActionResource(backupAlert = backupAlert)
    val padBottom = if (secondaryActionResource > 0) 16.dp else 56.dp

    Buttons.LargeTonal(
      onClick = onPrimaryActionClick,
      modifier = Modifier
        .defaultMinSize(minWidth = 220.dp)
        .padding(bottom = padBottom)
    ) {
      Text(text = primaryActionString(backupAlert = backupAlert))
    }

    if (secondaryActionResource > 0) {
      TextButton(
        onClick = onSecondaryActionClick,
        modifier = Modifier.padding(bottom = 32.dp)
      ) {
        Text(text = stringResource(id = secondaryActionResource))
      }
    }
  }
}

@Composable
private fun CouldNotRedeemBackup() {
  Text(
    text = stringResource(R.string.BackupAlertBottomSheet__too_many_devices_have_tried),
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 16.dp)
  )

  Row(
    modifier = Modifier
      .height(IntrinsicSize.Min)
      .padding(horizontal = 35.dp)
  ) {
    Box(
      modifier = Modifier
        .width(4.dp)
        .fillMaxHeight()
        .padding(vertical = 2.dp)
        .background(color = SignalTheme.colors.colorTransparentInverse2)
    )

    Text(
      text = stringResource(R.string.BackupAlertBottomSheet__reregistered_your_signal_account),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 12.dp)
    )
  }

  Row(
    modifier = Modifier
      .height(IntrinsicSize.Min)
      .padding(horizontal = 35.dp)
      .padding(top = 12.dp, bottom = 40.dp)
  ) {
    Box(
      modifier = Modifier
        .width(4.dp)
        .fillMaxHeight()
        .padding(vertical = 2.dp)
        .background(color = SignalTheme.colors.colorTransparentInverse2)
    )

    Text(
      text = stringResource(R.string.BackupAlertBottomSheet__have_too_many_devices_using_the_same_subscription),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 12.dp)
    )
  }
}

@Composable
private fun SubscriptionExpired() {
  Text(
    text = stringResource(id = R.string.BackupAlertBottomSheet__your_subscription_couldnt_be_renewed),
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 24.dp)
  )

  Text(
    text = stringResource(id = R.string.BackupAlertBottomSheet__youll_continue_to_have_access_to_the_free),
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 36.dp)
  )
}

@Composable
private fun CouldNotCompleteBackup(
  daysSinceLastBackup: Int
) {
  Text(
    text = pluralStringResource(id = R.plurals.BackupAlertBottomSheet__your_device_hasnt, daysSinceLastBackup, daysSinceLastBackup),
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
private fun DownloadYourBackupData(formattedSize: String) {
  Text(
    text = stringResource(id = R.string.BackupAlertBottomSheet__you_have_s_of_media_thats_not_on_this_device, formattedSize),
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
private fun BackupFailedBody() {
  Text(
    text = stringResource(id = R.string.BackupAlertBottomSheet__an_error_occurred),
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(bottom = 36.dp)
  )
}

@Composable
private fun rememberBackupsIconColors(backupAlert: BackupAlert): BackupsIconColors {
  return remember(backupAlert) {
    when (backupAlert) {
      BackupAlert.ExpiredAndDowngraded, BackupAlert.FailedToRenew, is BackupAlert.MediaBackupsAreOff -> error("Not icon-based options.")
      is BackupAlert.CouldNotCompleteBackup, BackupAlert.BackupFailed, is BackupAlert.DiskFull, BackupAlert.CouldNotRedeemBackup -> BackupsIconColors.Warning
      is BackupAlert.DownloadYourBackupData -> BackupsIconColors.Error
    }
  }
}

@Composable
private fun titleString(backupAlert: BackupAlert): String {
  return when (backupAlert) {
    is BackupAlert.CouldNotCompleteBackup -> stringResource(R.string.BackupAlertBottomSheet__couldnt_complete_backup)
    BackupAlert.FailedToRenew -> stringResource(R.string.BackupAlertBottomSheet__your_backups_subscription_failed_to_renew)
    is BackupAlert.MediaBackupsAreOff -> error("Use MediaBackupsAreOffBottomSheet instead.")
    is BackupAlert.DownloadYourBackupData -> {
      if (backupAlert.isLastDay) {
        stringResource(R.string.BackupAlertBottomSheet__download_your_backup_data_today)
      } else {
        stringResource(R.string.BackupAlertBottomSheet__download_your_backup_data)
      }
    }
    is BackupAlert.DiskFull -> stringResource(R.string.BackupAlertBottomSheet__free_up_s_on_this_device, backupAlert.requiredSpace)
    BackupAlert.BackupFailed -> stringResource(R.string.BackupAlertBottomSheet__backup_failed)
    BackupAlert.CouldNotRedeemBackup -> stringResource(R.string.BackupAlertBottomSheet__couldnt_redeem_your_backups_subscription)
    BackupAlert.ExpiredAndDowngraded -> stringResource(R.string.BackupAlertBottomSheet__your_backups_subscription_has_expired)
  }
}

@Composable
private fun primaryActionString(
  backupAlert: BackupAlert
): String {
  return when (backupAlert) {
    is BackupAlert.CouldNotCompleteBackup -> stringResource(R.string.BackupAlertBottomSheet__back_up_now)
    BackupAlert.FailedToRenew -> stringResource(R.string.BackupAlertBottomSheet__manage_subscription)
    is BackupAlert.MediaBackupsAreOff -> error("Not supported.")
    is BackupAlert.DownloadYourBackupData -> stringResource(R.string.BackupAlertBottomSheet__download_backup_now)
    is BackupAlert.DiskFull -> stringResource(R.string.BackupAlertBottomSheet__got_it)
    is BackupAlert.BackupFailed -> stringResource(R.string.BackupAlertBottomSheet__check_for_update)
    BackupAlert.CouldNotRedeemBackup -> stringResource(R.string.BackupAlertBottomSheet__got_it)
    BackupAlert.ExpiredAndDowngraded -> stringResource(R.string.BackupAlertBottomSheet__manage_backups)
  }
}

@Composable
private fun rememberSecondaryActionResource(backupAlert: BackupAlert): Int {
  return remember(backupAlert) {
    when (backupAlert) {
      is BackupAlert.CouldNotCompleteBackup -> R.string.BackupAlertBottomSheet__try_later
      BackupAlert.FailedToRenew, BackupAlert.ExpiredAndDowngraded -> R.string.BackupAlertBottomSheet__not_now
      is BackupAlert.MediaBackupsAreOff -> error("Not supported.")
      is BackupAlert.DownloadYourBackupData -> R.string.BackupAlertBottomSheet__dont_download_backup
      is BackupAlert.DiskFull -> R.string.BackupAlertBottomSheet__skip_restore
      is BackupAlert.BackupFailed -> R.string.BackupAlertBottomSheet__learn_more
      BackupAlert.CouldNotRedeemBackup -> R.string.BackupAlertBottomSheet__learn_more
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
private fun BackupAlertSheetContentPreviewDelete() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.DownloadYourBackupData(
        isLastDay = false,
        formattedSize = "2.3MB"
      )
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

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewBackupFailed() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.BackupFailed
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewCouldNotRedeemBackup() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.CouldNotRedeemBackup
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewSubscriptionExpired() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.ExpiredAndDowngraded
    )
  }
}

/**
 * All necessary information to display the sheet should be handed in through the specific alert.
 */
@Parcelize
sealed class BackupAlert : Parcelable {

  /**
   * This value is driven by a watermarking system and will be dismissed and snoozed whenever the sheet is closed.
   * This value is driven by failure to complete a backup within a timeout based on the user's chosen backup frequency.
   */
  data class CouldNotCompleteBackup(
    val daysSinceLastBackup: Int
  ) : BackupAlert()

  /**
   * This value is driven by the same watermarking system for [CouldNotCompleteBackup] so that only one of these sheets is shown by the system
   * This value is driven by failure to complete the initial backup.
   */
  data object BackupFailed : BackupAlert()

  /**
   * This value is driven by InAppPayment state, and will be automatically cleared when the sheet is displayed.
   */
  data object FailedToRenew : BackupAlert()

  /**
   * This value is driven by InAppPayment state, and will be automatically cleared when the sheet is displayed.
   * This value is displayed if we hit an 'unexpected cancellation' of a user's backup.
   */
  data class MediaBackupsAreOff(
    val endOfPeriodSeconds: Long
  ) : BackupAlert()

  /**
   * When a user's subscription becomes cancelled or has a payment failure, we will alert the user
   * up to two times regarding their media deletion via a sheet, and once in the last 4 hours with a dialog.
   *
   * This value drives viewing the sheet.
   */
  data class DownloadYourBackupData(
    val isLastDay: Boolean,
    val formattedSize: String,
    val type: BackupDownloadNotifierState.Type = BackupDownloadNotifierState.Type.SHEET
  ) : BackupAlert()

  /**
   * The disk is full. Contains a value representing the amount of space that must be freed.
   *
   */
  data class DiskFull(val requiredSpace: String) : BackupAlert()

  /**
   * Too many attempts to redeem the backup subscription have occurred this month.
   */
  data object CouldNotRedeemBackup : BackupAlert()

  /**
   * Displayed after the user falls out of the grace period and their backups subscription is downgraded
   * to the free tier.
   */
  data object ExpiredAndDowngraded : BackupAlert()
}
