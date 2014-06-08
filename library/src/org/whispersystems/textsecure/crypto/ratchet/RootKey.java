/**
 * Copyright (C) 2013-2014 Open WhisperSystems
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
package org.whispersystems.textsecure.crypto.ratchet;

import android.util.Pair;

import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECKeyPair;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.crypto.kdf.DerivedSecrets;
import org.whispersystems.textsecure.crypto.kdf.HKDF;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class RootKey {

  private final byte[] key;

  public RootKey(byte[] key) {
    this.key = key;
  }

  public byte[] getKeyBytes() {
    return key;
  }

  public Pair<RootKey, ChainKey> createChain(ECPublicKey theirEphemeral, ECKeyPair ourEphemeral)
      throws InvalidKeyException
  {
    HKDF           kdf          = new HKDF();
    byte[]         sharedSecret = Curve.calculateAgreement(theirEphemeral, ourEphemeral.getPrivateKey());
    DerivedSecrets keys         = kdf.deriveSecrets(sharedSecret, key, "WhisperRatchet".getBytes());
    RootKey        newRootKey   = new RootKey(keys.getCipherKey().getEncoded());
    ChainKey       newChainKey  = new ChainKey(keys.getMacKey().getEncoded(), 0);

    return new Pair<RootKey, ChainKey>(newRootKey, newChainKey);
  }
}
