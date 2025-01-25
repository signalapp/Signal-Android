package org.signal.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

class Registry : IssueRegistry() {
  override val vendor = Vendor(
    vendorName = "Signal",
    identifier = "Signal",
    feedbackUrl = "Signal",
    contact = "Signal"
  )

  override val issues = listOf(
    SignalLogDetector.LOG_NOT_SIGNAL,
    SignalLogDetector.LOG_NOT_APP,
    SignalLogDetector.INLINE_TAG,
    VersionCodeDetector.VERSION_CODE_USAGE,
    AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE,
    BlockingGetDetector.UNSAFE_BLOCKING_GET,
    RecipientIdDatabaseDetector.RECIPIENT_ID_DATABASE_REFERENCE_ISSUE,
    ThreadIdDatabaseDetector.THREAD_ID_DATABASE_REFERENCE_ISSUE,
    StartForegroundServiceDetector.START_FOREGROUND_SERVICE_ISSUE,
    CardViewDetector.CARD_VIEW_USAGE
  )

  override val api = CURRENT_API
}
