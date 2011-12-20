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

import android.util.Log;

public class EncryptedCharacterCalculator extends CharacterCalculator {

  private CharacterState calculateSingleRecordCharacters(int charactersSpent) {
    int charactersRemaining = SmsTransportDetails.ENCRYPTED_SINGLE_MESSAGE_BODY_MAX_SIZE - charactersSpent;

    return new CharacterState(1, charactersRemaining, SmsTransportDetails.ENCRYPTED_SINGLE_MESSAGE_BODY_MAX_SIZE);
  }
	
  private CharacterState calculateMultiRecordCharacters(int charactersSpent) {
    int charactersInFirstRecord = SmsTransportDetails.ENCRYPTED_SINGLE_MESSAGE_BODY_MAX_SIZE;
    int spillover               = charactersSpent - charactersInFirstRecord;		
    Log.w("EncryptedCharacterCalculator", "Spillover: " + spillover);
    //		int maxMultiMessageSize     = SessionCipher.getMaxBodySizePerMultiMessage(charactersSpent);
    //		Log.w("EncryptedCharacterCalculator", "Maxmultimessagesize: " + maxMultiMessageSize);
    //		int spilloverMessagesSpent  = spillover / maxMultiMessageSize;
    int spilloverMessagesSpent  = spillover / SmsTransportDetails.MULTI_MESSAGE_MAX_BYTES;
    Log.w("EncryptedCharacterCalculator", "Spillover messaegs spent: " + spilloverMessagesSpent);

    //		if ((spillover % maxMultiMessageSize) > 0)
    if ((spillover % SmsTransportDetails.MULTI_MESSAGE_MAX_BYTES) > 0)
      spilloverMessagesSpent++;

    Log.w("EncryptedCharacterCalculator", "Spillover messaegs spent: " + spilloverMessagesSpent);

    //		int charactersRemaining = (maxMultiMessageSize * spilloverMessagesSpent) - spillover;
    int charactersRemaining = (SmsTransportDetails.MULTI_MESSAGE_MAX_BYTES * spilloverMessagesSpent) - spillover;
    Log.w("EncryptedCharacterCalculator", "charactersRemaining: " + charactersRemaining);
		
    //		return new CharacterState(spilloverMessagesSpent+1, charactersRemaining, maxMultiMessageSize);		
    return new CharacterState(spilloverMessagesSpent+1, charactersRemaining, SmsTransportDetails.MULTI_MESSAGE_MAX_BYTES);
  }
	
  @Override
  public CharacterState calculateCharacters(int charactersSpent) {
    if (charactersSpent <= SmsTransportDetails.ENCRYPTED_SINGLE_MESSAGE_BODY_MAX_SIZE){
      return calculateSingleRecordCharacters(charactersSpent);
    } else {
      return calculateMultiRecordCharacters(charactersSpent);
    }			
  }
}
