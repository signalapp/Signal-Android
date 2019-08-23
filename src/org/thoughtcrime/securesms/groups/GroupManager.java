package org.thoughtcrime.securesms.groups;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class GroupManager {

  public static @NonNull GroupActionResult createGroup(@NonNull  Context        context,
                                                       @NonNull  Set<Recipient> members,
                                                       @Nullable Bitmap         avatar,
                                                       @Nullable String         name,
                                                                 boolean        mms)
  {
    Set<Address> addresses = getMemberAddresses(members);

    return V1GroupManager.createGroup(context, addresses, avatar, name, mms);
  }

  public static GroupActionResult updateGroup(@NonNull  Context        context,
                                              @NonNull  String         groupId,
                                              @NonNull  Set<Recipient> members,
                                              @Nullable Bitmap         avatar,
                                              @Nullable String         name)
      throws InvalidNumberException
  {
    Set<Address> addresses = getMemberAddresses(members);

    return V1GroupManager.updateGroup(context, groupId, addresses, avatar, name);
  }

  private static Set<Address> getMemberAddresses(Collection<Recipient> recipients) {
    final Set<Address> results = new HashSet<>();
    for (Recipient recipient : recipients) {
      results.add(recipient.getAddress());
    }

    return results;
  }

  public static class GroupActionResult {
    private final Recipient groupRecipient;
    private final long      threadId;

    public GroupActionResult(Recipient groupRecipient, long threadId) {
      this.groupRecipient = groupRecipient;
      this.threadId       = threadId;
    }

    public Recipient getGroupRecipient() {
      return groupRecipient;
    }

    public long getThreadId() {
      return threadId;
    }
  }
}
