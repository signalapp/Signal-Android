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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.billing.launchManageBackupsSubscription
import org.thoughtcrime.securesms.billing.upgrade.UpgradeToPaidTierBottomSheet
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.jobs.BackupRestoreMediaJob
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.PlayStoreUtil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import org.signal.core.ui.R as CoreUiR

/**
 * Notifies the user of an issue with their backup.
 */
class BackupAlertBottomSheet : UpgradeToPaidTierBottomSheet() {

  override val peekHeightPercentage: Float = 0.75f

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
  override fun UpgradeSheetContent(
    paidBackupType: MessageBackupsType.Paid,
    freeBackupType: MessageBackupsType.Free,
    isSubscribeEnabled: Boolean,
    onSubscribeClick: () -> Unit
  ) {
    var pricePerMonth by remember { mutableStateOf("-") }
    val resources = LocalContext.current.resources

    LaunchedEffect(paidBackupType.pricePerMonth) {
      pricePerMonth = FiatMoneyUtil.format(resources, paidBackupType.pricePerMonth, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
    }

    val performPrimaryAction = remember(onSubscribeClick) {
      createPrimaryAction(onSubscribeClick)
    }

    BackupAlertSheetContent(
      backupAlert = backupAlert,
      isSubscribeEnabled = isSubscribeEnabled,
      mediaTtl = paidBackupType.mediaTtl,
      onPrimaryActionClick = performPrimaryAction,
      onSecondaryActionClick = this::performSecondaryAction
    )
  }

  @Stable
  private fun createPrimaryAction(onSubscribeClick: () -> Unit): () -> Unit = {
    when (backupAlert) {
      is BackupAlert.CouldNotCompleteBackup -> {
        BackupMessagesJob.enqueue()
        startActivity(AppSettingsActivity.remoteBackups(requireContext()))
      }

      BackupAlert.FailedToRenew -> launchManageBackupsSubscription()
      is BackupAlert.MediaBackupsAreOff -> {
        onSubscribeClick()
      }

      BackupAlert.MediaWillBeDeletedToday -> {
        performFullMediaDownload()
      }

      is BackupAlert.DiskFull -> Unit
      is BackupAlert.BackupFailed ->
        PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext())

      BackupAlert.CouldNotRedeemBackup -> Unit
    }

    dismissAllowingStateLoss()
  }

