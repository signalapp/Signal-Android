package org.thoughtcrime.securesms.payments.backup.confirm;

import androidx.annotation.NonNull;

final class PaymentsRecoveryPhraseConfirmViewState {

  private final int     word1Index;
  private final int     word2Index;
  private final boolean isWord1Valid;
  private final boolean isWord2Valid;

  private PaymentsRecoveryPhraseConfirmViewState(@NonNull Builder builder) {
    this.word1Index   = builder.word1Index;
    this.word2Index   = builder.word2Index;
    this.isWord1Valid = builder.isWord1Valid;
    this.isWord2Valid = builder.isWord2Valid;
  }

  int getWord1Index() {
    return word1Index;
  }

  int getWord2Index() {
    return word2Index;
  }

  boolean isWord1Valid() {
    return isWord1Valid;
  }

  boolean isWord2Valid() {
    return isWord2Valid;
  }

  boolean areAllWordsValid() {
    return isWord1Valid() && isWord2Valid();
  }

  @NonNull Builder buildUpon() {
    return new Builder(word1Index, word2Index).withValidWord1(isWord1Valid())
                                              .withValidWord2(isWord2Valid());
  }

  static @NonNull PaymentsRecoveryPhraseConfirmViewState init(int word1Index, int word2Index) {
    return new Builder(word1Index, word2Index).build();
  }

  static final class Builder {
    private final int word1Index;
    private final int word2Index;

    private boolean isWord1Valid;
    private boolean isWord2Valid;

    private Builder(int word1Index, int word2Index) {
      this.word1Index = word1Index;
      this.word2Index = word2Index;
    }

    @NonNull Builder withValidWord1(boolean isValid) {
      this.isWord1Valid = isValid;
      return this;
    }

    @NonNull Builder withValidWord2(boolean isValid) {
      this.isWord2Valid = isValid;
      return this;
    }

    @NonNull PaymentsRecoveryPhraseConfirmViewState build() {
      return new PaymentsRecoveryPhraseConfirmViewState(this);
    }
  }
}
