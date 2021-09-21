package org.thoughtcrime.securesms.conversation.ui.groupcall;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.GroupCallPeekEvent;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.Objects;

public class GroupCallViewModel extends ViewModel {

  private static final String TAG = Log.tag(GroupCallViewModel.class);

  private final MutableLiveData<Boolean> activeGroup;
  private final MutableLiveData<Boolean> ongoingGroupCall;
  private final LiveData<Boolean>        activeGroupCall;
  private final MutableLiveData<Boolean> groupCallHasCapacity;

  private @Nullable Recipient currentRecipient;

  GroupCallViewModel() {
    this.activeGroup          = new MutableLiveData<>(false);
    this.ongoingGroupCall     = new MutableLiveData<>(false);
    this.groupCallHasCapacity = new MutableLiveData<>(false);
    this.activeGroupCall      = LiveDataUtil.combineLatest(activeGroup, ongoingGroupCall, (active, ongoing) -> active && ongoing);
  }

  public @NonNull LiveData<Boolean> hasActiveGroupCall() {
    return activeGroupCall;
  }

  public @NonNull LiveData<Boolean> groupCallHasCapacity() {
    return groupCallHasCapacity;
  }

  public void onRecipientChange(@Nullable Recipient recipient) {
    activeGroup.postValue(recipient != null && recipient.isActiveGroup());

    if (Objects.equals(currentRecipient, recipient)) {
      return;
    }

    ongoingGroupCall.postValue(false);
    groupCallHasCapacity.postValue(false);

    currentRecipient = recipient;

    peekGroupCall();
  }

  public void peekGroupCall() {
    if (isGroupCallCapable(currentRecipient)) {
      Log.i(TAG, "peek call for " + currentRecipient.getId());
      ApplicationDependencies.getSignalCallManager().peekGroupCall(currentRecipient.getId());
    }
  }

  public void onGroupCallPeekEvent(@NonNull GroupCallPeekEvent groupCallPeekEvent) {
    if (isGroupCallCapable(currentRecipient) && groupCallPeekEvent.getGroupRecipientId().equals(currentRecipient.getId())) {
      Log.i(TAG, "update UI with call event: ongoing call: " + groupCallPeekEvent.isOngoing() + " hasCapacity: " + groupCallPeekEvent.callHasCapacity());

      ongoingGroupCall.postValue(groupCallPeekEvent.isOngoing());
      groupCallHasCapacity.postValue(groupCallPeekEvent.callHasCapacity());
    } else {
      Log.i(TAG, "Ignore call event for different recipient.");
    }
  }

  private static boolean isGroupCallCapable(@Nullable Recipient recipient) {
    return recipient != null && recipient.isActiveGroup() && recipient.isPushV2Group() && Build.VERSION.SDK_INT > 19;
  }

  public static final class Factory implements ViewModelProvider.Factory {
    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new GroupCallViewModel());
    }
  }
}
