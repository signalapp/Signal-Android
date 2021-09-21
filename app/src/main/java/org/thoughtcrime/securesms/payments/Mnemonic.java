package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;

import com.mobilecoin.lib.Mnemonics;
import com.mobilecoin.lib.exceptions.BadMnemonicException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Mnemonic {

  public static final List<String> BIP39_WORDS_ENGLISH;

  private final String   mnemonic;
  private final String[] words;

  static {
    try {
      BIP39_WORDS_ENGLISH = Collections.unmodifiableList(Arrays.asList(Mnemonics.wordsByPrefix("")));
    } catch (BadMnemonicException e) {
      throw new AssertionError(e);
    }
  }

  public Mnemonic(@NonNull String mnemonic) {
    this.mnemonic = mnemonic;
    this.words    = mnemonic.split(" ");
  }

  public @NonNull List<String> getWords() {
    return Collections.unmodifiableList(Arrays.asList(words));
  }

  public int getWordCount() {
    return words.length;
  }

  public String getMnemonic() {
    return mnemonic;
  }
}
