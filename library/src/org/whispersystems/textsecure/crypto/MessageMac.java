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
package org.whispersystems.textsecure.crypto;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.whispersystems.textsecure.util.Hex;

import android.util.Log;

public class MessageMac {

  public static final int MAC_LENGTH  = 10;
	
  public static byte[] calculateMac(byte[] message, int offset, int length, SecretKeySpec macKey) {
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

  public static void verifyMac(byte[] message, int offset, int length,
                               byte[] receivedMac, SecretKeySpec macKey)
      throws InvalidMacException
  {
    byte[] localMac = calculateMac(message, offset, length, macKey);

    Log.w("MessageMac", "Local Mac: " + Hex.toString(localMac));
    Log.w("MessageMac", "Remot Mac: " + Hex.toString(receivedMac));
		
    if (!Arrays.equals(localMac, receivedMac)) {
      throw new InvalidMacException("MAC on message does not match calculated MAC.");
    }
  }
	
}
