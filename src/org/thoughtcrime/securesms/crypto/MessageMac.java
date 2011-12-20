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
package org.thoughtcrime.securesms.crypto;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.thoughtcrime.securesms.util.Hex;

import android.util.Log;

public class MessageMac {

  public static final int MAC_LENGTH  = 10;
	
  private static byte[] calculateMac(byte[] message, int offset, int length, SecretKeySpec macKey) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(macKey);
		
      assert(mac.getMacLength() >= MAC_LENGTH);
		
      mac.update(message, offset, length);
      byte[] macBytes          = mac.doFinal();
      byte[] truncatedMacBytes = new byte[MAC_LENGTH];
      System.arraycopy(macBytes, 0, truncatedMacBytes, 0, truncatedMacBytes.length);
			
      return truncatedMacBytes;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException(e);
    }
  }
	
  public static byte[] buildMessageWithMac(byte[] message, SecretKeySpec macKey) {
    byte[] macBytes = calculateMac(message, 0, message.length, macKey);
    byte[] combined = new byte[macBytes.length + message.length];
    System.arraycopy(message, 0, combined, 0, message.length);
    System.arraycopy(macBytes, 0, combined, message.length, macBytes.length);
		
    return combined;
  }
	
  public static byte[] getMessageWithoutMac(byte[] message) throws InvalidMacException {
    if (message.length <= MAC_LENGTH)
      throw new InvalidMacException("Message shorter than MAC!");
		
    byte[] strippedMessage = new byte[message.length - MAC_LENGTH];
    System.arraycopy(message, 0, strippedMessage, 0, strippedMessage.length);
    return strippedMessage;
  }
	
  public static void verifyMac(byte[] message, SecretKeySpec macKey) throws InvalidMacException {
    byte[] localMacBytes = calculateMac(message, 0, message.length - MAC_LENGTH, macKey);
    byte[] receivedMacBytes = new byte[MAC_LENGTH];
		
    System.arraycopy(message, message.length-MAC_LENGTH, receivedMacBytes, 0, receivedMacBytes.length);
		
    Log.w("mm", "Local Mac: " + Hex.toString(localMacBytes));
    Log.w("mm", "Remot Mac: " + Hex.toString(receivedMacBytes));
		
    if (!Arrays.equals(localMacBytes, receivedMacBytes))
      throw new InvalidMacException("MAC on message does not match calculated MAC.");
  }
	
}
