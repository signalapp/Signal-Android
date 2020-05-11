package org.thoughtcrime.securesms.messagerequests;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.livedata.LiveDataTriple;

import java.util.Collections;
import java.util.List;

public class MessageRequestViewModel extends ViewModel {

  private final SingleLiveEvent<Status>           status        = new SingleLiveEvent<>();
  private final MutableLiveData<Recipient>        recipient     = new MutableLiveData<>();
  private final MutableLiveData<List<String>>     groups        = new MutableLiveData<>(Collections.emptyList());
  private final MutableLiveData<GroupMemberCount> memberCount   = new MutableLiveData<>(GroupMemberCount.ZERO);
  private final MutableLiveData<DisplayState>     displayState  = new MutableLiveData<>();
  private final LiveData<RecipientInfo>           recipientInfo = Transformations.map(new LiveDataTriple<>(recipient, memberCount, groups),
                                                                                      triple -> new RecipientInfo(triple.first(), triple.second(), triple.third()));

  private final MessageRequestRepository repository;

  private LiveRecipient liveRecipient;
  private long          threadId;

  @SuppressWarnings("CodeBlock2Expr")
  private final RecipientForeverObserver recipientObserver = recipient -> {
    loadMessageRequestAccepted(recipient);
    this.recipient.setValue(recipient);
  };

  private MessageRequestViewModel(MessageRequestRepository repository) {
    this.repository = repository;
  }

  public void setConversationInfo(@NonNull RecipientId recipientId, long threadId) {
    if (liveRecipient != null) {
      liveRecipient.removeForeverObserver(recipientObserver);
    }

    liveRecipient = Recipient.live(recipientId);
    this.threadId = threadId;

    loadRecipient();
    loadGroups();
    loadMemberCount();
  }

  @Override
  protected void onCleared() {
    if (liveRecipient != null) {
      liveRecipient.removeForeverObserver(recipientObserver);
    }
  }

  public LiveData<DisplayState> getMessageRequestDisplayState() {
    return displayState;
  }

  public LiveData<Recipient> getRecipient() {
    return recipient;
  }

  public LiveData<RecipientInfo> getRecipientInfo() {
    return recipientInfo;
  }

  public LiveData<Status> getMessageRequestStatus() {
    return status;
  }

  public boolean shouldShowMessageRequest() {
    return displayState.getValue() == DisplayState.DISPLAY_MESSAGE_REQUEST;
  }

  @MainThread
  public void onAccept() {
    repository.acceptMessageRequest(liveRecipient, threadId, () -> {
      status.postValue(Status.ACCEPTED);
    });
  }

  @MainThread
  public void onDelete() {
    repository.deleteMessageRequest(liveRecipient, threadId, () -> {
      status.postValue(Status.DELETED);
    });
  }

  @MainThread
  public void onBlock() {
    repository.blockMessageRequest(liveRecipient, () -> {
      status.postValue(Status.BLOCKED);
    });
  }

  @MainThread
  public void onUnblock() {
    repository.unblockAndAccept(liveRecipient, threadId, () -> {
      status.postValue(Status.ACCEPTED);
    });
  }

  @MainThread
  public void onBlockAndDelete() {
    repository.blockAndDeleteMessageRequest(liveRecipient, threadId, () -> {
      status.postValue(Status.BLOCKED);
    });
  }

  private void loadRecipient() {
    liveRecipient.observeForever(recipientObserver);
    SignalExecutors.BOUNDED.execute(liveRecipient::refresh);
  }

  private void loadGroups() {
    repository.getGroups(liveRecipient.getId(), this.groups::postValue);
  }

  private void loadMemberCount() {
    repository.getMemberCount(liveRecipient.getId(), memberCount::postValue);
  }

  @SuppressWarnings("ConstantConditions")
  private void loadMessageRequestAccepted(@NonNull Recipient recipient) {
    if (FeatureFlags.messageRequests() && recipient.isBlocked()) {
      displayState.postValue(DisplayState.DISPLAY_MESSAGE_REQUEST);
      return;
    }

    repository.getMessageRequestState(recipient, threadId, accepted -> {
      switch (accepted) {
        case ACCEPTED:
          displayState.postValue(DisplayState.DISPLAY_NONE);
          break;
        case UNACCEPTED:
          displayState.postValue(DisplayState.DISPLAY_MESSAGE_REQUEST);
          break;
        case LEGACY:
          displayState.postValue(DisplayState.DISPLAY_LEGACY);
          break;
      }
    });
  }

  public static class RecipientInfo {
    @Nullable private final Recipient        recipient;
    @NonNull  private final GroupMemberCount groupMemberCount;
    @NonNull  private final List<String>     sharedGroups;

    private RecipientInfo(@Nullable Recipient recipient, @Nullable GroupMemberCount groupMemberCount, @Nullable List<String> sharedGroups) {
      this.recipient        = recipient;
      this.groupMemberCount = groupMemberCount == null ? GroupMemberCount.ZERO : groupMemberCount;
      this.sharedGroups     = sharedGroups == null ? Collections.emptyList() : sharedGroups;
    }

    @Nullable
    public Recipient getRecipient() {
      return recipient;
    }

    public int getGroupMemberCount() {
      return groupMemberCount.getFullMemberCount();
    }

    public int getGroupPendingMemberCount() {
      return groupMemberCount.getPendingMemberCount();
    }

    @NonNull
    public List<String> getSharedGroups() {
      return sharedGroups;
    }
  }

  public enum Status {
    BLOCKED,
    DELETED,
    ACCEPTED
  }

  public enum DisplayState {
    DISPLAY_MESSAGE_REQUEST, DISPLAY_LEGACY, DISPLAY_NONE
  }

  public static class Factory implements ViewModelProvider.Factory {

    private final Context context;

    public Factory(Context context) {
      this.context = context;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection unchecked
      return (T) new MessageRequestViewModel(new MessageRequestRepository(context.getApplicationContext()));
    }
  }

}
