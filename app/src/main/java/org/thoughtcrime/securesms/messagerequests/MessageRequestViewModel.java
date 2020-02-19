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
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.livedata.LiveDataTriple;

import java.util.Collections;
import java.util.List;

public class MessageRequestViewModel extends ViewModel {

  private final SingleLiveEvent<Status>       status                      = new SingleLiveEvent<>();
  private final MutableLiveData<Recipient>    recipient                   = new MutableLiveData<>();
  private final MutableLiveData<List<String>> groups                      = new MutableLiveData<>(Collections.emptyList());
  private final MutableLiveData<Integer>      memberCount                 = new MutableLiveData<>(0);
  private final MutableLiveData<Boolean>      shouldDisplayMessageRequest = new MutableLiveData<>();
  private final LiveData<RecipientInfo>       recipientInfo               = Transformations.map(new LiveDataTriple<>(recipient, memberCount, groups),
                                                                                                triple -> new RecipientInfo(triple.first(), triple.second(), triple.third()));

  private final MessageRequestRepository repository;

  private LiveRecipient liveRecipient;
  private long          threadId;

  @SuppressWarnings("CodeBlock2Expr")
  private final RecipientForeverObserver recipientObserver = recipient -> {
    if (Recipient.self().equals(recipient) || recipient.isBlocked() || recipient.isForceSmsSelection() || !recipient.isRegistered()) {
      shouldDisplayMessageRequest.setValue(false);
    } else {
      loadMessageRequestAccepted();
    }
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

  public LiveData<Boolean> getShouldDisplayMessageRequest() {
    return shouldDisplayMessageRequest;
  }

  public LiveData<Recipient> getRecipient() {
    return recipient;
  }

  public LiveData<RecipientInfo> getRecipientInfo() {
    return recipientInfo;
  }

  public LiveData<Status> getMesasgeRequestStatus() {
    return status;
  }

  @MainThread
  public void accept() {
    repository.acceptMessageRequest(liveRecipient, threadId, () -> {
      status.setValue(Status.ACCEPTED);
    });
  }

  @MainThread
  public void delete() {
    repository.deleteMessageRequest(threadId, () -> {
      status.setValue(Status.DELETED);
    });
  }

  @MainThread
  public void block() {
    repository.blockMessageRequest(liveRecipient, () -> {
      status.setValue(Status.BLOCKED);
    });
  }

  private void loadRecipient() {
    liveRecipient.observeForever(recipientObserver);
    SignalExecutors.BOUNDED.execute(liveRecipient::refresh);
  }

  private void loadGroups() {
    repository.getGroups(liveRecipient.getId(), this.groups::setValue);
  }

  private void loadMemberCount() {
    repository.getMemberCount(liveRecipient.getId(), memberCount -> {
      this.memberCount.setValue(memberCount == null ? 0 : memberCount);
    });
  }

  @SuppressWarnings("ConstantConditions")
  private void loadMessageRequestAccepted() {
    repository.getMessageRequestAccepted(threadId, accepted -> shouldDisplayMessageRequest.setValue(!accepted));
  }

  public static class Factory implements ViewModelProvider.Factory {

    private final Context context;

    public Factory(Context context) {
      this.context = context;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return (T) new MessageRequestViewModel(new MessageRequestRepository(context.getApplicationContext()));
    }
  }

  public static class RecipientInfo {
    private final @Nullable Recipient    recipient;
    private final           int          groupMemberCount;
    private final @NonNull  List<String> sharedGroups;

    private RecipientInfo(@Nullable Recipient recipient, @Nullable Integer groupMemberCount, @Nullable List<String> sharedGroups) {
      this.recipient        = recipient;
      this.groupMemberCount = groupMemberCount == null ? 0 : groupMemberCount;
      this.sharedGroups     = sharedGroups == null ? Collections.emptyList() : sharedGroups;
    }

    @Nullable
    public Recipient getRecipient() {
      return recipient;
    }

    public int getGroupMemberCount() {
      return groupMemberCount;
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
}
