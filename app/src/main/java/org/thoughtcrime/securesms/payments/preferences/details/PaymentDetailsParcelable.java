package org.thoughtcrime.securesms.payments.preferences.details;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.payments.Payment;
import org.thoughtcrime.securesms.payments.PaymentParcelable;

import java.util.Objects;
import java.util.UUID;

/**
 * Argument for PaymentDetailsFragment which takes EITHER a Payment OR a UUID, never both.
 */
public class PaymentDetailsParcelable implements Parcelable {

  private static final int TYPE_PAYMENT = 0;
  private static final int TYPE_UUID    = 1;

  private final Payment payment;
  private final UUID    uuid;

  private PaymentDetailsParcelable(@Nullable Payment payment, @Nullable UUID uuid) {
    if ((uuid == null) == (payment == null)) {
      throw new IllegalStateException("Must have exactly one of uuid or payment.");
    }
    this.payment = payment;
    this.uuid    = uuid;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    if (payment != null) {
      dest.writeInt(TYPE_PAYMENT);
      dest.writeParcelable(new PaymentParcelable(payment), flags);
    } else {
      dest.writeInt(TYPE_UUID);
      dest.writeString(uuid.toString());
    }
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<PaymentDetailsParcelable> CREATOR = new Creator<PaymentDetailsParcelable>() {
    @Override
    public PaymentDetailsParcelable createFromParcel(Parcel in) {
      int type = in.readInt();
      switch (type) {
        case TYPE_UUID   : return forUuid(UUID.fromString(in.readString()));
        case TYPE_PAYMENT: return forPayment(in.<PaymentParcelable>readParcelable(PaymentParcelable.class.getClassLoader()).getPayment());
        default          : throw new IllegalStateException("Unexpected parcel type " + type);
      }
    }

    @Override
    public PaymentDetailsParcelable[] newArray(int size) {
      return new PaymentDetailsParcelable[size];
    }
  };

  public boolean hasPayment() {
    return payment != null;
  }

  public @NonNull Payment requirePayment() {
    return Objects.requireNonNull(payment);
  }

  public @NonNull UUID requireUuid() {
    if (uuid != null) return uuid;
    else              return requirePayment().getUuid();
  }

  public static PaymentDetailsParcelable forUuid(@NonNull UUID uuid) {
    return new PaymentDetailsParcelable(null, uuid);
  }

  public static PaymentDetailsParcelable forPayment(@NonNull Payment payment) {
    return new PaymentDetailsParcelable(payment, null);
  }
}
