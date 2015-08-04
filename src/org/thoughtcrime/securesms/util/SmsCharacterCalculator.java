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

import org.thoughtcrime.securesms.sms.SmsTransportDetails;

public class SmsCharacterCalculator extends CharacterCalculator {

  @Override
  public CharacterState calculateCharacters(int charactersSpent) {
    int maxMessageSize;

    if (charactersSpent <= SmsTransportDetails.SMS_SIZE) {
      maxMessageSize = SmsTransportDetails.SMS_SIZE;
    } else {
      maxMessageSize = SmsTransportDetails.MULTIPART_SMS_SIZE;
    }

    int messagesSpent = charactersSpent / maxMessageSize;

    if (((charactersSpent % maxMessageSize) > 0) || (messagesSpent == 0))
      messagesSpent++;

    int charactersRemaining = (maxMessageSize * messagesSpent) - charactersSpent;

    return new CharacterState(messagesSpent, charactersRemaining, maxMessageSize);
  }
}

