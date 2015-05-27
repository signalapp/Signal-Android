package org.thoughtcrime.securesms.util;

public class MmsCharacterCalculator extends CharacterCalculator {

  private static final int MAX_SIZE = 5000;

  @Override
  public CharacterState calculateCharacters(int charactersSpent) {
    return new CharacterState(1, MAX_SIZE - charactersSpent, MAX_SIZE);
  }
}
