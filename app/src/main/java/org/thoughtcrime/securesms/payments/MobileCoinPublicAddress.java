package org.thoughtcrime.securesms.payments;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mobilecoin.lib.PrintableWrapper;
import com.mobilecoin.lib.PublicAddress;
import com.mobilecoin.lib.exceptions.InvalidUriException;
import com.mobilecoin.lib.exceptions.SerializationException;

import org.signal.core.util.logging.Log;

public final class MobileCoinPublicAddress {

  private static final String TAG = Log.tag(MobileCoinPublicAddress.class);

  private final com.mobilecoin.lib.PublicAddress publicAddress;
  private final String                           base58;
  private final Uri                              uri;

  static @NonNull MobileCoinPublicAddress fromPublicAddress(@Nullable PublicAddress publicAddress) throws AddressException {
    if (publicAddress == null) {
      throw new AddressException("Does not contain a public address");
    }
    return new MobileCoinPublicAddress(publicAddress);
  }

  MobileCoinPublicAddress(@NonNull PublicAddress publicAddress) {
    this.publicAddress = publicAddress;
    try {
      PrintableWrapper printableWrapper = PrintableWrapper.fromPublicAddress(publicAddress);
      this.base58 = printableWrapper.toB58String();
      this.uri    = printableWrapper.toUri();
    } catch (SerializationException e) {
      throw new AssertionError(e);
    }
  }

  public static @Nullable MobileCoinPublicAddress fromBytes(@Nullable byte[] bytes) {
    if (bytes == null) {
      return null;
    }

    try {
      return new MobileCoinPublicAddress(PublicAddress.fromBytes(bytes));
    } catch (SerializationException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  public static MobileCoinPublicAddress fromBase58NullableOrThrow(@Nullable String base58String) {
    return base58String != null ? fromBase58OrThrow(base58String) : null;
  }

  public static @NonNull MobileCoinPublicAddress fromBase58OrThrow(@NonNull String base58String) {
    try {
      return fromBase58(base58String);
    } catch (AddressException e) {
      throw new AssertionError(e);
    }
  }

  public static MobileCoinPublicAddress fromBase58(@NonNull String base58String) throws AddressException {
    try {
      PublicAddress publicAddress = PrintableWrapper.fromB58String(base58String).getPublicAddress();

      return MobileCoinPublicAddress.fromPublicAddress(publicAddress);
    } catch (SerializationException e) {
      throw new AddressException(e);
    }
  }

  public static @NonNull MobileCoinPublicAddress fromQr(@NonNull String data) throws AddressException {
    try {
      PrintableWrapper printableWrapper = PrintableWrapper.fromUri(Uri.parse(data));
      return MobileCoinPublicAddress.fromPublicAddress(printableWrapper.getPublicAddress());
    } catch (SerializationException | InvalidUriException e) {
      return fromBase58(data);
    }
  }

  public @NonNull String getPaymentAddressBase58() {
    return base58;
  }

  public @NonNull Uri getPaymentAddressUri() {
    return uri;
  }

  public @NonNull byte[] serialize() {
    return publicAddress.toByteArray();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MobileCoinPublicAddress)) return false;

    return base58.equals(((MobileCoinPublicAddress) o).base58);
  }

  @Override
  public int hashCode() {
    return base58.hashCode();
  }

  @Override
  public @NonNull String toString() {
    return base58;
  }

  PublicAddress getAddress() {
    return publicAddress;
  }

  public static final class AddressException extends Exception {

    private AddressException(Throwable e) {
      super(e);
    }

    private AddressException(String message) {
      super(message);
    }
  }
}
