package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mobilecoin.lib.Mnemonics;
import com.mobilecoin.lib.exceptions.BadEntropyException;
import com.mobilecoin.lib.exceptions.BadMnemonicException;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.payments.PaymentsConstants;

import java.util.Arrays;
import java.util.Locale;

public final class Entropy {
  private static final String TAG = Log.tag(Entropy.class);

  private final byte[] bytes;

  Entropy(@NonNull byte[] bytes) {
    this.bytes = bytes;
  }

  public static @NonNull Entropy generateNew() {
    return new Entropy(Util.getSecretBytes(PaymentsConstants.PAYMENTS_ENTROPY_LENGTH));
  }

  public static Entropy fromBytes(@Nullable byte[] bytes) {
    if (bytes == null) {
      return null;
    }
    if (bytes.length == PaymentsConstants.PAYMENTS_ENTROPY_LENGTH) {
      return new Entropy(bytes);
    } else {
      Log.w(TAG, String.format(Locale.US, "Entropy was supplied of length %d and ignored", bytes.length), new Throwable());
      return null;
    }
  }

  public byte[] getBytes() {
    return bytes;
  }

  public Mnemonic asMnemonic() {
    try {
      String mnemonic = Mnemonics.bip39EntropyToMnemonic(bytes);
      byte[] check    = Mnemonics.bip39EntropyFromMnemonic(mnemonic);
      if (!Arrays.equals(bytes, check)) {
        throw new AssertionError("Round trip mnemonic failure");
      }
      return new Mnemonic(mnemonic);
    } catch (BadEntropyException | BadMnemonicException e) {
      throw new AssertionError(e);
    }
  }
}
