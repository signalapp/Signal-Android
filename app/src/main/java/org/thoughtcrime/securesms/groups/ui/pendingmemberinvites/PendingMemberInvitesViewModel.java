package org.thoughtcrime.securesms.groups.ui.pendingmemberinvites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.logging.Log;

import java.util.ArrayList;
import java.util.List;

public class PendingMemberInvitesViewModel extends ViewModel {

  private static final String TAG = Log.tag(PendingMemberInvitesViewModel.class);

  private final Context                                                           context;
  private final GroupId                                                           groupId;
  private final PendingMemberRepository                                           pendingMemberRepository;
  private final MutableLiveData<List<GroupMemberEntry.PendingMember>>             whoYouInvited           = new MutableLiveData<>();
  private final MutableLiveData<List<GroupMemberEntry.UnknownPendingMemberCount>> whoOthersInvited        = new MutableLiveData<>();

  PendingMemberInvitesViewModel(@NonNull Context context,
                                @NonNull GroupId.V2 groupId,
                                @NonNull PendingMemberRepository pendingMemberRepository)
  {
    this.context                 = context;
    this.groupId                 = groupId;
    this.pendingMemberRepository = pendingMemberRepository;

    pendingMemberRepository.getInvitees(groupId, this::setMembers);
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

  private void setMembers(PendingMemberRepository.InviteeResult inviteeResult) {
    List<GroupMemberEntry.PendingMember>             byMe     = new ArrayList<>(inviteeResult.getByMe().size());
    List<GroupMemberEntry.UnknownPendingMemberCount> byOthers = new ArrayList<>(inviteeResult.getByOthers().size());

    for (PendingMemberRepository.SinglePendingMemberInvitedByYou pendingMember : inviteeResult.getByMe()) {
      byMe.add(new GroupMemberEntry.PendingMember(pendingMember.getInvitee(),
                                                  pendingMember.getInviteeCipherText()));
    }

    for (PendingMemberRepository.MultiplePendingMembersInvitedByAnother pendingMembers : inviteeResult.getByOthers()) {
      byOthers.add(new GroupMemberEntry.UnknownPendingMemberCount(pendingMembers.getInviter(),
                                                                  pendingMembers.getUuidCipherTexts().size()));
    }

    setInvitees(byMe, byOthers);
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
      return (T) new PendingMemberInvitesViewModel(context, groupId, new PendingMemberRepository(context.getApplicationContext()));
    }
  }
}
