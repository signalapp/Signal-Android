package org.thoughtcrime.securesms.groups.v2;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.groupsv2.GroupCandidate;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class GroupCandidateHelper {
  private final SignalServiceAccountManager signalServiceAccountManager;
  private final RecipientDatabase           recipientDatabase;

  public GroupCandidateHelper(@NonNull Context context) {
    signalServiceAccountManager = ApplicationDependencies.getSignalServiceAccountManager();
    recipientDatabase           = DatabaseFactory.getRecipientDatabase(context);
  }

  private static final String TAG = Log.tag(GroupCandidateHelper.class);

  /**
   * Given a recipient will create a {@link GroupCandidate} which may or may not have a profile key credential.
   * <p>
   * It will try to find missing profile key credentials from the server and persist locally.
   */
  @WorkerThread
  public @NonNull GroupCandidate recipientIdToCandidate(@NonNull RecipientId recipientId)
      throws IOException
  {
    final Recipient recipient = Recipient.resolved(recipientId);

    UUID uuid = recipient.getUuid().orNull();
    if (uuid == null) {
      throw new AssertionError("Non UUID members should have need detected by now");
    }

    Optional<ProfileKeyCredential> profileKeyCredential = Optional.fromNullable(recipient.getProfileKeyCredential());
    GroupCandidate                 candidate            = new GroupCandidate(uuid, profileKeyCredential);

    if (!candidate.hasProfileKeyCredential()) {
      ProfileKey profileKey = ProfileKeyUtil.profileKeyOrNull(recipient.getProfileKey());

      if (profileKey != null) {
        Log.i(TAG, String.format("No profile key credential on recipient %s, fetching", recipient.getId()));

        Optional<ProfileKeyCredential> profileKeyCredentialOptional = signalServiceAccountManager.resolveProfileKeyCredential(uuid, profileKey);

        if (profileKeyCredentialOptional.isPresent()) {
          boolean updatedProfileKey = recipientDatabase.setProfileKeyCredential(recipient.getId(), profileKey, profileKeyCredentialOptional.get());

          if (!updatedProfileKey) {
            Log.w(TAG, String.format("Failed to update the profile key credential on recipient %s", recipient.getId()));
          } else {
            Log.i(TAG, String.format("Got new profile key credential for recipient %s", recipient.getId()));
            candidate = candidate.withProfileKeyCredential(profileKeyCredentialOptional.get());
          }
        }
      }
    }

    return candidate;
  }

  @WorkerThread
  public @NonNull Set<GroupCandidate> recipientIdsToCandidates(@NonNull Collection<RecipientId> recipientIds)
      throws IOException
  {
    Set<GroupCandidate> result = new HashSet<>(recipientIds.size());

    for (RecipientId recipientId : recipientIds) {
      result.add(recipientIdToCandidate(recipientId));
    }

    return result;
  }
}
