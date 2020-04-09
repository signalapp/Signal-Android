package org.thoughtcrime.securesms.groups.v2.processing;

import androidx.annotation.NonNull;

import java.util.Collection;

/**
 * Pair of log entries applied and a new {@link GlobalGroupState}.
 */
final class AdvanceGroupStateResult {

  @NonNull private final Collection<GroupLogEntry> processedLogEntries;
  @NonNull private final GlobalGroupState          newGlobalGroupState;

  AdvanceGroupStateResult(@NonNull Collection<GroupLogEntry> processedLogEntries,
                          @NonNull GlobalGroupState newGlobalGroupState)
  {
    this.processedLogEntries = processedLogEntries;
    this.newGlobalGroupState = newGlobalGroupState;
  }

  @NonNull Collection<GroupLogEntry> getProcessedLogEntries() {
    return processedLogEntries;
  }

  @NonNull GlobalGroupState getNewGlobalGroupState() {
    return newGlobalGroupState;
  }
}
