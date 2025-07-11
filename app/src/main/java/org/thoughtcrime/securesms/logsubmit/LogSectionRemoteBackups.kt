/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.logsubmit

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore

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
    output.append("Last media sync:                      ${SignalStore.backup.lastAttachmentReconciliationTime}\n")
    output.append("Days since last backup:               ${SignalStore.backup.daysSinceLastBackup}\n")
    output.append("User manually skipped media restore:  ${SignalStore.backup.userManuallySkippedMediaRestore}\n")
    output.append("Can backup with cellular:             ${SignalStore.backup.backupWithCellular}\n")
    output.append("Has backup been uploaded:             ${SignalStore.backup.hasBackupBeenUploaded}\n")
    output.append("Has backup failure:                   ${SignalStore.backup.hasBackupFailure}\n")
    output.append("Backup frequency:                     ${SignalStore.backup.backupFrequency.name}\n")
    output.append("Optimize storage:                     ${SignalStore.backup.optimizeStorage}\n")
    output.append("Detected subscription state mismatch: ${SignalStore.backup.subscriptionStateMismatchDetected}\n")
    output.append("\n -- Subscription State\n")

    val backupSubscriptionId = InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP)
    val hasGooglePlayBilling = runBlocking { AppDependencies.billingApi.isApiAvailable() }
    val inAppPayment = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_BACKUP)

    output.append("Has backup subscription id: ${backupSubscriptionId != null}\n")
    output.append("Has Google Play Billing:    $hasGooglePlayBilling\n\n")

    if (inAppPayment != null) {
      output.append("IAP end of period (seconds):       ${inAppPayment.endOfPeriodSeconds}\n")
      output.append("IAP state:                         ${inAppPayment.state.name}\n")
      output.append("IAP inserted at:                   ${inAppPayment.insertedAt}\n")
      output.append("IAP updated at:                    ${inAppPayment.updatedAt}\n")
      output.append("IAP notified flag:                 ${inAppPayment.notified}\n")
      output.append("IAP level:                         ${inAppPayment.data.level}\n")
      output.append("IAP redemption stage (or null):    ${inAppPayment.data.redemption?.stage}\n")
      output.append("IAP error type (or null):          ${inAppPayment.data.error?.type}\n")
      output.append("IAP cancellation reason (or null): ${inAppPayment.data.cancellation?.reason}\n")
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

    return output
  }
}
