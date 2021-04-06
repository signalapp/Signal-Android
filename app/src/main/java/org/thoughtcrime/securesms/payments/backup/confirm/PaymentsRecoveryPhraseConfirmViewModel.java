package org.thoughtcrime.securesms.payments.backup.confirm;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.payments.Mnemonic;
import org.thoughtcrime.securesms.payments.backup.PaymentsRecoveryRepository;
import org.thoughtcrime.securesms.util.livedata.Store;

import java.security.SecureRandom;
import java.util.Random;

public class PaymentsRecoveryPhraseConfirmViewModel extends ViewModel {

  private final Random                                        random;
  private final Mnemonic                                      mnemonic;
  private final Store<PaymentsRecoveryPhraseConfirmViewState> viewState;

  public PaymentsRecoveryPhraseConfirmViewModel() {
    PaymentsRecoveryRepository repository = new PaymentsRecoveryRepository();

    random   = new SecureRandom();
    mnemonic = repository.getMnemonic();

    this.viewState = new Store<>(PaymentsRecoveryPhraseConfirmViewState.init(-1, -1));
  }

  public void updateRandomIndices() {
    int[] indices = getRandomIndices();

    this.viewState.update(unused -> PaymentsRecoveryPhraseConfirmViewState.init(indices[0], indices[1]));
  }

  private int[] getRandomIndices() {
    int firstIndex  = random.nextInt(mnemonic.getWordCount());
    int secondIndex = random.nextInt(mnemonic.getWordCount());

    while (firstIndex == secondIndex) {
      secondIndex = random.nextInt(mnemonic.getWordCount());
    }

    return new int[]{firstIndex, secondIndex};
  }

  @NonNull LiveData<PaymentsRecoveryPhraseConfirmViewState> getViewState() {
    return viewState.getStateLiveData();
  }

  void validateWord1(@NonNull String entry) {
    viewState.update(s -> s.buildUpon().withValidWord1(mnemonic.getWords().get(s.getWord1Index()).equals(entry.toLowerCase())).build());
  }

  void validateWord2(@NonNull String entry) {
    viewState.update(s -> s.buildUpon().withValidWord2(mnemonic.getWords().get(s.getWord2Index()).equals(entry.toLowerCase())).build());
  }
}
