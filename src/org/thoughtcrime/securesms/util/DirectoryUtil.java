/**
 * Copyright (C) 2014 Open Whisper Systems
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


import org.whispersystems.textsecure.util.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DirectoryUtil {

  public static String getDirectoryServerToken(String e164number) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA1");
      byte[]        token  = org.whispersystems.textsecure.util.Util.trim(digest.digest(e164number.getBytes()), 10);
      return Base64.encodeBytesWithoutPadding(token);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Get a mapping of directory server tokens to their requested number.
   * @param e164numbers
   * @return map with token as key, E164 number as value
   */
  public static Map<String, String> getDirectoryServerTokenMap(Collection<String> e164numbers) {
    final Map<String,String> tokenMap = new HashMap<String,String>(e164numbers.size());
    for (String number : e164numbers) {
      tokenMap.put(getDirectoryServerToken(number), number);
    }
    return tokenMap;
  }
}
