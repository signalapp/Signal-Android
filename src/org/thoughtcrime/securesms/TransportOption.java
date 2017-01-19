package org.thoughtcrime.securesms;

import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.util.CharacterCalculator;
import org.thoughtcrime.securesms.util.CharacterCalculator.CharacterState;
import org.whispersystems.libsignal.util.guava.Optional;

public class TransportOption {

  public enum Type {
    SMS,
    TEXTSECURE
  }

  private final int                             drawable;
  private final int                             backgroundColor;
  private final @NonNull String                 text;
  private final @NonNull Type                   type;
  private final @NonNull String                 composeHint;
  private final @NonNull CharacterCalculator    characterCalculator;
  private final @NonNull Optional<CharSequence> simName;
  private final @NonNull Optional<Integer>      simSubscriptionId;

  public TransportOption(@NonNull  Type type,
                         @DrawableRes int drawable,
                         int backgroundColor,
                         @NonNull String text,
                         @NonNull String composeHint,
                         @NonNull CharacterCalculator characterCalculator)
  {
    this(type, drawable, backgroundColor, text, composeHint, characterCalculator,
         Optional.<CharSequence>absent(), Optional.<Integer>absent());
  }

  public TransportOption(@NonNull  Type type,
                         @DrawableRes int drawable,
                         int backgroundColor,
                         @NonNull String text,
                         @NonNull String composeHint,
                         @NonNull CharacterCalculator characterCalculator,
                         @NonNull Optional<CharSequence> simName,
                         @NonNull Optional<Integer> simSubscriptionId)
  {
    this.type                = type;
    this.drawable            = drawable;
    this.backgroundColor     = backgroundColor;
    this.text                = text;
    this.composeHint         = composeHint;
    this.characterCalculator = characterCalculator;
    this.simName             = simName;
    this.simSubscriptionId   = simSubscriptionId;
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

  public @DrawableRes int getDrawable() {
    return drawable;
  }

  public int getBackgroundColor() {
    return backgroundColor;
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

}
