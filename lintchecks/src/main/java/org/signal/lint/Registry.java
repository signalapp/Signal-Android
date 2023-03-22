package org.signal.lint;

import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.Vendor;
import com.android.tools.lint.detector.api.ApiKt;
import com.android.tools.lint.detector.api.Issue;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class Registry extends IssueRegistry {

  @Override
  public Vendor getVendor() {
    return new Vendor("Signal", "Signal", "Signal", "Signal");
  }

  @Override
  public List<Issue> getIssues() {
    return Arrays.asList(SignalLogDetector.LOG_NOT_SIGNAL,
                         SignalLogDetector.LOG_NOT_APP,
                         SignalLogDetector.INLINE_TAG,
                         VersionCodeDetector.VERSION_CODE_USAGE,
                         AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE,
                         BlockingGetDetector.UNSAFE_BLOCKING_GET,
                         RecipientIdDatabaseDetector.RECIPIENT_ID_DATABASE_REFERENCE_ISSUE,
                         ThreadIdDatabaseDetector.THREAD_ID_DATABASE_REFERENCE_ISSUE,
                         StartForegroundServiceDetector.START_FOREGROUND_SERVICE_ISSUE,
                         CardViewDetector.CARD_VIEW_USAGE);
  }

  @Override
  public int getApi() {
    return ApiKt.CURRENT_API;
  }
}
