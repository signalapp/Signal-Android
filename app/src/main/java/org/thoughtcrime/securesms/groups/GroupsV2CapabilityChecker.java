package org.thoughtcrime.securesms.groups;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class GroupsV2CapabilityChecker {

  private static final String TAG = Log.tag(GroupsV2CapabilityChecker.class);

  private GroupsV2CapabilityChecker() {}

  @WorkerThread
  static boolean allAndSelfHaveServiceId(@NonNull Collection<RecipientId> recipientIds) {
    HashSet<RecipientId> recipientIdsSet = new HashSet<>(recipientIds);

    recipientIdsSet.add(Recipient.self().getId());

    return allHaveServiceId(recipientIdsSet);
  }

  @WorkerThread
  static boolean allHaveServiceId(@NonNull Collection<RecipientId> recipientIds) {
    return Recipient.resolvedList(recipientIds)
                    .stream()
                    .allMatch(Recipient::hasServiceId);
  }
}
