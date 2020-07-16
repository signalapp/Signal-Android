package org.thoughtcrime.securesms.groups.ui;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.List;

public interface AddMembersResultCallback {
  void onMembersAdded(int numberOfMembersAdded, @NonNull List<RecipientId> invitedMembers);
}
