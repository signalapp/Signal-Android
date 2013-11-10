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

import org.spongycastle.crypto.params.ECPublicKeyParameters;

public class NistECPublicKey implements ECPublicKey {

  private final ECPublicKeyParameters publicKey;

  NistECPublicKey(ECPublicKeyParameters publicKey) {
    this.publicKey = publicKey;
  }

  @Override
  public byte[] serialize() {
    return CurveP256.encodePoint(publicKey.getQ());
  }

  @Override
  public int getType() {
    return Curve.NIST_TYPE;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null)                       return false;
    if (!(other instanceof NistECPublicKey)) return false;

    NistECPublicKey that = (NistECPublicKey)other;
    return publicKey.getQ().equals(that.publicKey.getQ());
  }

  @Override
  public int hashCode() {
    return publicKey.getQ().hashCode();
  }

  @Override
  public int compareTo(ECPublicKey another) {
    return publicKey.getQ().getX().toBigInteger()
                    .compareTo(((NistECPublicKey) another).publicKey.getQ().getX().toBigInteger());
  }

  public ECPublicKeyParameters getParameters() {
    return publicKey;
  }
}
