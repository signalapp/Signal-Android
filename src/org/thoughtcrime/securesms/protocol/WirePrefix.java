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
package org.thoughtcrime.securesms.protocol;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.thoughtcrime.securesms.util.Base64;

import android.util.Log;

/**
 * Calculates prefixes that identify a message as
 * being part of an encrypted session.  The idea was to
 * make calculating and identifying these prefixes somewhat
 * expensive, so that filtering them en-mass would come at a cost.
 * 
 * @author Moxie Marlinspike
 */

public abstract class WirePrefix {
	
  private static final int HASH_ITERATIONS = 1000;
  private static final int PREFIX_BYTES    = 3;
  public  static final int PREFIX_SIZE     = 4;
	
  public abstract String calculatePrefix(String message);
	
  public static boolean isKeyExchange(String message) {
    return verifyPrefix("?TSK", message);
  }
	
  public static boolean isEncryptedMessage(String message) {
    return verifyPrefix("?TSM", message);
  }
	
  public static String calculateKeyExchangePrefix(String message) {
    return calculatePrefix(("?TSK" + message).getBytes(), PREFIX_BYTES);
  }
	
  public static String calculateEncryptedMesagePrefix(String message) {
    return calculatePrefix(("?TSM" + message).getBytes(), PREFIX_BYTES);
  }

  private static boolean verifyPrefix(String prefixType, String message) {
    if (message.length() <= PREFIX_SIZE)
      return false;
		
    String prefix           = message.substring(0, PREFIX_SIZE);
    message                 = message.substring(PREFIX_SIZE);	
		
    Log.w("Prefix", "Calculating on message: " + message + "**");
		
    String calculatedPrefix = calculatePrefix((prefixType + message).getBytes(), PREFIX_BYTES);
		
    assert(calculatedPrefix.length() == PREFIX_SIZE);
		
    Log.w("Prefix", "Received prefix: " + prefix);
    Log.w("Prefix", "Calculated prefix: " + calculatedPrefix);
		
    return prefix.equals(calculatedPrefix);				
  }
	
  private static String calculatePrefix(byte[] message, int byteCount) {
    try {
      MessageDigest md     = MessageDigest.getInstance("SHA1");
      byte[] runningDigest = message;
			
      for (int i=0;i<HASH_ITERATIONS;i++) {
        runningDigest = md.digest(runningDigest);
      }

      return Base64.encodeBytes(runningDigest, 0, byteCount);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }		
  }
	
  public static String calculateEncryptedMmsSubject() {
    try {
      byte[] postfix = new byte[6];
      SecureRandom.getInstance("SHA1PRNG").nextBytes(postfix);
			
      String postfixString = Base64.encodeBytes(postfix);
      String prefix        = calculatePrefix(postfixString.getBytes(), 6);
			
      return prefix + postfixString;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }
	
  public static boolean isEncryptedMmsSubject(String subject) {
    if (subject.length() < 9)
      return false;
		
    String prefix  = subject.substring(0, 8);
    String postfix = subject.substring(8);
		
    String calculatedPrefix = calculatePrefix(postfix.getBytes(), 6);
    return calculatedPrefix.equals(prefix);
  }
}
