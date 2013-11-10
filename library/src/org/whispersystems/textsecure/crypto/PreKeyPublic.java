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

package org.whispersystems.textsecure.crypto;

import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.util.Util;

public class PreKeyPublic {

  public  static final int KEY_SIZE = ECPublicKey.KEY_SIZE;

  private final ECPublicKey publicKey;

  public PreKeyPublic(ECPublicKey publicKey) {
    this.publicKey = publicKey;
  }

  public PreKeyPublic(byte[] serialized, int offset) throws InvalidKeyException {
    this.publicKey = Curve.decodePoint(serialized, offset);
  }

  public byte[] serialize() {
    return publicKey.serialize();
  }

  public ECPublicKey getPublicKey() {
    return publicKey;
  }

  public int getType() {
    return this.publicKey.getType();
  }
}
