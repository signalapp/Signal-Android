package org.thoughtcrime.securesms.groups.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.recipients.Recipient;

public abstract class GroupMemberEntry {

  private @Nullable Runnable onClick;

  private GroupMemberEntry() {
  }

  public void setOnClick(@NonNull Runnable onClick) {
    this.onClick = onClick;
  }

  public @Nullable Runnable getOnClick() {
    return onClick;
  }

  public final static class FullMember extends GroupMemberEntry {

    private final Recipient member;

    public FullMember(@NonNull Recipient member) {
      this.member = member;
    }

    public Recipient getMember() {
      return member;
    }
  }

  public final static class PendingMember extends GroupMemberEntry {
    private final Recipient invitee;
    private final byte[]    inviteeCipherText;

    public PendingMember(@NonNull Recipient invitee, @NonNull byte[] inviteeCipherText) {
      this.invitee           = invitee;
      this.inviteeCipherText = inviteeCipherText;
    }

    public Recipient getInvitee() {
      return invitee;
    }

    public byte[] getInviteeCipherText() {
      return inviteeCipherText;
    }
  }

  public final static class UnknownPendingMemberCount extends GroupMemberEntry {
    private Recipient inviter;
    private int       inviteCount;

    public UnknownPendingMemberCount(@NonNull Recipient inviter, int inviteCount) {
      this.inviter     = inviter;
      this.inviteCount = inviteCount;
    }

    public Recipient getInviter() {
      return inviter;
    }

    public int getInviteCount() {
      return inviteCount;
    }
  }
}
