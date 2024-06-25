package org.thoughtcrime.securesms.profiles.spoofing;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

public final class ReviewUtil {

  private ReviewUtil() { }

  @WorkerThread
  public static int getGroupsInCommonCount(@NonNull Context context, @NonNull RecipientId recipientId) {
    return Stream.of(SignalDatabase.groups()
                 .getPushGroupsContainingMember(recipientId))
                 .filter(g -> g.getMembers().contains(Recipient.self().getId()))
                 .map(GroupRecord::getRecipientId)
                 .toList()
                 .size();
  }
}
