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

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;

import org.thoughtcrime.securesms.database.MessageRecord;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.protocol.Prefix;
import org.thoughtcrime.securesms.util.InvalidMessageException;

public class MessageDisplayHelper {
	
  private static final int MAX_CACHE_SIZE = 2000;
	
  private static final LinkedHashMap<String,SoftReference<String>> decryptedBodyCache = new LinkedHashMap<String,SoftReference<String>>() {
    @Override
    protected boolean removeEldestEntry(Entry<String,SoftReference<String>> eldest) {
      return this.size() > MAX_CACHE_SIZE;
    }
  };

  private static boolean isUnreadableAsymmetricMessage(long type) {
    return type == SmsDatabase.Types.FAILED_DECRYPT_TYPE;
  }
	
  private static boolean isInProcessAsymmetricMessage(String body, long type) {
    return type == SmsDatabase.Types.DECRYPT_IN_PROGRESS_TYPE || (type == 0 && body.startsWith(Prefix.ASYMMETRIC_ENCRYPT)) || (type == 0 && body.startsWith(Prefix.ASYMMETRIC_LOCAL_ENCRYPT));
  }
	
  private static boolean isRogueAsymmetricMessage(long type) {
    return type == SmsDatabase.Types.NO_SESSION_TYPE;
  }
	
  private static boolean isKeyExchange(String body) {
    return body.startsWith(Prefix.KEY_EXCHANGE);
  }
	
  private static boolean isProcessedKeyExchange(String body) {
    return body.startsWith(Prefix.PROCESSED_KEY_EXCHANGE);
  }
	
  private static boolean isStaleKeyExchange(String body) {
    return body.startsWith(Prefix.STALE_KEY_EXCHANGE);
  }
	
  private static String checkCacheForBody(String body) {
    if (decryptedBodyCache.containsKey(body)) {
      String decryptedBody = decryptedBodyCache.get(body).get();
      if (decryptedBody != null) {
        return decryptedBody;
      } else {
        decryptedBodyCache.remove(body);
        return null;
      }	
    }
		
    return null;
  }
	
  public static void setDecryptedMessageBody(String body, MessageRecord message, MasterCipher bodyCipher) {
		
    try {
      if (body.startsWith(Prefix.SYMMETRIC_ENCRYPT)) {				
        String cacheResult = checkCacheForBody(body);
        if (cacheResult != null) {
          body = cacheResult;
        } else {
          String decryptedBody = bodyCipher.decryptBody(body.substring(Prefix.SYMMETRIC_ENCRYPT.length()));
          decryptedBodyCache.put(body, new SoftReference<String>(decryptedBody));
          body = decryptedBody;
        }
      }
			
      if (isUnreadableAsymmetricMessage(message.getType())) {
        message.setBody("Bad encrypted message...");
        message.setEmphasis(true);
      } else if (isInProcessAsymmetricMessage(body, message.getType())) {
        message.setBody("Decrypting, please wait...");
        message.setEmphasis(true);
      } else if (isRogueAsymmetricMessage(message.getType())) {
        message.setBody("Message encrypted for non-existent session...");
        message.setEmphasis(true);
      } else if (isKeyExchange(body)) {
        message.setKeyExchange(true);
        message.setEmphasis(true);
        message.setBody(body);
      } else if (isProcessedKeyExchange(body)) {
        message.setProcessedKeyExchange(true);
        message.setEmphasis(true);
        message.setBody(body);
      } else if (isStaleKeyExchange(body)) {
        message.setStaleKeyExchange(true);
        message.setEmphasis(true);
        message.setBody(body);
      } else {
        message.setBody(body);
        message.setEmphasis(false);
      }		
    } catch (InvalidMessageException ime) {
      message.setBody("Decryption error: local message corrupted, MAC doesn't match. Potential tampering?");
      message.setEmphasis(true);
    }
  }
	
}
