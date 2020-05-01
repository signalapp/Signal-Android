package org.signal.lint;

import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.ApiKt;
import com.android.tools.lint.detector.api.Issue;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class Registry extends IssueRegistry {

  @Override
  public List<Issue> getIssues() {
    return Collections.singletonList(SignalLogDetector.LOG_NOT_SIGNAL);
  }

  @Override
  public int getApi() {
    return ApiKt.CURRENT_API;
  }
}
