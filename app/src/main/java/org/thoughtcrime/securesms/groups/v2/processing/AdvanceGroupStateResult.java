package org.thoughtcrime.securesms.groups.v2.processing;

import androidx.annotation.NonNull;

import java.util.Collection;

/**
 * Pair of log entries applied and a new {@link GlobalGroupState}.
 */
final class AdvanceGroupStateResult {

  @NonNull private final Collection<LocalGroupLogEntry> processedLogEntries;
  @NonNull private final GlobalGroupState               newGlobalGroupState;

  AdvanceGroupStateResult(@NonNull Collection<LocalGroupLogEntry> processedLogEntries,
                          @NonNull GlobalGroupState newGlobalGroupState)
  {
    this.processedLogEntries = processedLogEntries;
    this.newGlobalGroupState = newGlobalGroupState;
  }

  @NonNull Collection<LocalGroupLogEntry> getProcessedLogEntries() {
    return processedLogEntries;
  }

  @NonNull GlobalGroupState getNewGlobalGroupState() {
    return newGlobalGroupState;
  }
}
