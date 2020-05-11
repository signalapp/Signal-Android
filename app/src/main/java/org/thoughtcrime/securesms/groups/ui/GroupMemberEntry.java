package org.thoughtcrime.securesms.groups.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import org.signal.zkgroup.groups.UuidCiphertext;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;

import java.util.Collection;

public abstract class GroupMemberEntry {

            private final DefaultValueLiveData<Boolean> busy    = new DefaultValueLiveData<>(false);
  @Nullable private       Runnable                      onClick;

  private GroupMemberEntry() {
  }

  public void setOnClick(@NonNull Runnable onClick) {
    this.onClick = onClick;
  }

  public @Nullable Runnable getOnClick() {
    return onClick;
  }

  public LiveData<Boolean> getBusy() {
    return busy;
  }

  public void setBusy(boolean busy) {
    this.busy.postValue(busy);
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
    private final Recipient      invitee;
    private final UuidCiphertext inviteeCipherText;
    private final boolean        cancellable;

    public PendingMember(@NonNull Recipient invitee, @NonNull UuidCiphertext inviteeCipherText, boolean cancellable) {
      this.invitee           = invitee;
      this.inviteeCipherText = inviteeCipherText;
      this.cancellable       = cancellable;
    }

    public Recipient getInvitee() {
      return invitee;
    }

    public UuidCiphertext getInviteeCipherText() {
      return inviteeCipherText;
    }

    public boolean isCancellable() {
      return cancellable;
    }
  }

  public final static class UnknownPendingMemberCount extends GroupMemberEntry {
    private final Recipient                  inviter;
    private final Collection<UuidCiphertext> ciphertexts;
    private final boolean                    cancellable;

    public UnknownPendingMemberCount(@NonNull Recipient inviter,
                                     @NonNull Collection<UuidCiphertext> ciphertexts,
                                     boolean cancellable) {
      this.inviter     = inviter;
      this.ciphertexts = ciphertexts;
      this.cancellable = cancellable;
    }

    public Recipient getInviter() {
      return inviter;
    }

    public int getInviteCount() {
      return ciphertexts.size();
    }

    public Collection<UuidCiphertext> getCiphertexts() {
      return ciphertexts;
    }

    public boolean isCancellable() {
      return cancellable;
    }
  }
}