  @Stable
  private fun performSecondaryAction() {
    when (backupAlert) {
      is BackupAlert.CouldNotCompleteBackup -> Unit
      BackupAlert.FailedToRenew -> Unit
      is BackupAlert.MediaBackupsAreOff -> Unit
      BackupAlert.MediaWillBeDeletedToday -> {
        displayLastChanceDialog()
      }

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
      is BackupAlert.MediaWillBeDeletedToday -> BackupRepository.snoozeYourMediaWillBeDeletedTodaySheet()
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
    // TODO [backups] -- We need to force this to download everything
    AppDependencies.jobManager.add(BackupRestoreMediaJob())
  }
}

@Composable
private fun BackupAlertSheetContent(
  backupAlert: BackupAlert,
  pricePerMonth: String = "",
  isSubscribeEnabled: Boolean = true,
  mediaTtl: Duration,
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
      BackupAlert.FailedToRenew, is BackupAlert.MediaBackupsAreOff -> {
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
      is BackupAlert.MediaBackupsAreOff -> MediaBackupsAreOffBody(backupAlert.endOfPeriodSeconds, mediaTtl)
      BackupAlert.MediaWillBeDeletedToday -> MediaWillBeDeletedTodayBody()
      is BackupAlert.DiskFull -> DiskFullBody(requiredSpace = backupAlert.requiredSpace)
      BackupAlert.BackupFailed -> BackupFailedBody()
      BackupAlert.CouldNotRedeemBackup -> CouldNotRedeemBackup()
    }

    val secondaryActionResource = rememberSecondaryActionResource(backupAlert = backupAlert)
    val padBottom = if (secondaryActionResource > 0) 16.dp else 56.dp

    Buttons.LargeTonal(
      enabled = isSubscribeEnabled,
      onClick = onPrimaryActionClick,
      modifier = Modifier
        .defaultMinSize(minWidth = 220.dp)
        .padding(bottom = padBottom)
    ) {
      Text(text = primaryActionString(backupAlert = backupAlert, pricePerMonth = pricePerMonth))
    }

    if (secondaryActionResource > 0) {
      TextButton(
        enabled = isSubscribeEnabled,
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
private fun MediaBackupsAreOffBody(
  endOfPeriodSeconds: Long,
  mediaTtl: Duration
) {
  val daysUntilDeletion = remember { endOfPeriodSeconds.days + mediaTtl }.inWholeDays.toInt()

  Text(
    text = pluralStringResource(id = R.plurals.BackupAlertBottomSheet__your_backup_plan_has_expired, daysUntilDeletion, daysUntilDeletion),
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
      BackupAlert.FailedToRenew, is BackupAlert.MediaBackupsAreOff -> error("Not icon-based options.")
      is BackupAlert.CouldNotCompleteBackup, BackupAlert.BackupFailed, is BackupAlert.DiskFull, BackupAlert.CouldNotRedeemBackup -> BackupsIconColors.Warning
      BackupAlert.MediaWillBeDeletedToday -> BackupsIconColors.Error
    }
  }
}

@Composable
private fun titleString(backupAlert: BackupAlert): String {
  return when (backupAlert) {
    is BackupAlert.CouldNotCompleteBackup -> stringResource(R.string.BackupAlertBottomSheet__couldnt_complete_backup)
    BackupAlert.FailedToRenew -> stringResource(R.string.BackupAlertBottomSheet__your_backups_subscription_failed_to_renew)
    is BackupAlert.MediaBackupsAreOff -> stringResource(R.string.BackupAlertBottomSheet__your_backups_subscription_expired)
    BackupAlert.MediaWillBeDeletedToday -> stringResource(R.string.BackupAlertBottomSheet__your_media_will_be_deleted_today)
    is BackupAlert.DiskFull -> stringResource(R.string.BackupAlertBottomSheet__free_up_s_on_this_device, backupAlert.requiredSpace)
    BackupAlert.BackupFailed -> stringResource(R.string.BackupAlertBottomSheet__backup_failed)
    BackupAlert.CouldNotRedeemBackup -> stringResource(R.string.BackupAlertBottomSheet__couldnt_redeem_your_backups_subscription)
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
    is BackupAlert.MediaBackupsAreOff -> stringResource(R.string.BackupAlertBottomSheet__subscribe_for_s_month, pricePerMonth)
    BackupAlert.MediaWillBeDeletedToday -> stringResource(R.string.BackupAlertBottomSheet__download_media_now)
    is BackupAlert.DiskFull -> stringResource(R.string.BackupAlertBottomSheet__got_it)
    is BackupAlert.BackupFailed -> stringResource(R.string.BackupAlertBottomSheet__check_for_update)
    BackupAlert.CouldNotRedeemBackup -> stringResource(R.string.BackupAlertBottomSheet__got_it)
  }
}

@Composable
private fun rememberSecondaryActionResource(backupAlert: BackupAlert): Int {
  return remember(backupAlert) {
    when (backupAlert) {
      is BackupAlert.CouldNotCompleteBackup -> R.string.BackupAlertBottomSheet__try_later
      BackupAlert.FailedToRenew -> R.string.BackupAlertBottomSheet__not_now
      is BackupAlert.MediaBackupsAreOff -> R.string.BackupAlertBottomSheet__not_now
      BackupAlert.MediaWillBeDeletedToday -> R.string.BackupAlertBottomSheet__dont_download_media
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
      backupAlert = BackupAlert.CouldNotCompleteBackup(daysSinceLastBackup = 7),
      mediaTtl = 60.days
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewPayment() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.FailedToRenew,
      mediaTtl = 60.days
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewMedia() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.MediaBackupsAreOff(endOfPeriodSeconds = System.currentTimeMillis().milliseconds.inWholeSeconds),
      pricePerMonth = "$2.99",
      mediaTtl = 60.days
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewDelete() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.MediaWillBeDeletedToday,
      mediaTtl = 60.days
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewDiskFull() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.DiskFull(requiredSpace = "12GB"),
      mediaTtl = 60.days
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewBackupFailed() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.BackupFailed,
      mediaTtl = 60.days
    )
  }
}

@SignalPreview
@Composable
private fun BackupAlertSheetContentPreviewCouldNotRedeemBackup() {
  Previews.BottomSheetPreview {
    BackupAlertSheetContent(
      backupAlert = BackupAlert.CouldNotRedeemBackup,
      mediaTtl = 60.days
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
   * TODO [backups] - This value is driven as "60D after the last time the user pinged their backup"
   */
  data object MediaWillBeDeletedToday : BackupAlert()

  /**
   * The disk is full. Contains a value representing the amount of space that must be freed.
   *
   */
  data class DiskFull(val requiredSpace: String) : BackupAlert()

  /**
   * Too many attempts to redeem the backup subscription have occurred this month.
   */
  data object CouldNotRedeemBackup : BackupAlert()
}
