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

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
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
  private final MutableLiveData<DisplayState>             displayState  = new MutableLiveData<>();
  private final LiveData<RecipientInfo>                   recipientInfo = Transformations.map(new LiveDataTriple<>(recipient, memberCount, groups),
                                                                                              triple -> new RecipientInfo(triple.first(), triple.second(), triple.third()));

  private final MessageRequestRepository repository;

  private LiveRecipient liveRecipient;
  private long          threadId;

  private final RecipientForeverObserver recipientObserver = recipient -> {
    loadMessageRequestAccepted(recipient);
    loadMemberCount();
    this.recipient.setValue(recipient);
  };

  private MessageRequestViewModel(MessageRequestRepository repository) {
    this.repository  = repository;
    this.messageData = LiveDataUtil.mapAsync(recipient, this::createMessageDataForRecipient);
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
    return displayState.getValue() == DisplayState.DISPLAY_MESSAGE_REQUEST;
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
    SignalExecutors.BOUNDED.execute(liveRecipient::refresh);
  }

  private void loadGroups() {
    repository.getGroups(liveRecipient.getId(), this.groups::postValue);
  }

  private void loadMemberCount() {
    repository.getMemberCount(liveRecipient.getId(), memberCount::postValue);
  }

  @WorkerThread
  private @NonNull MessageData createMessageDataForRecipient(@NonNull Recipient recipient) {
    if (recipient.isBlocked()) {
      if (recipient.isGroup()) {
        return new MessageData(recipient, MessageClass.BLOCKED_GROUP);
      } else {
        return new MessageData(recipient, MessageClass.BLOCKED_INDIVIDUAL);
      }
    } else if (recipient.isPushV2Group()) {
      if (repository.isPendingMember(recipient.requireGroupId().requireV2())) {
        return new MessageData(recipient, MessageClass.GROUP_V2_INVITE);
      } else {
        return new MessageData(recipient, MessageClass.GROUP_V2_ADD);
      }
    } else if (isLegacyThread(recipient)) {
      if (recipient.isGroup()) {
        return new MessageData(recipient, MessageClass.LEGACY_GROUP_V1);
      } else {
        return new MessageData(recipient, MessageClass.LEGACY_INDIVIDUAL);
      }
    } else if (recipient.isGroup()) {
      return new MessageData(recipient, MessageClass.GROUP_V1);
    } else {
      return new MessageData(recipient, MessageClass.INDIVIDUAL);
    }
  }

  @SuppressWarnings("ConstantConditions")
  private void loadMessageRequestAccepted(@NonNull Recipient recipient) {
    if (recipient.isBlocked()) {
      displayState.postValue(DisplayState.DISPLAY_MESSAGE_REQUEST);
      return;
    }

    repository.getMessageRequestState(recipient, threadId, accepted -> {
      switch (accepted) {
        case NOT_REQUIRED:
          displayState.postValue(DisplayState.DISPLAY_NONE);
          break;
        case REQUIRED:
          displayState.postValue(DisplayState.DISPLAY_MESSAGE_REQUEST);
          break;
        case PRE_MESSAGE_REQUEST:
          displayState.postValue(DisplayState.DISPLAY_PRE_MESSAGE_REQUEST);
          break;
      }
    });
  }

  @WorkerThread
  private boolean isLegacyThread(@NonNull Recipient recipient) {
    Context context  = ApplicationDependencies.getApplication();
    Long    threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient.getId());

    return FeatureFlags.modernProfileSharing() &&
           threadId != null                    &&
           (RecipientUtil.hasSentMessageInThread(context, threadId) || RecipientUtil.isPreMessageRequestThread(context, threadId));
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

  public enum DisplayState {
    DISPLAY_MESSAGE_REQUEST, DISPLAY_PRE_MESSAGE_REQUEST, DISPLAY_NONE
  }

  public enum MessageClass {
    BLOCKED_INDIVIDUAL,
    BLOCKED_GROUP,
    /** An individual conversation that existed pre-message-requests but doesn't have profile sharing enabled */
    LEGACY_INDIVIDUAL,
    /** A V1 group conversation that existed pre-message-requests but doesn't have profile sharing enabled */
    LEGACY_GROUP_V1,
    GROUP_V1,
    GROUP_V2_INVITE,
    GROUP_V2_ADD,
    INDIVIDUAL
  }

  public static final class MessageData {
    private final Recipient    recipient;
    private final MessageClass messageClass;

    public MessageData(@NonNull Recipient recipient, @NonNull MessageClass messageClass) {
      this.recipient    = recipient;
      this.messageClass = messageClass;
    }

    public @NonNull Recipient getRecipient() {
      return recipient;
    }

    public @NonNull MessageClass getMessageClass() {
      return messageClass;
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
