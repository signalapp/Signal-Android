package org.thoughtcrime.securesms.groups.ui.invitesandrequests.requesting;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.LiveGroup;
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason;
import org.thoughtcrime.securesms.groups.ui.GroupErrors;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.groups.v2.GroupLinkUrlAndStatus;
import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.thoughtcrime.securesms.util.SingleLiveEvent;

import java.util.List;

public class RequestingMemberInvitesViewModel extends ViewModel {

  private final Context                                           context;
  private final RequestingMemberRepository                        requestingMemberRepository;
  private final MutableLiveData<String>                           toasts;
  private final LiveData<List<GroupMemberEntry.RequestingMember>> requesting;
  private final LiveData<GroupLinkUrlAndStatus>                   inviteLink;

  private RequestingMemberInvitesViewModel(@NonNull Context context,
                                           @NonNull GroupId.V2 groupId,
                                           @NonNull RequestingMemberRepository requestingMemberRepository)
  {
    this.context                    = context;
    this.requestingMemberRepository = requestingMemberRepository;
    this.toasts                     = new SingleLiveEvent<>();

    LiveGroup liveGroup = new LiveGroup(groupId);

    this.requesting = liveGroup.getRequestingMembers();
    this.inviteLink = liveGroup.getGroupLink();
  }

  LiveData<List<GroupMemberEntry.RequestingMember>> getRequesting() {
    return requesting;
  }

  LiveData<GroupLinkUrlAndStatus> getInviteLink() {
    return inviteLink;
  }

  LiveData<String> getToasts() {
    return toasts;
  }

  void approveRequestFor(@NonNull GroupMemberEntry.RequestingMember requestingMember) {
    requestingMember.setBusy(true);
    requestingMemberRepository.approveRequest(requestingMember.getRequester(), new AsynchronousCallback.WorkerThread<Void, GroupChangeFailureReason>() {
      @Override
      public void onComplete(@Nullable Void result) {
        requestingMember.setBusy(false);
        toasts.postValue(context.getString(R.string.RequestingMembersFragment_added_s, requestingMember.getRequester().getDisplayName(context)));
      }

      @Override
      public void onError(@Nullable GroupChangeFailureReason error) {
        requestingMember.setBusy(false);
        toasts.postValue(context.getString(GroupErrors.getUserDisplayMessage(error)));
      }
    });
  }

  void denyRequestFor(@NonNull GroupMemberEntry.RequestingMember requestingMember) {
    requestingMember.setBusy(true);
    requestingMemberRepository.denyRequest(requestingMember.getRequester(), new AsynchronousCallback.WorkerThread<Void, GroupChangeFailureReason>() {
      @Override
      public void onComplete(@Nullable Void result) {
        requestingMember.setBusy(false);
        toasts.postValue(context.getString(R.string.RequestingMembersFragment_denied_s, requestingMember.getRequester().getDisplayName(context)));
      }

      @Override
      public void onError(@Nullable GroupChangeFailureReason error) {
        requestingMember.setBusy(false);
        toasts.postValue(context.getString(GroupErrors.getUserDisplayMessage(error)));
      }
    });
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
      return (T) new RequestingMemberInvitesViewModel(context, groupId, new RequestingMemberRepository(context.getApplicationContext(), groupId));
    }
  }
}
