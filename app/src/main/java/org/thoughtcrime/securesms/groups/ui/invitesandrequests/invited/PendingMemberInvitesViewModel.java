package org.thoughtcrime.securesms.groups.ui.invitesandrequests.invited;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.libsignal.zkgroup.groups.UuidCiphertext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.signal.core.util.concurrent.SimpleTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class PendingMemberInvitesViewModel extends ViewModel {

  private final Context                                                                context;
  private final PendingMemberInvitesRepository pendingMemberRepository;
  private final DefaultValueLiveData<List<GroupMemberEntry.PendingMember>>             whoYouInvited           = new DefaultValueLiveData<>(Collections.emptyList());
  private final DefaultValueLiveData<List<GroupMemberEntry.UnknownPendingMemberCount>> whoOthersInvited        = new DefaultValueLiveData<>(Collections.emptyList());

  private PendingMemberInvitesViewModel(@NonNull Context context,
                                        @NonNull PendingMemberInvitesRepository pendingMemberRepository)
  {
    this.context                 = context;
    this.pendingMemberRepository = pendingMemberRepository;

    pendingMemberRepository.getInvitees(this::setMembers);
  }

  public LiveData<List<GroupMemberEntry.PendingMember>> getWhoYouInvited() {
    return whoYouInvited;
  }

  public LiveData<List<GroupMemberEntry.UnknownPendingMemberCount>> getWhoOthersInvited() {
    return whoOthersInvited;
  }

  private void setInvitees(List<GroupMemberEntry.PendingMember> byYou, List<GroupMemberEntry.UnknownPendingMemberCount> byOthers) {
    whoYouInvited.postValue(byYou);
    whoOthersInvited.postValue(byOthers);
  }

  private void setMembers(PendingMemberInvitesRepository.InviteeResult inviteeResult) {
    List<GroupMemberEntry.PendingMember>             byMe     = new ArrayList<>(inviteeResult.getByMe().size());
    List<GroupMemberEntry.UnknownPendingMemberCount> byOthers = new ArrayList<>(inviteeResult.getByOthers().size());

    for (PendingMemberInvitesRepository.SinglePendingMemberInvitedByYou pendingMember : inviteeResult.getByMe()) {
      byMe.add(new GroupMemberEntry.PendingMember(pendingMember.getInvitee(),
                                                  pendingMember.getInviteeCipherText(),
                                                  inviteeResult.isCanRevokeInvites()));
    }

    for (PendingMemberInvitesRepository.MultiplePendingMembersInvitedByAnother pendingMembers : inviteeResult.getByOthers()) {
      byOthers.add(new GroupMemberEntry.UnknownPendingMemberCount(pendingMembers.getInviter(),
                                                                  pendingMembers.getUuidCipherTexts(),
                                                                  inviteeResult.isCanRevokeInvites()));
    }

    setInvitees(byMe, byOthers);
  }

  void revokeInviteFor(@NonNull GroupMemberEntry.PendingMember pendingMember) {
    UuidCiphertext inviteeCipherText = pendingMember.getInviteeCipherText();

    InviteRevokeConfirmationDialog.showOwnInviteRevokeConfirmationDialog(context, pendingMember.getInvitee(), () ->
      SimpleTask.run(
        () -> {
          pendingMember.setBusy(true);
          try {
            return pendingMemberRepository.revokeInvites(Collections.singleton(inviteeCipherText));
          } finally {
            pendingMember.setBusy(false);
          }
        },
        result -> {
          if (result) {
            ArrayList<GroupMemberEntry.PendingMember> newList  = new ArrayList<>(whoYouInvited.getValue());
            Iterator<GroupMemberEntry.PendingMember>  iterator = newList.iterator();

            while (iterator.hasNext()) {
              if (iterator.next().getInviteeCipherText().equals(inviteeCipherText)) {
                iterator.remove();
              }
            }

            whoYouInvited.setValue(newList);
          } else {
            toastErrorCanceling(1);
          }
        }
      ));
  }

  void revokeInvitesFor(@NonNull GroupMemberEntry.UnknownPendingMemberCount pendingMembers) {
    InviteRevokeConfirmationDialog.showOthersInviteRevokeConfirmationDialog(context, pendingMembers.getInviter(), pendingMembers.getInviteCount(),
      () -> SimpleTask.run(
        () -> {
          pendingMembers.setBusy(true);
          try {
            return pendingMemberRepository.revokeInvites(pendingMembers.getCiphertexts());
          } finally {
            pendingMembers.setBusy(false);
          }
        },
        result -> {
          if (result) {
            ArrayList<GroupMemberEntry.UnknownPendingMemberCount> newList  = new ArrayList<>(whoOthersInvited.getValue());
            Iterator<GroupMemberEntry.UnknownPendingMemberCount>  iterator = newList.iterator();

            while (iterator.hasNext()) {
              if (iterator.next().getInviter().equals(pendingMembers.getInviter())) {
                iterator.remove();
              }
            }

            whoOthersInvited.setValue(newList);
          } else {
            toastErrorCanceling(pendingMembers.getInviteCount());
          }
        }
      ));
  }

  private void toastErrorCanceling(int quantity) {
    Toast.makeText(context, context.getResources().getQuantityText(R.plurals.PendingMembersActivity_error_revoking_invite, quantity), Toast.LENGTH_SHORT)
         .show();
  }

  public static class Factory implements ViewModelProvider.Factory {

    private final Context    context;
    private final GroupId.V2 groupId;

    public Factory(@NonNull Context context, @NonNull GroupId.V2 groupId) {
      this.context = context;
      this.groupId = groupId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection unchecked
      return (T) new PendingMemberInvitesViewModel(context, new PendingMemberInvitesRepository(context.getApplicationContext(), groupId));
    }
  }
}
