package org.thoughtcrime.securesms.groups.ui;

import androidx.annotation.NonNull;

public interface AdminActionsListener {

  void onRevokeInvite(@NonNull GroupMemberEntry.PendingMember pendingMember);

  void onRevokeAllInvites(@NonNull GroupMemberEntry.UnknownPendingMemberCount pendingMembers);

  void onApproveRequest(@NonNull GroupMemberEntry.RequestingMember requestingMember);

  void onDenyRequest(@NonNull GroupMemberEntry.RequestingMember requestingMember);
}
