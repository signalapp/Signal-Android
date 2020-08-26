package org.thoughtcrime.securesms.groups.ui.invitesandrequests.joining;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.groups.v2.GroupInviteLinkUrl;
import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.thoughtcrime.securesms.util.SingleLiveEvent;

public class GroupJoinViewModel extends ViewModel {

  private final GroupJoinRepository                     repository;
  private final MutableLiveData<GroupDetails>           groupDetails = new MutableLiveData<>();
  private final MutableLiveData<FetchGroupDetailsError> errors       = new SingleLiveEvent<>();
  private final MutableLiveData<JoinGroupError>         joinErrors   = new SingleLiveEvent<>();
  private final MutableLiveData<Boolean>                busy         = new MediatorLiveData<>();
  private final MutableLiveData<JoinGroupSuccess>       joinSuccess  = new SingleLiveEvent<>();

  private GroupJoinViewModel(@NonNull GroupJoinRepository repository) {
    this.repository = repository;

    busy.setValue(true);
    repository.getGroupDetails(new AsynchronousCallback.WorkerThread<GroupDetails, FetchGroupDetailsError>() {
      @Override
      public void onComplete(@Nullable GroupDetails details) {
        busy.postValue(false);
        groupDetails.postValue(details);
      }

      @Override
      public void onError(@Nullable FetchGroupDetailsError error) {
        busy.postValue(false);
        errors.postValue(error);
      }
    });
  }

  void join(@NonNull GroupDetails groupDetails) {
    busy.setValue(true);
    repository.joinGroup(groupDetails, new AsynchronousCallback.WorkerThread<JoinGroupSuccess, JoinGroupError>() {
      @Override
      public void onComplete(@Nullable JoinGroupSuccess result) {
        busy.postValue(false);
        joinSuccess.postValue(result);
      }

      @Override
      public void onError(@Nullable JoinGroupError error) {
        busy.postValue(false);
        joinErrors.postValue(error);
      }
    });
  }

  LiveData<GroupDetails> getGroupDetails() {
    return groupDetails;
  }

  LiveData<JoinGroupSuccess> getJoinSuccess() {
    return joinSuccess;
  }

  LiveData<Boolean> isBusy() {
    return busy;
  }

  LiveData<FetchGroupDetailsError> getErrors() {
    return errors;
  }

  LiveData<JoinGroupError> getJoinErrors() {
    return joinErrors;
  }

  public static class Factory implements ViewModelProvider.Factory {

    private final Context context;
    private final GroupInviteLinkUrl groupInviteLinkUrl;

    public Factory(@NonNull Context context, @NonNull GroupInviteLinkUrl groupInviteLinkUrl) {
      this.context            = context;
      this.groupInviteLinkUrl = groupInviteLinkUrl;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection unchecked
      return (T) new GroupJoinViewModel(new GroupJoinRepository(context.getApplicationContext(), groupInviteLinkUrl));
    }
  }
}
