package org.thoughtcrime.securesms.messagerequests;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason;
import org.thoughtcrime.securesms.profiles.spoofing.ReviewUtil;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.livedata.LiveDataTriple;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.Collections;
import java.util.List;

public class MessageRequestViewModel extends ViewModel {

  private final SingleLiveEvent<Status>                   status        = new SingleLiveEvent<>();
  private final SingleLiveEvent<GroupChangeFailureReason> failures      = new SingleLiveEvent<>();
  private final MutableLiveData<Recipient>                recipient     = new MutableLiveData<>();
  private final LiveData<MessageData>                     messageData;
  private final MutableLiveData<List<String>>             groups        = new MutableLiveData<>(Collections.emptyList());
  private final MutableLiveData<GroupMemberCount>         memberCount   = new MutableLiveData<>(GroupMemberCount.ZERO);
  private final LiveData<RequestReviewDisplayState>       requestReviewDisplayState;
  private final LiveData<RecipientInfo>                   recipientInfo = Transformations.map(new LiveDataTriple<>(recipient, memberCount, groups),
                                                                                              triple -> new RecipientInfo(triple.first(), triple.second(), triple.third()));

  private final MessageRequestRepository repository;

  private LiveRecipient liveRecipient;
  private long          threadId;

  private final RecipientForeverObserver recipientObserver = recipient -> {
    loadMemberCount();
    this.recipient.setValue(recipient);
  };

  private MessageRequestViewModel(MessageRequestRepository repository) {
    this.repository                = repository;
    this.messageData               = LiveDataUtil.mapAsync(recipient, this::createMessageDataForRecipient);
    this.requestReviewDisplayState = LiveDataUtil.mapAsync(messageData, MessageRequestViewModel::transformHolderToReviewDisplayState);
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

  public LiveData<RequestReviewDisplayState> getRequestReviewDisplayState() {
    return requestReviewDisplayState;
  }

  public LiveData<Recipient> getRecipient() {
    return recipient;
  }

  public LiveData<MessageData> getMessageData() {
    return messageData;
  }

  public LiveData<RecipientInfo> getRecipientInfo() {
    return recipientInfo;
  }

  public LiveData<Status> getMessageRequestStatus() {
    return status;
  }

  public LiveData<GroupChangeFailureReason> getFailures() {
    return failures;
  }

  public boolean shouldShowMessageRequest() {
    MessageData data = messageData.getValue();
    return data != null && data.getMessageState() != MessageRequestState.NONE;
  }

  @MainThread
  public void onAccept() {
    status.setValue(Status.ACCEPTING);
    repository.acceptMessageRequest(liveRecipient,
                                    threadId,
                                    () -> status.postValue(Status.ACCEPTED),
                                    this::onGroupChangeError);
  }

  @MainThread
  public void onDelete() {
    status.setValue(Status.DELETING);
    repository.deleteMessageRequest(liveRecipient,
                                    threadId,
                                    () -> status.postValue(Status.DELETED),
                                    this::onGroupChangeError);
  }

  @MainThread
  public void onBlock() {
    status.setValue(Status.BLOCKING);
    repository.blockMessageRequest(liveRecipient,
                                   () -> status.postValue(Status.BLOCKED),
                                   this::onGroupChangeError);
  }

  @MainThread
  public void onUnblock() {
    repository.unblockAndAccept(liveRecipient,
                                threadId,
                                () -> status.postValue(Status.ACCEPTED));
  }

  @MainThread
  public void onBlockAndDelete() {
    repository.blockAndDeleteMessageRequest(liveRecipient,
                                            threadId,
                                            () -> status.postValue(Status.BLOCKED),
                                            this::onGroupChangeError);
  }

  private void onGroupChangeError(@NonNull GroupChangeFailureReason error) {
    status.postValue(Status.IDLE);
    failures.postValue(error);
  }

  private void loadRecipient() {
    liveRecipient.observeForever(recipientObserver);
    SignalExecutors.BOUNDED.execute(() -> {
      liveRecipient.refresh();
      recipient.postValue(liveRecipient.get());
    });
  }

  private void loadGroups() {
    repository.getGroups(liveRecipient.getId(), this.groups::postValue);
  }

  private void loadMemberCount() {
    repository.getMemberCount(liveRecipient.getId(), memberCount::postValue);
  }

  private static RequestReviewDisplayState transformHolderToReviewDisplayState(@NonNull MessageData holder) {
    if (holder.getMessageState() == MessageRequestState.INDIVIDUAL) {
      return ReviewUtil.isRecipientReviewSuggested(holder.getRecipient().getId()) ? RequestReviewDisplayState.SHOWN
                                                                                  : RequestReviewDisplayState.HIDDEN;
    } else {
      return RequestReviewDisplayState.NONE;
    }
  }

  @WorkerThread
  private @NonNull MessageData createMessageDataForRecipient(@NonNull Recipient recipient) {
    MessageRequestState state = repository.getMessageRequestState(recipient, threadId);
    return new MessageData(recipient, state);
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
    IDLE,
    BLOCKING,
    BLOCKED,
    DELETING,
    DELETED,
    ACCEPTING,
    ACCEPTED
  }

  public enum RequestReviewDisplayState {
    HIDDEN,
    SHOWN,
    NONE
  }

  public static final class MessageData {
    private final Recipient    recipient;
    private final MessageRequestState messageState;

    public MessageData(@NonNull Recipient recipient, @NonNull MessageRequestState messageState) {
      this.recipient    = recipient;
      this.messageState = messageState;
    }

    public @NonNull Recipient getRecipient() {
      return recipient;
    }

    public @NonNull MessageRequestState getMessageState() {
      return messageState;
    }
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
