package org.thoughtcrime.securesms.jobmanager;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec;

public interface JobUpdater {
  /**
   * Called for each enqueued job, giving you an opportunity to update each one.
   *
   * @param jobSpec An object representing data about an enqueued job.
   * @param serializer An object that can be used to serialize/deserialize data if necessary for
   *                   your update.
   *
   * @return The updated JobSpec you want persisted. If you do not wish to make an update, return
   *         the literal same JobSpec instance you were provided.
   */
  @NonNull JobSpec update(@NonNull JobSpec jobSpec, @NonNull Data.Serializer serializer);
}
