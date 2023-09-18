package org.thoughtcrime.securesms.payments;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.payments.preferences.model.PayeeParcelable;
import org.thoughtcrime.securesms.payments.proto.PaymentMetaData;
import org.whispersystems.signalservice.api.payments.Money;

import java.io.IOException;
import java.util.UUID;

/**
 * Wraps a Payment and enables it to be parcelized.
 */
public class PaymentParcelable implements Parcelable {

  private final Payment payment;

  public PaymentParcelable(@NonNull Payment payment) {
    this.payment = payment;
  }

  protected PaymentParcelable(Parcel in) {
    this.payment = new ParcelPayment(in);
  }

  public @NonNull Payment getPayment() {
    return payment;
  }

  public static final Creator<PaymentParcelable> CREATOR = new Creator<PaymentParcelable>() {
    @Override
    public PaymentParcelable createFromParcel(Parcel in) {
      return new PaymentParcelable(in);
    }

    @Override
    public PaymentParcelable[] newArray(int size) {
      return new PaymentParcelable[size];
    }
  };

  @Override public int describeContents() {
    return 0;
  }

  @Override public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(payment.getUuid().toString());
    dest.writeParcelable(new PayeeParcelable(payment.getPayee()), flags);
    dest.writeLong(payment.getBlockIndex());
    dest.writeLong(payment.getBlockTimestamp());
    dest.writeLong(payment.getTimestamp());
    dest.writeLong(payment.getDisplayTimestamp());
    dest.writeInt(payment.getDirection().serialize());
    dest.writeInt(payment.getState().serialize());

    if (payment.getFailureReason() == null) {
      dest.writeInt(-1);
    } else {
      dest.writeInt(payment.getFailureReason().serialize());
    }

    dest.writeString(payment.getNote());
    dest.writeString(payment.getAmount().serialize());
    dest.writeString(payment.getFee().serialize());
    dest.writeByteArray(payment.getPaymentMetaData().encode());
    dest.writeByte(payment.isSeen() ? (byte) 1 : 0);
    dest.writeString(payment.getAmountWithDirection().serialize());
    dest.writeString(payment.getAmountPlusFeeWithDirection().serialize());
    dest.writeByte(payment.isDefrag() ? (byte) 1 : 0);
  }

  private static final class ParcelPayment implements Payment {

    private final UUID            uuid;
    private final Payee           payee;
    private final long            blockIndex;
    private final long            blockTimestamp;
    private final long            timestamp;
    private final long            displayTimestamp;
    private final Direction       direction;
    private final State           state;
    private final FailureReason   failureReason;
    private final String          note;
    private final Money           amount;
    private final Money           fee;
    private final PaymentMetaData paymentMetaData;
    private final boolean         isSeen;
    private final Money           amountWithDirection;
    private final Money           amountPlusFeeWithDirection;
    private final boolean         isDefrag;

    private ParcelPayment(Parcel in) {
      try {
        uuid = UUID.fromString(in.readString());

        PayeeParcelable payeeParcelable = in.readParcelable(PayeeParcelable.class.getClassLoader());
        payee = payeeParcelable.getPayee();

        blockIndex                 = in.readLong();
        blockTimestamp             = in.readLong();
        timestamp                  = in.readLong();
        displayTimestamp           = in.readLong();
        direction                  = Direction.deserialize(in.readInt());
        state                      = State.deserialize(in.readInt());

        int failureReasonSerialized = in.readInt();
        if (failureReasonSerialized == -1) {
          failureReason = null;
        } else {
          failureReason = FailureReason.deserialize(failureReasonSerialized);
        }

        note                       = in.readString();
        amount                     = Money.parse(in.readString());
        fee                        = Money.parse(in.readString());
        paymentMetaData            = PaymentMetaData.ADAPTER.decode(in.createByteArray());
        isSeen                     = in.readByte() == 1;
        amountWithDirection        = Money.parse(in.readString());
        amountPlusFeeWithDirection = Money.parse(in.readString());
        isDefrag                   = in.readByte() == 1;
      } catch (Money.ParseException | IOException e) {
        throw new IllegalArgumentException();
      }
    }

    @Override
    public @NonNull UUID getUuid() {
      return uuid;
    }

    @Override
    public @NonNull Payee getPayee() {
      return payee;
    }

    @Override
    public long getBlockIndex() {
      return blockIndex;
    }

    @Override
    public long getBlockTimestamp() {
      return blockTimestamp;
    }

    @Override
    public long getTimestamp() {
      return timestamp;
    }

    @Override
    public long getDisplayTimestamp() {
      return displayTimestamp;
    }

    @Override
    public @NonNull Direction getDirection() {
      return direction;
    }

    @Override
    public @NonNull State getState() {
      return state;
    }

    @Override
    public @Nullable FailureReason getFailureReason() {
      return failureReason;
    }

    @Override
    public @NonNull String getNote() {
      return note;
    }

    @Override
    public @NonNull Money getAmount() {
      return amount;
    }

    @Override
    public @NonNull Money getFee() {
      return fee;
    }

    @Override
    public @NonNull PaymentMetaData getPaymentMetaData() {
      return paymentMetaData;
    }

    @Override
    public boolean isSeen() {
      return isSeen;
    }

    @Override
    public @NonNull Money getAmountWithDirection() {
      return amountWithDirection;
    }

    @NonNull @Override public Money getAmountPlusFeeWithDirection() {
      return amountPlusFeeWithDirection;
    }

    @Override public boolean isDefrag() {
      return isDefrag;
    }
  }
}
