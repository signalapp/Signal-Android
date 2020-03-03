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

  public static class FullMember extends GroupMemberEntry {

    private final Recipient member;

    public FullMember(@NonNull Recipient member) {
      this.member = member;
    }

    public Recipient getMember() {
      return member;
    }
  }
}
