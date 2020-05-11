package org.thoughtcrime.securesms.groups.ui;

import androidx.annotation.NonNull;

public interface AdminActionsListener {

  void onCancelInvite(@NonNull GroupMemberEntry.PendingMember pendingMember);

  void onCancelAllInvites(@NonNull GroupMemberEntry.UnknownPendingMemberCount pendingMembers);
}
