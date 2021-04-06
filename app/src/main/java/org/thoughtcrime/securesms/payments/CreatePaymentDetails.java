package org.thoughtcrime.securesms.payments;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.payments.preferences.model.PayeeParcelable;
import org.whispersystems.signalservice.api.payments.Money;

public class CreatePaymentDetails implements Parcelable {
  private final PayeeParcelable payee;
  private final Money           amount;
  private final String          note;

  public CreatePaymentDetails(@NonNull PayeeParcelable payee,
                              @NonNull Money amount,
                              @Nullable String note)
  {
    this.payee  = payee;
    this.amount = amount;
    this.note   = note;
  }

  protected CreatePaymentDetails(@NonNull Parcel in) {
    this.payee  = in.readParcelable(PayeeParcelable.class.getClassLoader());
    this.amount = Money.parseOrThrow(in.readString());
    this.note   = in.readString();
  }

  public @NonNull Payee getPayee() {
    return payee.getPayee();
  }

  public @NonNull Money getAmount() {
    return amount;
  }

  public @Nullable String getNote() {
    return note;
  }

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    dest.writeParcelable(payee, flags);
    dest.writeString(amount.serialize());
    dest.writeString(note);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<CreatePaymentDetails> CREATOR = new Creator<CreatePaymentDetails>() {
    @Override
    public @NonNull CreatePaymentDetails createFromParcel(@NonNull Parcel in) {
      return new CreatePaymentDetails(in);
    }

    @Override
    public @NonNull CreatePaymentDetails[] newArray(int size) {
      return new CreatePaymentDetails[size];
    }
  };
}
