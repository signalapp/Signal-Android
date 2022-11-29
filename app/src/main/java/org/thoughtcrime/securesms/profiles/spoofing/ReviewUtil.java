package org.thoughtcrime.securesms.profiles.spoofing;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Base64;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class ReviewUtil {

  private ReviewUtil() { }

  private static final long TIMEOUT = TimeUnit.HOURS.toMillis(24);

  /**
   * Checks a single recipient against the database to see whether duplicates exist.
   * This should not be used in the context of a group, due to performance reasons.
   *
   * @param recipientId Id of the recipient we are interested in.
   * @return            Whether or not multiple recipients share this profile name.
   */
  @WorkerThread
  public static boolean isRecipientReviewSuggested(@NonNull RecipientId recipientId)
  {
    Recipient recipient = Recipient.resolved(recipientId);

    if (recipient.isGroup() || recipient.isSystemContact()) {
      return false;
    }

    return SignalDatabase.recipients().getSimilarRecipientIds(recipient).size() > 1;
  }

  @WorkerThread
  public static @NonNull List<ReviewRecipient> getDuplicatedRecipients(@NonNull GroupId.V2 groupId)
  {
    Context             context              = ApplicationDependencies.getApplication();
    List<MessageRecord> profileChangeRecords = getProfileChangeRecordsForGroup(context, groupId);

    if (profileChangeRecords.isEmpty()) {
      return Collections.emptyList();
    }

    List<Recipient> members = SignalDatabase.groups()
                                             .getGroupMembers(groupId, GroupTable.MemberSet.FULL_MEMBERS_INCLUDING_SELF);

    List<ReviewRecipient> changed = Stream.of(profileChangeRecords)
                                          .distinctBy(record -> record.getRecipient().getId())
                                          .map(record -> new ReviewRecipient(record.getRecipient().resolve(), getProfileChangeDetails(record)))
                                          .filter(recipient -> !recipient.getRecipient().isSystemContact())
                                          .toList();

    List<ReviewRecipient> results = new LinkedList<>();

    for (ReviewRecipient recipient : changed) {
      if (results.contains(recipient)) {
        continue;
      }

      members.remove(recipient.getRecipient());

      for (Recipient member : members) {
        if (Objects.equals(member.getDisplayName(context), recipient.getRecipient().getDisplayName(context))) {
          results.add(recipient);
          results.add(new ReviewRecipient(member));
        }
      }
    }

    return results;
  }

  @WorkerThread
  public static @NonNull List<MessageRecord> getProfileChangeRecordsForGroup(@NonNull Context context, @NonNull GroupId.V2 groupId) {
    RecipientId recipientId = SignalDatabase.recipients().getByGroupId(groupId).get();
    Long        threadId    = SignalDatabase.threads().getThreadIdFor(recipientId);

    if (threadId == null) {
      return Collections.emptyList();
    } else {
      return SignalDatabase.sms().getProfileChangeDetailsRecords(threadId, System.currentTimeMillis() - TIMEOUT);
    }
  }

  @WorkerThread
  public static int getGroupsInCommonCount(@NonNull Context context, @NonNull RecipientId recipientId) {
    return Stream.of(SignalDatabase.groups()
                 .getPushGroupsContainingMember(recipientId))
                 .filter(g -> g.getMembers().contains(Recipient.self().getId()))
                 .map(GroupTable.GroupRecord::getRecipientId)
                 .toList()
                 .size();
  }

  private static @NonNull ProfileChangeDetails getProfileChangeDetails(@NonNull MessageRecord messageRecord) {
    try {
      return ProfileChangeDetails.parseFrom(Base64.decode(messageRecord.getBody()));
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
