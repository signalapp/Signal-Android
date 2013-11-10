/**
 * Copyright (C) 2013 Open Whisper Systems
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

package org.whispersystems.textsecure.crypto.ecc;

import org.whispersystems.textsecure.util.Util;

import java.math.BigInteger;
import java.util.Arrays;

public class DjbECPublicKey implements ECPublicKey {

  private final byte[] publicKey;

  DjbECPublicKey(byte[] publicKey) {
    this.publicKey = publicKey;
  }

  @Override
  public byte[] serialize() {
    byte[] type = {Curve.DJB_TYPE};
    return Util.combine(type, publicKey);
  }

  @Override
  public int getType() {
    return Curve.DJB_TYPE;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null)                      return false;
    if (!(other instanceof DjbECPublicKey)) return false;

    DjbECPublicKey that = (DjbECPublicKey)other;
    return Arrays.equals(this.publicKey, that.publicKey);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(publicKey);
  }

  @Override
  public int compareTo(ECPublicKey another) {
    return new BigInteger(publicKey).compareTo(new BigInteger(((DjbECPublicKey)another).publicKey));
  }

  public byte[] getPublicKey() {
    return publicKey;
  }
}
