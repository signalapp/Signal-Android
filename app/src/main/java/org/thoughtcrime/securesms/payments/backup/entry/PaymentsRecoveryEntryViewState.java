package org.thoughtcrime.securesms.payments.backup.entry;

import androidx.annotation.Nullable;

class PaymentsRecoveryEntryViewState {

  private final int     wordIndex;
  private final boolean canMoveToNext;
  private final String  currentEntry;

  PaymentsRecoveryEntryViewState() {
    this.wordIndex     = 0;
    this.canMoveToNext = false;
    this.currentEntry  = null;
  }

  PaymentsRecoveryEntryViewState(int wordIndex, boolean canMoveToNext, String currentEntry) {
    this.wordIndex     = wordIndex;
    this.canMoveToNext = canMoveToNext;
    this.currentEntry  = currentEntry;
  }

  int getWordIndex() {
    return wordIndex;
  }

  boolean canMoveToNext() {
    return canMoveToNext;
  }

  @Nullable String getCurrentEntry() {
    return currentEntry;
  }
}
