/**
 * Copyright (C) 2011 Whisper Systems
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

import android.telephony.SmsMessage;

public class SmsCharacterCalculator extends CharacterCalculator {

  @Override
  public CharacterState calculateCharacters(String messageBody) {

    int[] length            = SmsMessage.calculateLength(messageBody, false);
    int messagesSpent       = length[0];
    int charactersSpent     = length[1];
    int charactersRemaining = length[2];

    int maxMessageSize;

    if (messagesSpent > 0) {
      maxMessageSize = (charactersSpent + charactersRemaining) / messagesSpent;
    } else {
      maxMessageSize = (charactersSpent + charactersRemaining);
    }
    
    return new CharacterState(messagesSpent, charactersRemaining, maxMessageSize);
  }
}

