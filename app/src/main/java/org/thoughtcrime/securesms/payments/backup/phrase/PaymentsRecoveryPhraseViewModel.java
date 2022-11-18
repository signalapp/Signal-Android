package org.thoughtcrime.securesms.payments.backup.phrase;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.util.SingleLiveEvent;

import java.util.List;

public class PaymentsRecoveryPhraseViewModel extends ViewModel {

  private final SingleLiveEvent<SubmitResult>    submitResult = new SingleLiveEvent<>();
  private final PaymentsRecoveryPhraseRepository repository   = new PaymentsRecoveryPhraseRepository();

  public LiveData<SubmitResult> getSubmitResult() {
    return submitResult;
  }

  void onSubmit(List<String> words) {
    repository.restoreMnemonic(words, result -> {
      switch (result) {
        case ENTROPY_CHANGED:
        case ENTROPY_UNCHANGED:
          submitResult.postValue(SubmitResult.SUCCESS);
          break;
        case MNEMONIC_ERROR:
          submitResult.postValue(SubmitResult.ERROR);
      }
    });
  }

  enum SubmitResult {
    SUCCESS,
    ERROR
  }
}
