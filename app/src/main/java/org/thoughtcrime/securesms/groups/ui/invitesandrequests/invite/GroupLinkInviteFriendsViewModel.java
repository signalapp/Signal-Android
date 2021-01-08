package org.thoughtcrime.securesms.groups.ui.invitesandrequests.invite;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.LiveGroup;
import org.thoughtcrime.securesms.groups.v2.GroupInviteLinkUrl;
import org.thoughtcrime.securesms.groups.v2.GroupLinkUrlAndStatus;
import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.thoughtcrime.securesms.util.SingleLiveEvent;

public class GroupLinkInviteFriendsViewModel extends ViewModel {

  private static final boolean INITIAL_MEMBER_APPROVAL_STATE = false;

  private final GroupLinkInviteRepository              repository;
  private final MutableLiveData<EnableInviteLinkError> enableErrors   = new SingleLiveEvent<>();
  private final MutableLiveData<Boolean>               busy           = new MediatorLiveData<>();
  private final MutableLiveData<GroupInviteLinkUrl>    enableSuccess  = new SingleLiveEvent<>();
  private final LiveData<GroupLinkUrlAndStatus>        groupLink;
  private final MutableLiveData<Boolean>               memberApproval = new MutableLiveData<>(INITIAL_MEMBER_APPROVAL_STATE);

  private GroupLinkInviteFriendsViewModel(GroupId.V2 groupId, @NonNull GroupLinkInviteRepository repository) {
    this.repository = repository;

    LiveGroup liveGroup = new LiveGroup(groupId);

    this.groupLink = liveGroup.getGroupLink();
  }

  LiveData<GroupLinkUrlAndStatus> getGroupInviteLinkAndStatus() {
    return groupLink;
  }

  void enable() {
    busy.setValue(true);
    repository.enableGroupInviteLink(getCurrentMemberApproval(), new AsynchronousCallback.WorkerThread<GroupInviteLinkUrl, EnableInviteLinkError>() {
      @Override
      public void onComplete(@Nullable GroupInviteLinkUrl groupInviteLinkUrl) {
        busy.postValue(false);
        enableSuccess.postValue(groupInviteLinkUrl);
      }

      @Override
      public void onError(@Nullable EnableInviteLinkError error) {
        busy.postValue(false);
        enableErrors.postValue(error);
      }
    });
  }

  LiveData<Boolean> isBusy() {
    return busy;
  }

  LiveData<GroupInviteLinkUrl> getEnableSuccess() {
    return enableSuccess;
  }

  LiveData<EnableInviteLinkError> getEnableErrors() {
    return enableErrors;
  }

  LiveData<Boolean> getMemberApproval() {
    return memberApproval;
  }

  private boolean getCurrentMemberApproval() {
    Boolean value = memberApproval.getValue();
    if (value == null) {
      return INITIAL_MEMBER_APPROVAL_STATE;
    }
    return value;
  }

  void toggleMemberApproval() {
    memberApproval.postValue(!getCurrentMemberApproval());
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
      return (T) new GroupLinkInviteFriendsViewModel(groupId, new GroupLinkInviteRepository(context.getApplicationContext(), groupId));
    }
  }
}
