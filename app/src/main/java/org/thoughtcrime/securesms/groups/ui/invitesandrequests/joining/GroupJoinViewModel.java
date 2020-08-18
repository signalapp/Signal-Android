package org.thoughtcrime.securesms.groups.ui.invitesandrequests.joining;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.groups.v2.GroupInviteLinkUrl;
import org.thoughtcrime.securesms.util.SingleLiveEvent;

public class GroupJoinViewModel extends ViewModel {

  private final MutableLiveData<GroupDetails>           groupDetails = new MutableLiveData<>();
  private final MutableLiveData<FetchGroupDetailsError> errors       = new SingleLiveEvent<>();
  private final MutableLiveData<Boolean>                busy         = new MediatorLiveData<>();

  private GroupJoinViewModel(@NonNull GroupJoinRepository repository) {
    busy.setValue(true);
    repository.getGroupDetails(new GroupJoinRepository.GetGroupDetailsCallback() {
      @Override
      public void onComplete(@NonNull GroupDetails details) {
        busy.postValue(false);
        groupDetails.postValue(details);
      }

      @Override
      public void onError(@NonNull FetchGroupDetailsError error) {
        busy.postValue(false);
        errors.postValue(error);
      }
    });
  }

  LiveData<GroupDetails> getGroupDetails() {
    return groupDetails;
  }

  LiveData<Boolean> isBusy() {
    return busy;
  }

  LiveData<FetchGroupDetailsError> getErrors() {
    return errors;
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
