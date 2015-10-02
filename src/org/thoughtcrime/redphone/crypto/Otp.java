/*
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

package org.thoughtcrime.redphone.crypto;

import org.thoughtcrime.redphone.util.Base64;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A utility class for calcuating a HOTP token from a user's
 * password and the current counter value.
 *
 * @author Moxie Marlinspike
 *
 */
public class Otp {
  public static String calculateOtp(String password, long counter) {
    try {
      SecretKeySpec key = new SecretKeySpec(password.getBytes(), "HmacSHA1");
      Mac mac           = Mac.getInstance("HmacSHA1");
      mac.init(key);

      return Base64.encodeBytes(mac.doFinal((counter+"").getBytes()));
    } catch (NoSuchAlgorithmException | InvalidKeyException nsae) {
      throw new AssertionError(nsae);
    }
  }
}
