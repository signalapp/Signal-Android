/**
 * Copyright (C) 2015 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.util;

public class PushCharacterCalculator extends CharacterCalculator {
  private static final int MAX_SIZE     = 2000;
  private static final int MAX_MESSAGES = 1;
  @Override
  public CharacterState calculateCharacters(int charactersSpent) {
    int mod      = charactersSpent % MAX_SIZE;
    int diff     = charactersSpent - mod;
    int messages = Math.min((diff / MAX_SIZE) + 1, MAX_MESSAGES);
    return new CharacterState(messages, MAX_SIZE - charactersSpent, MAX_SIZE, MAX_MESSAGES);
  }
}

