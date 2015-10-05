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

package org.thoughtcrime.redphone.crypto.zrtp;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * ZRTP hash commitment chain.
 *
 * @author Moxie Marlinspike
 *
 */
public class HashChain {

  private byte[] h0 = new byte[32];
  private byte[] h1;
  private byte[] h2;
  private byte[] h3;

  public HashChain() {
    try {
      SecureRandom.getInstance("SHA1PRNG").nextBytes(h0);

      MessageDigest md = MessageDigest.getInstance("SHA256");
      h1               = md.digest(h0);
      h2               = md.digest(h1);
      h3               = md.digest(h2);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public byte[] getH0() {
    return this.h0;
  }

  public byte[] getH1() {
    return this.h1;
  }

  public byte[] getH2() {
    return this.h2;
  }

  public byte[] getH3() {
    return this.h3;
  }

}
