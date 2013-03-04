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

import org.thoughtcrime.securesms.protocol.Prefix;
import org.thoughtcrime.securesms.util.InvalidMessageException;

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;

public class MessageDisplayHelper {

  private static final int MAX_CACHE_SIZE = 2000;

  private static final LinkedHashMap<String,SoftReference<String>> decryptedBodyCache = new LinkedHashMap<String,SoftReference<String>>() {
    @Override
    protected boolean removeEldestEntry(Entry<String,SoftReference<String>> eldest) {
      return this.size() > MAX_CACHE_SIZE;
    }
  };

  private static String checkCacheForBody(String body) {
    synchronized (decryptedBodyCache) {
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
  }

  public static String getDecryptedMessageBody(MasterCipher bodyCipher, String body) throws InvalidMessageException {
    if (body.startsWith(Prefix.SYMMETRIC_ENCRYPT)) {
      String cacheResult = checkCacheForBody(body);

      if (cacheResult != null)
        return cacheResult;

      String decryptedBody = bodyCipher.decryptBody(body.substring(Prefix.SYMMETRIC_ENCRYPT.length()));

      synchronized (decryptedBodyCache) {
        decryptedBodyCache.put(body, new SoftReference<String>(decryptedBody));
      }

      return decryptedBody;
    }

    return body;
  }
}