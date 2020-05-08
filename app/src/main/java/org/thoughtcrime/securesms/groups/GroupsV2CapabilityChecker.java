package org.thoughtcrime.securesms.groups;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

final class GroupsV2CapabilityChecker {

  private static final String TAG = Log.tag(GroupsV2CapabilityChecker.class);

  GroupsV2CapabilityChecker() {
  }

  @WorkerThread
  boolean allAndSelfSupportGroupsV2AndUuid(@NonNull Collection<RecipientId> recipientIds)
      throws IOException
  {
    HashSet<RecipientId> recipientIdsSet = new HashSet<>(recipientIds);

    recipientIdsSet.add(Recipient.self().getId());

    return allSupportGroupsV2AndUuid(recipientIdsSet);
  }

  @WorkerThread
  boolean allSupportGroupsV2AndUuid(@NonNull Collection<RecipientId> recipientIds)
      throws IOException
  {
    final HashSet<RecipientId> recipientIdsSet = new HashSet<>(recipientIds);

    for (RecipientId recipientId : recipientIdsSet) {
      Recipient            member         = Recipient.resolved(recipientId);
      Recipient.Capability gv2Capability  = member.getGroupsV2Capability();

      if (gv2Capability == Recipient.Capability.UNKNOWN) {
        if (!ApplicationDependencies.getJobManager().runSynchronously(RetrieveProfileJob.forRecipient(member), TimeUnit.SECONDS.toMillis(1000)).isPresent()) {
          throw new IOException("Recipient capability was not retrieved in time");
        }
      }

      if (gv2Capability != Recipient.Capability.SUPPORTED) {
        Log.i(TAG, "At least one recipient does not support GV2, capability was " + gv2Capability);
        return false;
      }
    }

    for (RecipientId recipientId : recipientIdsSet) {
      Recipient member = Recipient.resolved(recipientId);

      if (!member.hasUuid()) {
        Log.i(TAG, "At least one recipient did not have a UUID known to us");
        return false;
      }
    }

    return true;
  }
}
