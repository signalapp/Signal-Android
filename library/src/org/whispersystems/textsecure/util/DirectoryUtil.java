package org.whispersystems.textsecure.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DirectoryUtil {

  public static String getDirectoryServerToken(String e164number) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA1");
      byte[]        token  = Util.trim(digest.digest(e164number.getBytes()), 10);
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
