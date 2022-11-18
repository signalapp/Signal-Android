package org.thoughtcrime.securesms.payments.preferences.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.payments.MobileCoinPublicAddress;
import org.thoughtcrime.securesms.payments.Payee;
import org.thoughtcrime.securesms.recipients.RecipientId;

public final class PayeeParcelable implements Parcelable {
  private final Payee payee;

  public PayeeParcelable(@NonNull Payee payee) {
    this.payee = payee;
  }

  public PayeeParcelable(@NonNull RecipientId recipientId) {
    this(new Payee(recipientId));
  }

  public PayeeParcelable(@NonNull RecipientId recipientId, @NonNull MobileCoinPublicAddress address) {
    this(Payee.fromRecipientAndAddress(recipientId, address));
  }

  public PayeeParcelable(@NonNull MobileCoinPublicAddress publicAddress) {
    this(new Payee(publicAddress));
  }

  public Payee getPayee() {
    return payee;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof PayeeParcelable)) return false;

    PayeeParcelable other = (PayeeParcelable) o;
    return payee.equals(other.payee);
  }

  @Override
  public int hashCode() {
    return payee.hashCode();
  }

  private static final int UNKNOWN                           = 0;
  private static final int CONTAINS_RECIPIENT_ID             = 1;
  private static final int CONTAINS_ADDRESS                  = 2;
  private static final int CONTAINS_RECIPIENT_ID_AND_ADDRESS = 3;

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    if (payee.hasRecipientId()) {
      if (payee.hasPublicAddress()) {
        dest.writeInt(CONTAINS_RECIPIENT_ID_AND_ADDRESS);
        dest.writeParcelable(payee.requireRecipientId(), flags);
        dest.writeString(payee.requirePublicAddress().getPaymentAddressBase58());
      } else {
        dest.writeInt(CONTAINS_RECIPIENT_ID);
        dest.writeParcelable(payee.requireRecipientId(), flags);
      }
    } else if (payee.hasPublicAddress()) {
      dest.writeInt(CONTAINS_ADDRESS);
      dest.writeString(payee.requirePublicAddress().getPaymentAddressBase58());
    } else {
      dest.writeInt(UNKNOWN);
    }
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<PayeeParcelable> CREATOR = new Creator<PayeeParcelable>() {
    @Override
    public @NonNull PayeeParcelable createFromParcel(@NonNull Parcel in) {

      switch (in.readInt()) {
        case UNKNOWN: {
          return new PayeeParcelable(Payee.UNKNOWN);
        }
        case CONTAINS_RECIPIENT_ID: {
          RecipientId recipientId = in.readParcelable(RecipientId.class.getClassLoader());
          return new PayeeParcelable(new Payee(recipientId));
        }
        case CONTAINS_RECIPIENT_ID_AND_ADDRESS: {
          RecipientId             recipientId   = in.readParcelable(RecipientId.class.getClassLoader());
          MobileCoinPublicAddress publicAddress = MobileCoinPublicAddress.fromBase58OrThrow(in.readString());
          return new PayeeParcelable(Payee.fromRecipientAndAddress(recipientId, publicAddress));
        }
        case CONTAINS_ADDRESS: {
          return new PayeeParcelable(new Payee(MobileCoinPublicAddress.fromBase58OrThrow(in.readString())));
        }
        default: {
          throw new AssertionError();
        }
      }
    }

    @Override
    public @NonNull PayeeParcelable[] newArray(int size) {
      return new PayeeParcelable[size];
    }
  };
}
