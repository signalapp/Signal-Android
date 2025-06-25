/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import android.content.DialogInterface
import android.os.Parcelable
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.billing.launchManageBackupsSubscription
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportDialogFragment
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.keyvalue.protos.BackupDownloadNotifierState
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.PlayStoreUtil

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
    AlertContainer(
      backupAlert = backupAlert,
      primaryActionButtonState = rememberPrimaryAction(backupAlert, remember(backupAlert) { createPrimaryAction() }),
      secondaryActionButtonState = rememberSecondaryAction(backupAlert) { performSecondaryAction() }
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

      BackupAlert.BackupFailed -> {
        ContactSupportDialogFragment.create(
          subject = R.string.BackupAlertBottomSheet_network_failure_support_email,
          filter = R.string.BackupAlertBottomSheet_export_failure_filter
        ).show(parentFragmentManager, null)
      }

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
private fun AlertContainer(
  backupAlert: BackupAlert,
  primaryActionButtonState: BackupAlertActionButtonState,
  secondaryActionButtonState: BackupAlertActionButtonState? = null
) {
  BackupAlertBottomSheetContainer(
    icon = { AlertIcon(backupAlert) },
    title = titleString(backupAlert),
    primaryActionButtonState = primaryActionButtonState,
    secondaryActionButtonState = secondaryActionButtonState,
    content = { Body(backupAlert) }
  )
}

@Composable
private fun AlertIcon(backupAlert: BackupAlert) {
  when (backupAlert) {
    is BackupAlert.MediaBackupsAreOff -> error("Use MediaBackupsAreOffBottomSheet instead.")
    BackupAlert.FailedToRenew, BackupAlert.ExpiredAndDowngraded -> {
      BackupAlertImage()
    }

    else -> {
      val iconColors = rememberBackupsIconColors(backupAlert = backupAlert)
      BackupAlertIcon(iconColors = iconColors)
    }
  }
}

@Composable
private fun Body(backupAlert: BackupAlert) {
  when (val alert = backupAlert) {
    is BackupAlert.CouldNotCompleteBackup -> CouldNotCompleteBackup(
      daysSinceLastBackup = alert.daysSinceLastBackup
    )

    BackupAlert.FailedToRenew -> PaymentProcessingBody()
    is BackupAlert.DownloadYourBackupData -> DownloadYourBackupData(alert.formattedSize)
    is BackupAlert.DiskFull -> DiskFullBody(requiredSpace = alert.requiredSpace)
    BackupAlert.BackupFailed -> BackupFailedBody()
    BackupAlert.CouldNotRedeemBackup -> CouldNotRedeemBackup()
    is BackupAlert.MediaBackupsAreOff -> error("Use MediaBackupsAreOffBottomSheet instead.")
    BackupAlert.ExpiredAndDowngraded -> SubscriptionExpired()
  }
}

@Composable
private fun CouldNotRedeemBackup() {
  BackupAlertText(
    text = stringResource(R.string.BackupAlertBottomSheet__too_many_devices_have_tried),
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
  BackupAlertText(
    text = stringResource(id = R.string.BackupAlertBottomSheet__your_subscription_couldnt_be_renewed),
    modifier = Modifier.padding(bottom = 24.dp)
  )

  BackupAlertText(
    text = stringResource(id = R.string.BackupAlertBottomSheet__youll_continue_to_have_access_to_the_free),
    modifier = Modifier.padding(bottom = 36.dp)
  )
}

@Composable
private fun CouldNotCompleteBackup(
  daysSinceLastBackup: Int
) {
  BackupAlertText(
    text = pluralStringResource(id = R.plurals.BackupAlertBottomSheet__your_device_hasnt, daysSinceLastBackup, daysSinceLastBackup),
    modifier = Modifier.padding(bottom = 60.dp)
  )
}

@Composable
private fun PaymentProcessingBody() {
  BackupAlertText(
    text = stringResource(id = R.string.BackupAlertBottomSheet__check_to_make_sure_your_payment_method),
    modifier = Modifier.padding(bottom = 60.dp)
  )
}

@Composable
private fun DownloadYourBackupData(formattedSize: String) {
  BackupAlertText(
    text = stringResource(id = R.string.BackupAlertBottomSheet__you_have_s_of_media_thats_not_on_this_device, formattedSize),
    modifier = Modifier.padding(bottom = 24.dp)
  )

  BackupAlertText(
    text = stringResource(id = R.string.BackupAlertBottomSheet__you_can_begin_paying_for_backups_again),
    modifier = Modifier.padding(bottom = 36.dp)
  )
}

@Composable
private fun DiskFullBody(requiredSpace: String) {
  BackupAlertText(
    text = stringResource(id = R.string.BackupAlertBottomSheet__to_finish_downloading_your_signal_backup, requiredSpace),
    modifier = Modifier.padding(bottom = 24.dp)
  )

  BackupAlertText(
    text = stringResource(R.string.BackupAlertBottomSheet__to_free_up_space_offload),
    modifier = Modifier.padding(bottom = 36.dp)
  )
}

@Composable
private fun BackupFailedBody() {
  val context = LocalContext.current
  val text = buildAnnotatedString {
    append(stringResource(id = R.string.BackupAlertBottomSheet__an_error_occurred))
    append(" ")

    withLink(
      LinkAnnotation.Clickable(tag = "learn-more") {
        CommunicationActions.openBrowserLink(context, context.getString(R.string.backup_failed_support_url))
      }
    ) {
      withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
        append(stringResource(id = R.string.BackupAlertBottomSheet__learn_more))
      }
    }
  }

  BackupAlertText(
    text = text,
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
private fun rememberPrimaryAction(
  backupAlert: BackupAlert,
  callback: () -> Unit
): BackupAlertActionButtonState {
  val label = when (backupAlert) {
    is BackupAlert.CouldNotCompleteBackup -> stringResource(R.string.BackupAlertBottomSheet__back_up_now)
    BackupAlert.FailedToRenew -> stringResource(R.string.BackupAlertBottomSheet__manage_subscription)
    is BackupAlert.MediaBackupsAreOff -> error("Not supported.")
    is BackupAlert.DownloadYourBackupData -> stringResource(R.string.BackupAlertBottomSheet__download_backup_now)
    is BackupAlert.DiskFull -> stringResource(R.string.BackupAlertBottomSheet__got_it)
    is BackupAlert.BackupFailed -> stringResource(R.string.BackupAlertBottomSheet__check_for_update)
    BackupAlert.CouldNotRedeemBackup -> stringResource(R.string.BackupAlertBottomSheet__got_it)
    BackupAlert.ExpiredAndDowngraded -> stringResource(R.string.BackupAlertBottomSheet__manage_backups)
  }

  return remember(backupAlert, callback) {
    BackupAlertActionButtonState(
      label = label,
      callback = callback
    )
  }
}

@Composable
private fun rememberSecondaryAction(
  backupAlert: BackupAlert,
  callback: () -> Unit
): BackupAlertActionButtonState? {
  val labelResource = when (backupAlert) {
    is BackupAlert.CouldNotCompleteBackup -> R.string.BackupAlertBottomSheet__try_later
    BackupAlert.FailedToRenew, BackupAlert.ExpiredAndDowngraded -> R.string.BackupAlertBottomSheet__not_now
    is BackupAlert.MediaBackupsAreOff -> error("Not supported.")
    is BackupAlert.DownloadYourBackupData -> R.string.BackupAlertBottomSheet__dont_download_backup
    is BackupAlert.DiskFull -> R.string.BackupAlertBottomSheet__skip_restore
    is BackupAlert.BackupFailed -> R.string.BackupAlertBottomSheet__contact_support
    BackupAlert.CouldNotRedeemBackup -> R.string.BackupAlertBottomSheet__learn_more
  }

  if (labelResource <= 0) {
    return null
  }

  val label = stringResource(labelResource)

  return remember(backupAlert, callback) {
    BackupAlertActionButtonState(
      label = label,
      callback = callback
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewGeneric() {
  Previews.BottomSheetPreview {
    val backupAlert = BackupAlert.CouldNotCompleteBackup(daysSinceLastBackup = 7)
    val primaryActionButtonState = rememberPrimaryAction(backupAlert) { }
    val secondaryActionButtonState = rememberSecondaryAction(backupAlert) { }

    AlertContainer(backupAlert, primaryActionButtonState, secondaryActionButtonState)
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewPayment() {
  Previews.BottomSheetPreview {
    val backupAlert = BackupAlert.FailedToRenew
    val primaryActionButtonState = rememberPrimaryAction(backupAlert) { }
    val secondaryActionButtonState = rememberSecondaryAction(backupAlert) { }

    AlertContainer(backupAlert, primaryActionButtonState, secondaryActionButtonState)
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewDelete() {
  Previews.BottomSheetPreview {
    val backupAlert = BackupAlert.DownloadYourBackupData(
      isLastDay = false,
      formattedSize = "2.3MB"
    )
    val primaryActionButtonState = rememberPrimaryAction(backupAlert) { }
    val secondaryActionButtonState = rememberSecondaryAction(backupAlert) { }

    AlertContainer(backupAlert, primaryActionButtonState, secondaryActionButtonState)
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewDiskFull() {
  Previews.BottomSheetPreview {
    val backupAlert = BackupAlert.DiskFull(requiredSpace = "12GB")
    val primaryActionButtonState = rememberPrimaryAction(backupAlert) { }
    val secondaryActionButtonState = rememberSecondaryAction(backupAlert) { }

    AlertContainer(backupAlert, primaryActionButtonState, secondaryActionButtonState)
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewBackupFailed() {
  Previews.BottomSheetPreview {
    val backupAlert = BackupAlert.BackupFailed
    val primaryActionButtonState = rememberPrimaryAction(backupAlert) { }
    val secondaryActionButtonState = rememberSecondaryAction(backupAlert) { }

    AlertContainer(backupAlert, primaryActionButtonState, secondaryActionButtonState)
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewCouldNotRedeemBackup() {
  Previews.BottomSheetPreview {
    val backupAlert = BackupAlert.CouldNotRedeemBackup
    val primaryActionButtonState = rememberPrimaryAction(backupAlert) { }
    val secondaryActionButtonState = rememberSecondaryAction(backupAlert) { }

    AlertContainer(backupAlert, primaryActionButtonState, secondaryActionButtonState)
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewSubscriptionExpired() {
  Previews.BottomSheetPreview {
    val backupAlert = BackupAlert.ExpiredAndDowngraded
    val primaryActionButtonState = rememberPrimaryAction(backupAlert) { }
    val secondaryActionButtonState = rememberSecondaryAction(backupAlert) { }

    AlertContainer(backupAlert, primaryActionButtonState, secondaryActionButtonState)
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
