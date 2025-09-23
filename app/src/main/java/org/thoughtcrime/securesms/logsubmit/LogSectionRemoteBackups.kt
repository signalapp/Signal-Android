/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.logsubmit

import android.content.Context
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.runBlocking
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgress
import org.thoughtcrime.securesms.backup.v2.ui.subscription.GooglePlayServicesAvailability
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState
import org.thoughtcrime.securesms.payments.FiatMoneyUtil

class LogSectionRemoteBackups : LogSection {
  override fun getTitle(): String = "REMOTE BACKUPS"

  override fun getContent(context: Context): CharSequence {
    val output = StringBuilder()

    output.append("-- Backup State\n")
    output.append("Enabled:                              ${SignalStore.backup.areBackupsEnabled}\n")
    output.append("Current tier:                         ${SignalStore.backup.backupTier}\n")
    output.append("Latest tier:                          ${SignalStore.backup.latestBackupTier}\n")
    output.append("Backup override tier:                 ${SignalStore.backup.backupTierInternalOverride}\n")
    output.append("Last backup time:                     ${SignalStore.backup.lastBackupTime}\n")
    output.append("Last check-in:                        ${SignalStore.backup.lastCheckInMillis}\n")
    output.append("Last reconciliation time:             ${SignalStore.backup.lastAttachmentReconciliationTime}\n")
    output.append("Days since last backup:               ${SignalStore.backup.daysSinceLastBackup}\n")
    output.append("User manually skipped media restore:  ${SignalStore.backup.userManuallySkippedMediaRestore}\n")
    output.append("Can backup with cellular:             ${SignalStore.backup.backupWithCellular}\n")
    output.append("Has backup been uploaded:             ${SignalStore.backup.hasBackupBeenUploaded}\n")
    output.append("Has backup failure:                   ${SignalStore.backup.hasBackupFailure}\n")
    output.append("Optimize storage:                     ${SignalStore.backup.optimizeStorage}\n")
    output.append("Detected subscription state mismatch: ${SignalStore.backup.subscriptionStateMismatchDetected}\n")
    output.append("Last verified key time:               ${SignalStore.backup.lastVerifyKeyTime}\n")
    output.append("Restore state:                        ${ArchiveRestoreProgress.state}\n")
    output.append("\n -- Subscription State\n")

    val backupSubscriptionId = InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP)
    val googlePlayBillingAccess = runBlocking { AppDependencies.billingApi.getApiAvailability() }
    val googlePlayServicesAvailability = GooglePlayServicesAvailability.fromCode(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context))
    val inAppPayment = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_BACKUP)

    output.append("Has backup subscription id: ${backupSubscriptionId != null}\n")
    output.append("Google Play Billing state:  $googlePlayBillingAccess\n")
    output.append("Google Play Services state: $googlePlayServicesAvailability\n\n")

    if (inAppPayment != null) {
      output.append("IAP end of period (seconds):       ${inAppPayment.endOfPeriodSeconds}\n")
      output.append("IAP state:                         ${inAppPayment.state.name}\n")
      output.append("IAP inserted at (seconds):         ${inAppPayment.insertedAt.inWholeSeconds}\n")
      output.append("IAP updated at (seconds):          ${inAppPayment.updatedAt.inWholeSeconds}\n")
      output.append("IAP notified flag:                 ${inAppPayment.notified}\n")
      output.append("IAP level:                         ${inAppPayment.data.level}\n")
      output.append("IAP redemption stage (or null):    ${inAppPayment.data.redemption?.stage}\n")
      output.append("IAP error type (or null):          ${inAppPayment.data.error?.type}\n")
      output.append("IAP cancellation reason (or null): ${inAppPayment.data.cancellation?.reason}\n")
      output.append("IAP price:                         ${inAppPayment.data.amount?.toFiatMoney()?.let { FiatMoneyUtil.format(context.resources, it)} ?: "Not available" }\n")
    } else {
      output.append("No in-app payment data available.\n")
    }

    output.append("\n -- Imported DebugInfo\n")
    if (SignalStore.internal.importedBackupDebugInfo != null) {
      val info = SignalStore.internal.importedBackupDebugInfo!!
      output.append("Debuglog          : ${info.debuglogUrl}\n")
      output.append("Using Paid Tier   : ${info.usingPaidTier}\n")
      output.append("Attachment Details:\n")
      output.append("  NONE              : ${info.attachmentDetails?.notStartedCount ?: "N/A"}\n")
      output.append("  UPLOAD_IN_PROGRESS: ${info.attachmentDetails?.uploadInProgressCount ?: "N/A"}\n")
      output.append("  COPY_PENDING      : ${info.attachmentDetails?.copyPendingCount ?: "N/A"}\n")
      output.append("  FINISHED          : ${info.attachmentDetails?.finishedCount ?: "N/A"}\n")
      output.append("  PERMANENT_FAILURE : ${info.attachmentDetails?.permanentFailureCount ?: "N/A"}\n")
      output.append("  TEMPORARY_FAILURE : ${info.attachmentDetails?.temporaryFailureCount ?: "N/A"}\n")
    } else {
      output.append("None\n")
    }

    output.append("\n -- ArchiveUploadProgress\n")
    if (SignalStore.backup.archiveUploadState != null) {
      output.append(SignalStore.backup.archiveUploadState!!.toPrettyString())

      if (SignalStore.backup.archiveUploadState!!.state !in setOf(ArchiveUploadProgressState.State.None, ArchiveUploadProgressState.State.UserCanceled)) {
        output.append("Pending bytes: ${SignalDatabase.attachments.getPendingArchiveUploadBytes()}\n")
      }
    } else {
      output.append("None\n")
    }

    output.append("\n -- Attachment Stats\n")
    output.append(SignalDatabase.attachments.debugGetAttachmentStats().prettyString())

    return output
  }
}

private fun ArchiveUploadProgressState.toPrettyString(): String {
  return buildString {
    appendLine("state: ${state.name}")
    appendLine("backupPhase: ${backupPhase.name}")
    appendLine("frameExportCount: $frameExportCount")
    appendLine("frameTotalCount: $frameTotalCount")
    appendLine("backupFileUploadedBytes: $backupFileUploadedBytes")
    appendLine("backupFileTotalBytes: $backupFileTotalBytes")
    appendLine("mediaUploadedBytes: $mediaUploadedBytes")
    appendLine("mediaTotalBytes: $mediaTotalBytes")

    if (frameTotalCount > 0) {
      val frameProgress = (frameExportCount.toDouble() / frameTotalCount * 100).toInt()
      appendLine("Frame export progress: $frameProgress%")
    }

    if (backupFileTotalBytes > 0) {
      val backupFileProgress = (backupFileUploadedBytes.toDouble() / backupFileTotalBytes * 100).toInt()
      appendLine("Backup file upload progress: $backupFileProgress%")
    }

    if (mediaTotalBytes > 0) {
      val mediaProgress = (mediaUploadedBytes.toDouble() / mediaTotalBytes * 100).toInt()
      appendLine("Media upload progress: $mediaProgress%")
    }
  }
}
