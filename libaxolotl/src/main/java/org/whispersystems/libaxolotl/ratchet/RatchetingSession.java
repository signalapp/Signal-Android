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
import org.whispersystems.libaxolotl.kdf.HKDF;
import org.whispersystems.libaxolotl.state.SessionState;
import org.whispersystems.libaxolotl.util.ByteUtil;
import org.whispersystems.libaxolotl.util.Pair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.ParseException;
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

    ECKeyPair sendingKey = Curve.generateKeyPair(true);
    DHEResult result     = calculate4DHE(true, sessionVersion, ourBaseKey, theirBaseKey,
                                         ourPreKey, theirPreKey, ourIdentityKey, theirIdentityKey);

    Pair<RootKey, ChainKey> sendingChain = result.getRootKey().createChain(theirEphemeralKey, sendingKey);

    sessionState.addReceiverChain(theirEphemeralKey, result.getChainKey());
    sessionState.setSenderChain(sendingKey, sendingChain.second());
    sessionState.setRootKey(sendingChain.first());

    if (sessionVersion >= 3) {
      VerifyKey verifyKey       = result.getVerifyKey();
      byte[]    verificationTag = verifyKey.generateVerification(ourBaseKey.getPublicKey(),
                                                                 ourPreKey.getPublicKey(),
                                                                 ourIdentityKey.getPublicKey().getPublicKey(),
                                                                 theirBaseKey, theirPreKey,
                                                                 theirIdentityKey.getPublicKey());

      sessionState.setVerification(verificationTag);
    }
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

    DHEResult result = calculate4DHE(false, sessionVersion, ourBaseKey, theirBaseKey,
                                     ourPreKey, theirPreKey, ourIdentityKey, theirIdentityKey);

    sessionState.setSenderChain(ourEphemeralKey, result.getChainKey());
    sessionState.setRootKey(result.getRootKey());

    if (sessionVersion >= 3) {
      VerifyKey verifyKey       = result.getVerifyKey();
      byte[]    verificationTag = verifyKey.generateVerification(theirBaseKey, theirPreKey,
                                                                 theirIdentityKey.getPublicKey(),
                                                                 ourBaseKey.getPublicKey(),
                                                                 ourPreKey.getPublicKey(),
                                                                 ourIdentityKey.getPublicKey().getPublicKey());

      sessionState.setVerification(verificationTag);
    }
  }

  private static DHEResult calculate4DHE(boolean isAlice, int sessionVersion,
                                         ECKeyPair ourBaseKey, ECPublicKey theirBaseKey,
                                         ECKeyPair ourPreKey, ECPublicKey theirPreKey,
                                         IdentityKeyPair ourIdentity, IdentityKey theirIdentity)
      throws InvalidKeyException
  {
    try {
      HKDF                  kdf           = HKDF.createFor(sessionVersion);
      byte[]                discontinuity = new byte[32];
      ByteArrayOutputStream secrets       = new ByteArrayOutputStream();

      if (sessionVersion >= 3) {
        Arrays.fill(discontinuity, (byte) 0xFF);
        secrets.write(discontinuity);
      }

      if (isAlice) {
        secrets.write(Curve.calculateAgreement(theirBaseKey, ourIdentity.getPrivateKey()));
        secrets.write(Curve.calculateAgreement(theirIdentity.getPublicKey(), ourBaseKey.getPrivateKey()));
      } else {
        secrets.write(Curve.calculateAgreement(theirIdentity.getPublicKey(), ourBaseKey.getPrivateKey()));
        secrets.write(Curve.calculateAgreement(theirBaseKey, ourIdentity.getPrivateKey()));
      }

      secrets.write(Curve.calculateAgreement(theirBaseKey, ourBaseKey.getPrivateKey()));

      if (sessionVersion >= 3) {
        secrets.write(Curve.calculateAgreement(theirPreKey, ourPreKey.getPrivateKey()));
      }

      byte[]   derivedSecretBytes = kdf.deriveSecrets(secrets.toByteArray(), "WhisperText".getBytes(), 96);
      byte[][] derivedSecrets     = ByteUtil.split(derivedSecretBytes, 32, 32, 32);

      return new DHEResult(new RootKey(kdf, derivedSecrets[0]),
                           new ChainKey(kdf, derivedSecrets[1], 0),
                           new VerifyKey(derivedSecrets[2]));

    } catch (IOException | ParseException e) {
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

  private static class DHEResult {
    private final RootKey   rootKey;
    private final ChainKey  chainKey;
    private final VerifyKey verifyKey;

    private DHEResult(RootKey rootKey, ChainKey chainKey, VerifyKey verifyKey) {
      this.rootKey   = rootKey;
      this.chainKey  = chainKey;
      this.verifyKey = verifyKey;
    }

    public RootKey getRootKey() {
      return rootKey;
    }

    public ChainKey getChainKey() {
      return chainKey;
    }

    public VerifyKey getVerifyKey() {
      return verifyKey;
    }
  }

}
