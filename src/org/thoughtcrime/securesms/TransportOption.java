package org.thoughtcrime.securesms;

import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.util.CharacterCalculator;
import org.thoughtcrime.securesms.util.CharacterCalculator.CharacterState;

public class TransportOption {

  public enum Type {
    SMS,
    TEXTSECURE
  }

  private int                 drawable;
  private int                 backgroundColor;
  private String              text;
  private Type                type;
  private String              composeHint;
  private CharacterCalculator characterCalculator;
  private String[]            requiredPermissions;

  public TransportOption(Type type,
                         @DrawableRes int drawable,
                         int backgroundColor,
                         @NonNull String text,
                         @NonNull String composeHint,
                         @NonNull CharacterCalculator characterCalculator,
                         @NonNull String[] requiredPermissions)
  {
    this.type                = type;
    this.drawable            = drawable;
    this.backgroundColor     = backgroundColor;
    this.text                = text;
    this.composeHint         = composeHint;
    this.characterCalculator = characterCalculator;
    this.requiredPermissions = requiredPermissions;
  }

  public Type getType() {
    return type;
  }

  public boolean isType(Type type) {
    return this.type == type;
  }

  public boolean isSms() {
    return type == Type.SMS;
  }

  public CharacterState calculateCharacters(int charactersSpent) {
    return characterCalculator.calculateCharacters(charactersSpent);
  }

  public @DrawableRes int getDrawable() {
    return drawable;
  }

  public int getBackgroundColor() {
    return backgroundColor;
  }

  public String getComposeHint() {
    return composeHint;
  }

  public String getDescription() {
    return text;
  }

  public @NonNull String[] getRequiredPermissions() {
    return requiredPermissions;
  }
}
