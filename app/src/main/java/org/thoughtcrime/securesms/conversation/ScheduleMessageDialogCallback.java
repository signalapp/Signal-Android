package org.thoughtcrime.securesms.conversation;

import javax.annotation.Nullable;

/**
 * Interface for responding to scheduled message dialogs blocking the send flow for permissions check.
 */
public interface ScheduleMessageDialogCallback {
  String ARGUMENT_METRIC_ID = "ARGUMENT_METRIC_ID";
  String ARGUMENT_SCHEDULED_DATE = "ARGUMENT_SCHEDULED_DATE";

  void onSchedulePermissionsGranted(@Nullable String metricId, long scheduledDate);
}
