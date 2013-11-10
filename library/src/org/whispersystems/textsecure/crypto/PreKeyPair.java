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

import org.whispersystems.textsecure.crypto.ecc.ECKeyPair;
import org.whispersystems.textsecure.crypto.ecc.ECPrivateKey;
import org.whispersystems.textsecure.util.Util;

public class PreKeyPair {

  private final MasterCipher masterCipher;
  private final PreKeyPublic publicKey;
  private final ECPrivateKey privateKey;

  public PreKeyPair(MasterSecret masterSecret, ECKeyPair keyPair) {
    this.masterCipher = new MasterCipher(masterSecret);
    this.publicKey    = new PreKeyPublic(keyPair.getPublicKey());
    this.privateKey   = keyPair.getPrivateKey();
  }

  public PreKeyPair(MasterSecret masterSecret, byte[] serialized) throws InvalidKeyException {
    byte[] privateKeyBytes = new byte[serialized.length - PreKeyPublic.KEY_SIZE];
    System.arraycopy(serialized, PreKeyPublic.KEY_SIZE, privateKeyBytes, 0, privateKeyBytes.length);

    this.masterCipher = new MasterCipher(masterSecret);
    this.publicKey    = new PreKeyPublic(serialized, 0);
    this.privateKey   = masterCipher.decryptKey(this.publicKey.getType(), privateKeyBytes);
  }

  public PreKeyPublic getPublicKey() {
    return publicKey;
  }

  public ECKeyPair getKeyPair() {
    return new ECKeyPair(publicKey.getPublicKey(), privateKey);
  }

  public byte[] serialize() {
    byte[] publicKeyBytes  = publicKey.serialize();
    byte[] privateKeyBytes = masterCipher.encryptKey(privateKey);

    return Util.combine(publicKeyBytes, privateKeyBytes);
  }
}
