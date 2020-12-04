package org.thoughtcrime.securesms.conversation.ui.groupcall;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.events.GroupCallPeekEvent;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.WebRtcCallService;
import org.thoughtcrime.securesms.util.FeatureFlags;

import java.util.Objects;

public class GroupCallViewModel extends ViewModel {

  private static final String TAG = Log.tag(GroupCallViewModel.class);

  private final MutableLiveData<Boolean> activeGroupCall;
  private final MutableLiveData<Boolean> canJoin;

  private @Nullable Recipient currentRecipient;

  GroupCallViewModel() {
    this.activeGroupCall = new MutableLiveData<>(false);
    this.canJoin         = new MutableLiveData<>(false);
  }

  public @NonNull LiveData<Boolean> hasActiveGroupCall() {
    return activeGroupCall;
  }

  public @NonNull LiveData<Boolean> canJoinGroupCall() {
    return canJoin;
  }

  public void onRecipientChange(@NonNull Context context, @Nullable Recipient recipient) {
    if (Objects.equals(currentRecipient, recipient)) {
      return;
    }

    activeGroupCall.postValue(false);
    canJoin.postValue(false);

    currentRecipient = recipient;

    peekGroupCall(context);
  }

  public void peekGroupCall(@NonNull Context context) {
    if (isGroupCallCapable(currentRecipient)) {
      Log.i(TAG, "peek call for " + currentRecipient.getId());
      Intent intent = new Intent(context, WebRtcCallService.class);
      intent.setAction(WebRtcCallService.ACTION_GROUP_CALL_PEEK)
            .putExtra(WebRtcCallService.EXTRA_REMOTE_PEER, new RemotePeer(currentRecipient.getId()));

      context.startService(intent);
    }
  }

  public void onGroupCallPeekEvent(@NonNull GroupCallPeekEvent groupCallPeekEvent) {
    if (isGroupCallCapable(currentRecipient) && groupCallPeekEvent.getGroupRecipientId().equals(currentRecipient.getId())) {
      Log.i(TAG, "update UI with call event: active call: " + groupCallPeekEvent.hasActiveCall() + " canJoin: " + groupCallPeekEvent.canJoinCall());

      activeGroupCall.postValue(groupCallPeekEvent.hasActiveCall());
      canJoin.postValue(groupCallPeekEvent.canJoinCall());
    } else {
      Log.i(TAG, "Ignore call event for different recipient.");
    }
  }

  private static boolean isGroupCallCapable(@Nullable Recipient recipient) {
    return recipient != null && recipient.isActiveGroup() && recipient.isPushV2Group() && FeatureFlags.groupCalling();
  }

  public static final class Factory implements ViewModelProvider.Factory {
    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new GroupCallViewModel());
    }
  }
}
