package org.thoughtcrime.securesms;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import android.text.TextUtils;

import org.thoughtcrime.securesms.util.CharacterCalculator;
import org.thoughtcrime.securesms.util.CharacterCalculator.CharacterState;
import org.whispersystems.libsignal.util.guava.Optional;

import network.loki.messenger.R;

public class TransportOption implements Parcelable {

  public enum Type {
    SMS,
    TEXTSECURE
  }

  private final @NonNull String                 text;
  private final @NonNull Type                   type;
  private final @NonNull String                 composeHint;
  private final @NonNull CharacterCalculator    characterCalculator;
  private final @NonNull Optional<CharSequence> simName;
  private final @NonNull Optional<Integer>      simSubscriptionId;

  public TransportOption(@NonNull  Type type,
                         @NonNull String text,
                         @NonNull String composeHint,
                         @NonNull CharacterCalculator characterCalculator)
  {
    this(type, text, composeHint, characterCalculator,
         Optional.<CharSequence>absent(), Optional.<Integer>absent());
  }

  public TransportOption(@NonNull  Type type,
                         @NonNull String text,
                         @NonNull String composeHint,
                         @NonNull CharacterCalculator characterCalculator,
                         @NonNull Optional<CharSequence> simName,
                         @NonNull Optional<Integer> simSubscriptionId)
  {
    this.type                = type;
    this.text                = text;
    this.composeHint         = composeHint;
    this.characterCalculator = characterCalculator;
    this.simName             = simName;
    this.simSubscriptionId   = simSubscriptionId;
  }

  TransportOption(Parcel in) {
    this(Type.valueOf(in.readString()),
         in.readString(),
         in.readString(),
         CharacterCalculator.readFromParcel(in),
         Optional.fromNullable(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in)),
         in.readInt() == 1 ? Optional.of(in.readInt()) : Optional.absent());
  }

  public @NonNull Type getType() {
    return type;
  }

  public boolean isType(Type type) {
    return this.type == type;
  }

  public boolean isSms() {
    return type == Type.SMS;
  }

  public CharacterState calculateCharacters(String messageBody) {
    return characterCalculator.calculateCharacters(messageBody);
  }

  public @NonNull String getComposeHint() {
    return composeHint;
  }

  public @NonNull String getDescription() {
    return text;
  }

  @NonNull
  public Optional<CharSequence> getSimName() {
    return simName;
  }

  @NonNull
  public Optional<Integer> getSimSubscriptionId() {
    return simSubscriptionId;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(type.name());
    dest.writeString(text);
    dest.writeString(composeHint);
    CharacterCalculator.writeToParcel(dest, characterCalculator);
    TextUtils.writeToParcel(simName.orNull(), dest, flags);

    if (simSubscriptionId.isPresent()) {
      dest.writeInt(1);
      dest.writeInt(simSubscriptionId.get());
    } else {
      dest.writeInt(0);
    }
  }

  public static final Creator<TransportOption> CREATOR = new Creator<TransportOption>() {
    @Override
    public TransportOption createFromParcel(Parcel in) {
      return new TransportOption(in);
    }

    @Override
    public TransportOption[] newArray(int size) {
      return new TransportOption[size];
    }
  };
}
