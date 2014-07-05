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

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.kdf.DerivedSecrets;
import org.whispersystems.libaxolotl.kdf.HKDF;
import org.whispersystems.libaxolotl.state.SessionState;
import org.whispersystems.libaxolotl.util.Pair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class RatchetingSession {

  public static void initializeSession(SessionState    sessionState,
                                       int             sessionVersion,
                                       ECKeyPair       ourBaseKey,
                                       ECPublicKey     theirBaseKey,
                                       ECKeyPair       ourEphemeralKey,
                                       ECPublicKey     theirEphemeralKey,
                                       ECKeyPair       ourPreKey,
                                       ECPublicKey     theirPreKey,
                                       IdentityKeyPair ourIdentityKey,
                                       IdentityKey     theirIdentityKey)
      throws InvalidKeyException
  {
    if (isAlice(ourBaseKey.getPublicKey(), theirBaseKey, ourEphemeralKey.getPublicKey(), theirEphemeralKey)) {
      initializeSessionAsAlice(sessionState, sessionVersion, ourBaseKey, theirBaseKey, theirEphemeralKey,
                               ourPreKey, theirPreKey, ourIdentityKey, theirIdentityKey);
    } else {
      initializeSessionAsBob(sessionState, sessionVersion, ourBaseKey, theirBaseKey, ourEphemeralKey,
                             ourPreKey, theirPreKey, ourIdentityKey, theirIdentityKey);
    }

    sessionState.setSessionVersion(sessionVersion);
  }

  private static void initializeSessionAsAlice(SessionState sessionState,
                                               int sessionVersion,
                                               ECKeyPair ourBaseKey, ECPublicKey theirBaseKey,
                                               ECPublicKey theirEphemeralKey,
                                               ECKeyPair ourPreKey, ECPublicKey theirPreKey,
                                               IdentityKeyPair ourIdentityKey,
                                               IdentityKey theirIdentityKey)
      throws InvalidKeyException
  {
    sessionState.setRemoteIdentityKey(theirIdentityKey);
    sessionState.setLocalIdentityKey(ourIdentityKey.getPublicKey());

    ECKeyPair               sendingKey     = Curve.generateKeyPair(true);
    Pair<RootKey, ChainKey> receivingChain = calculate4DHE(true, sessionVersion,
                                                           ourBaseKey, theirBaseKey,
                                                           ourPreKey, theirPreKey,
                                                           ourIdentityKey, theirIdentityKey);
    Pair<RootKey, ChainKey> sendingChain   = receivingChain.first().createChain(theirEphemeralKey, sendingKey);

    sessionState.addReceiverChain(theirEphemeralKey, receivingChain.second());
    sessionState.setSenderChain(sendingKey, sendingChain.second());
    sessionState.setRootKey(sendingChain.first());
  }

  private static void initializeSessionAsBob(SessionState sessionState,
                                             int sessionVersion,
                                             ECKeyPair ourBaseKey, ECPublicKey theirBaseKey,
                                             ECKeyPair ourEphemeralKey,
                                             ECKeyPair ourPreKey, ECPublicKey theirPreKey,
                                             IdentityKeyPair ourIdentityKey,
                                             IdentityKey theirIdentityKey)
      throws InvalidKeyException
  {
    sessionState.setRemoteIdentityKey(theirIdentityKey);
    sessionState.setLocalIdentityKey(ourIdentityKey.getPublicKey());

    Pair<RootKey, ChainKey> sendingChain = calculate4DHE(false, sessionVersion,
                                                         ourBaseKey, theirBaseKey,
                                                         ourPreKey, theirPreKey,
                                                         ourIdentityKey, theirIdentityKey);

    sessionState.setSenderChain(ourEphemeralKey, sendingChain.second());
    sessionState.setRootKey(sendingChain.first());
  }

  private static Pair<RootKey, ChainKey> calculate4DHE(boolean isAlice, int sessionVersion,
                                                       ECKeyPair ourEphemeral, ECPublicKey theirEphemeral,
                                                       ECKeyPair ourPreKey, ECPublicKey theirPreKey,
                                                       IdentityKeyPair ourIdentity, IdentityKey theirIdentity)
      throws InvalidKeyException
  {
    try {
        byte[]                discontinuity = new byte[32];
        ByteArrayOutputStream secrets       = new ByteArrayOutputStream();

      if (sessionVersion >= 3) {
        Arrays.fill(discontinuity, (byte) 0xFF);
        secrets.write(discontinuity);
      }

      if (isAlice) {
        secrets.write(Curve.calculateAgreement(theirEphemeral, ourIdentity.getPrivateKey()));
        secrets.write(Curve.calculateAgreement(theirIdentity.getPublicKey(), ourEphemeral.getPrivateKey()));
      } else {
        secrets.write(Curve.calculateAgreement(theirIdentity.getPublicKey(), ourEphemeral.getPrivateKey()));
        secrets.write(Curve.calculateAgreement(theirEphemeral, ourIdentity.getPrivateKey()));
      }

      secrets.write(Curve.calculateAgreement(theirEphemeral, ourEphemeral.getPrivateKey()));

      if (sessionVersion >= 3 && ourPreKey != null && theirPreKey != null) {
        secrets.write(Curve.calculateAgreement(theirPreKey, ourPreKey.getPrivateKey()));
      }

      DerivedSecrets derivedSecrets = new HKDF().deriveSecrets(secrets.toByteArray(),
                                                               "WhisperText".getBytes());

      return new Pair<>(new RootKey(derivedSecrets.getCipherKey().getEncoded()),
                        new ChainKey(derivedSecrets.getMacKey().getEncoded(), 0));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static boolean isAlice(ECPublicKey ourBaseKey, ECPublicKey theirBaseKey,
                                 ECPublicKey ourEphemeralKey, ECPublicKey theirEphemeralKey)
  {
    if (ourEphemeralKey.equals(ourBaseKey)) {
      return false;
    }

    if (theirEphemeralKey.equals(theirBaseKey)) {
      return true;
    }

    return isLowEnd(ourBaseKey, theirBaseKey);
  }

  private static boolean isLowEnd(ECPublicKey ourKey, ECPublicKey theirKey) {
    return ourKey.compareTo(theirKey) < 0;
  }


}
