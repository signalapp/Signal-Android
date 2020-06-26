package org.thoughtcrime.securesms.conversation.ui.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.conversation.ui.error.SafetyNumberChangeRepository.SafetyNumberChangeState;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.List;
import java.util.Objects;

public final class SafetyNumberChangeViewModel extends ViewModel {

  private final SafetyNumberChangeRepository      safetyNumberChangeRepository;
  private final LiveData<SafetyNumberChangeState> safetyNumberChangeState;

  private SafetyNumberChangeViewModel(@NonNull List<RecipientId> recipientIds, @Nullable Long messageId, SafetyNumberChangeRepository safetyNumberChangeRepository) {
    this.safetyNumberChangeRepository = safetyNumberChangeRepository;
    safetyNumberChangeState           = this.safetyNumberChangeRepository.getSafetyNumberChangeState(recipientIds, messageId);
  }

  @NonNull LiveData<List<ChangedRecipient>> getChangedRecipients() {
    return Transformations.map(safetyNumberChangeState, SafetyNumberChangeState::getChangedRecipients);
  }

  @NonNull LiveData<TrustAndVerifyResult> trustOrVerifyChangedRecipients() {
    SafetyNumberChangeState state = Objects.requireNonNull(safetyNumberChangeState.getValue());
    if (state.getMessageRecord() != null) {
      return safetyNumberChangeRepository.trustOrVerifyChangedRecipientsAndResend(state.getChangedRecipients(), state.getMessageRecord());
    } else {
      return safetyNumberChangeRepository.trustOrVerifyChangedRecipients(state.getChangedRecipients());
    }
  }

  public static final class Factory implements ViewModelProvider.Factory {
    private final List<RecipientId> recipientIds;
    private final Long              messageId;

    public Factory(@NonNull List<RecipientId> recipientIds, @Nullable Long messageId) {
      this.recipientIds = recipientIds;
      this.messageId    = messageId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      SafetyNumberChangeRepository repo = new SafetyNumberChangeRepository(ApplicationDependencies.getApplication());
      return Objects.requireNonNull(modelClass.cast(new SafetyNumberChangeViewModel(recipientIds, messageId, repo)));
    }
  }
}
