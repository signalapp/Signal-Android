package org.thoughtcrime.securesms.payments.backup.phrase;

import androidx.annotation.NonNull;

final class MnemonicPart {

  private final String word;
  private final int    index;

  MnemonicPart(int index, @NonNull String word) {
    this.word  = word;
    this.index = index;
  }

  public String getWord() {
    return word;
  }

  public int getIndex() {
    return index;
  }
}
