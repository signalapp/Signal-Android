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
package org.whispersystems.libaxolotl.ratchet;

import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.kdf.DerivedSecrets;
import org.whispersystems.libaxolotl.kdf.HKDF;
import org.whispersystems.libaxolotl.util.ByteUtil;
import org.whispersystems.libaxolotl.util.Pair;

public class RootKey {

  private final HKDF   kdf;
  private final byte[] key;

  public RootKey(HKDF kdf, byte[] key) {
    this.kdf = kdf;
    this.key = key;
  }

  public byte[] getKeyBytes() {
    return key;
  }

  public Pair<RootKey, ChainKey> createChain(ECPublicKey theirEphemeral, ECKeyPair ourEphemeral)
      throws InvalidKeyException
  {
    byte[]   sharedSecret = Curve.calculateAgreement(theirEphemeral, ourEphemeral.getPrivateKey());
    byte[]   keyBytes     = kdf.deriveSecrets(sharedSecret, key, "WhisperRatchet".getBytes(), 64);
    byte[][] keys         = ByteUtil.split(keyBytes, 32, 32);

    RootKey        newRootKey   = new RootKey(kdf, keys[0]);
    ChainKey       newChainKey  = new ChainKey(kdf, keys[1], 0);

    return new Pair<>(newRootKey, newChainKey);
  }
}
