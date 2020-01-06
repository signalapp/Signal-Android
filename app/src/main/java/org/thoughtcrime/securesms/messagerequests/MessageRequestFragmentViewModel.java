package org.thoughtcrime.securesms.messagerequests;

import android.content.Context;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.recipients.RecipientForeverObserver;
import org.thoughtcrime.securesms.recipients.RecipientId;

public class MessageRequestFragmentViewModel extends ViewModel {

  private static final String TAG = MessageRequestFragmentViewModel.class.getSimpleName();

  private final MutableLiveData<MessageRequestFragmentState> internalState = new MutableLiveData<>();

  private final MessageRequestFragmentRepository repository;

  @SuppressWarnings("CodeBlock2Expr")
  private final RecipientForeverObserver recipientObserver = recipient -> {
    updateState(getNewState(s -> s.updateRecipient(recipient)));
  };

  private MessageRequestFragmentViewModel(@NonNull MessageRequestFragmentRepository repository) {
    internalState.setValue(new MessageRequestFragmentState(MessageRequestFragmentState.MessageRequestState.LOADING, null, null, null, 0));
    this.repository = repository;

    loadRecipient();
    loadMessageRecord();
    loadGroups();
    loadMemberCount();
  }

  @Override
  protected void onCleared() {
    repository.getLiveRecipient().removeForeverObserver(recipientObserver);
  }

  public @NonNull LiveData<MessageRequestFragmentState> getState() {
    return internalState;
  }

  @MainThread
  public void accept() {
    repository.acceptMessageRequest(() -> {
      MessageRequestFragmentState state = internalState.getValue();
      updateState(state.updateMessageRequestState(MessageRequestFragmentState.MessageRequestState.ACCEPTED));
    });
  }

  @MainThread
  public void delete() {
    repository.deleteMessageRequest(() -> {
      MessageRequestFragmentState state = internalState.getValue();
      updateState(state.updateMessageRequestState(MessageRequestFragmentState.MessageRequestState.DELETED));
    });
  }

  @MainThread
  public void block() {
    repository.blockMessageRequest(() -> {
      MessageRequestFragmentState state = internalState.getValue();
      updateState(state.updateMessageRequestState(MessageRequestFragmentState.MessageRequestState.BLOCKED));
    });
  }

  private void updateState(@NonNull MessageRequestFragmentState newState) {
    Log.i(TAG, "updateState: " + newState);
    internalState.setValue(newState);
  }

  private void loadRecipient() {
    repository.getLiveRecipient().observeForever(recipientObserver);
    repository.refreshRecipient();
  }

  private void loadMessageRecord() {
    repository.getMessageRecord(messageRecord -> {
      MessageRequestFragmentState newState = getNewState(s -> s.updateMessageRecord(messageRecord));
      updateState(newState);
    });
  }

  private void loadGroups() {
    repository.getGroups(groups -> {
      MessageRequestFragmentState newState = getNewState(s -> s.updateGroups(groups));
      updateState(newState);
    });
  }

  private void loadMemberCount() {
    repository.getMemberCount(memberCount -> {
      MessageRequestFragmentState newState = getNewState(s -> s.updateMemberCount(memberCount == null ? 0 : memberCount));
      updateState(newState);
    });
  }

  private @NonNull MessageRequestFragmentState getNewState(@NonNull Function<MessageRequestFragmentState, MessageRequestFragmentState> stateTransformer) {
    MessageRequestFragmentState oldState = internalState.getValue();
    MessageRequestFragmentState newState = stateTransformer.apply(oldState);
    return newState.updateMessageRequestState(getUpdatedRequestState(newState));
  }

  private static @NonNull MessageRequestFragmentState.MessageRequestState getUpdatedRequestState(@NonNull MessageRequestFragmentState state) {
    if (state.messageRequestState != MessageRequestFragmentState.MessageRequestState.LOADING) {
      return state.messageRequestState;
    }

    if (state.messageRecord != null && state.recipient != null && state.groups != null) {
      return MessageRequestFragmentState.MessageRequestState.PENDING;
    }

    return MessageRequestFragmentState.MessageRequestState.LOADING;
  }

  public static class Factory implements ViewModelProvider.Factory {
    private final Context     context;
    private final long        threadId;
    private final RecipientId recipientId;

    public Factory(@NonNull Context context, long threadId, @NonNull RecipientId recipientId) {
      this.context     = context;
      this.threadId    = threadId;
      this.recipientId = recipientId;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return (T) new MessageRequestFragmentViewModel(new MessageRequestFragmentRepository(context, recipientId, threadId));
    }
  }
}
