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
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;

public class RatchetingSession {

  public static void initializeSession(SessionState sessionState,
                                       int sessionVersion,
                                       InitializationParameters parameters)
      throws InvalidKeyException
  {
    if (isAlice(parameters)) initializeSessionAsAlice(sessionState, sessionVersion, parameters);
    else                     initializeSessionAsBob(sessionState, sessionVersion, parameters);

    sessionState.setSessionVersion(sessionVersion);
  }

  private static void initializeSessionAsAlice(SessionState sessionState,
                                               int sessionVersion,
                                               InitializationParameters parameters)
      throws InvalidKeyException
  {
    sessionState.setRemoteIdentityKey(parameters.getTheirIdentityKey());
    sessionState.setLocalIdentityKey(parameters.getOurIdentityKey().getPublicKey());

    ECKeyPair sendingKey = Curve.generateKeyPair(true);
    DHEResult result     = calculate4DHE(true, sessionVersion, parameters);

    Pair<RootKey, ChainKey> sendingChain = result.getRootKey().createChain(parameters.getTheirEphemeralKey(), sendingKey);

    sessionState.addReceiverChain(parameters.getTheirEphemeralKey(), result.getChainKey());
    sessionState.setSenderChain(sendingKey, sendingChain.second());
    sessionState.setRootKey(sendingChain.first());

    if (sessionVersion >= 3) {
      sessionState.setVerification(calculateVerificationTag(true, result.getVerifyKey(), parameters));
    }
  }

  private static void initializeSessionAsBob(SessionState sessionState,
                                             int sessionVersion,
                                             InitializationParameters parameters)
      throws InvalidKeyException
  {
    sessionState.setRemoteIdentityKey(parameters.getTheirIdentityKey());
    sessionState.setLocalIdentityKey(parameters.getOurIdentityKey().getPublicKey());

    DHEResult result = calculate4DHE(false, sessionVersion, parameters);

    sessionState.setSenderChain(parameters.getOurEphemeralKey(), result.getChainKey());
    sessionState.setRootKey(result.getRootKey());

    if (sessionVersion >= 3) {
      sessionState.setVerification(calculateVerificationTag(false, result.getVerifyKey(), parameters));
    }
  }

  private static DHEResult calculate4DHE(boolean isAlice, int sessionVersion, InitializationParameters parameters)
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
        secrets.write(Curve.calculateAgreement(parameters.getTheirBaseKey(),
                                               parameters.getOurIdentityKey().getPrivateKey()));
        secrets.write(Curve.calculateAgreement(parameters.getTheirIdentityKey().getPublicKey(),
                                               parameters.getOurBaseKey().getPrivateKey()));
      } else {
        secrets.write(Curve.calculateAgreement(parameters.getTheirIdentityKey().getPublicKey(),
                                               parameters.getOurBaseKey().getPrivateKey()));
        secrets.write(Curve.calculateAgreement(parameters.getTheirBaseKey(),
                                               parameters.getOurIdentityKey().getPrivateKey()));
      }

      secrets.write(Curve.calculateAgreement(parameters.getTheirBaseKey(),
                                             parameters.getOurBaseKey().getPrivateKey()));

      if (sessionVersion >= 3 && parameters.getTheirPreKey().isPresent() && parameters.getOurPreKey().isPresent()) {
        secrets.write(Curve.calculateAgreement(parameters.getTheirPreKey().get(), parameters.getOurPreKey().get().getPrivateKey()));
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

  private static byte[] calculateVerificationTag(boolean isAlice, VerifyKey verifyKey,
                                                 InitializationParameters parameters)
  {
    if (isAlice) {
      return verifyKey.generateVerification(parameters.getOurBaseKey().getPublicKey(),
                                            getPublicKey(parameters.getOurPreKey()),
                                            parameters.getOurIdentityKey().getPublicKey().getPublicKey(),
                                            parameters.getTheirBaseKey(),
                                            parameters.getTheirPreKey(),
                                            parameters.getTheirIdentityKey().getPublicKey());
    } else {
      return verifyKey.generateVerification(parameters.getTheirBaseKey(),
                                            parameters.getTheirPreKey(),
                                            parameters.getTheirIdentityKey().getPublicKey(),
                                            parameters.getOurBaseKey().getPublicKey(),
                                            getPublicKey(parameters.getOurPreKey()),
                                            parameters.getOurIdentityKey().getPublicKey().getPublicKey());
    }
  }

  private static boolean isAlice(InitializationParameters parameters)
  {
    if (parameters.getOurEphemeralKey().equals(parameters.getOurBaseKey())) {
      return false;
    }

    if (parameters.getTheirEphemeralKey().equals(parameters.getTheirBaseKey())) {
      return true;
    }

    return isLowEnd(parameters.getOurBaseKey().getPublicKey(), parameters.getTheirBaseKey());
  }

  private static boolean isLowEnd(ECPublicKey ourKey, ECPublicKey theirKey) {
    return ourKey.compareTo(theirKey) < 0;
  }

  private static Optional<ECPublicKey> getPublicKey(Optional<ECKeyPair> keyPair) {
    if (keyPair.isPresent()) return Optional.of(keyPair.get().getPublicKey());
    else                     return Optional.absent();
  }

  public static class InitializationParameters {
    private final ECKeyPair             ourBaseKey;
    private final ECKeyPair             ourEphemeralKey;
    private final Optional<ECKeyPair>   ourPreKey;
    private final IdentityKeyPair       ourIdentityKey;

    private final ECPublicKey           theirBaseKey;
    private final ECPublicKey           theirEphemeralKey;
    private final Optional<ECPublicKey> theirPreKey;
    private final IdentityKey           theirIdentityKey;

    public InitializationParameters(ECKeyPair ourBaseKey, ECKeyPair ourEphemeralKey,
                                    Optional<ECKeyPair> ourPreKey, IdentityKeyPair ourIdentityKey,
                                    ECPublicKey theirBaseKey, ECPublicKey theirEphemeralKey,
                                    Optional<ECPublicKey> theirPreKey, IdentityKey theirIdentityKey)
    {
      this.ourBaseKey        = ourBaseKey;
      this.ourEphemeralKey   = ourEphemeralKey;
      this.ourPreKey         = ourPreKey;
      this.ourIdentityKey    = ourIdentityKey;
      this.theirBaseKey      = theirBaseKey;
      this.theirEphemeralKey = theirEphemeralKey;
      this.theirPreKey       = theirPreKey;
      this.theirIdentityKey  = theirIdentityKey;
    }

    public ECKeyPair getOurBaseKey() {
      return ourBaseKey;
    }

    public ECKeyPair getOurEphemeralKey() {
      return ourEphemeralKey;
    }

    public Optional<ECKeyPair> getOurPreKey() {
      return ourPreKey;
    }

    public IdentityKeyPair getOurIdentityKey() {
      return ourIdentityKey;
    }

    public ECPublicKey getTheirBaseKey() {
      return theirBaseKey;
    }

    public ECPublicKey getTheirEphemeralKey() {
      return theirEphemeralKey;
    }

    public Optional<ECPublicKey> getTheirPreKey() {
      return theirPreKey;
    }

    public IdentityKey getTheirIdentityKey() {
      return theirIdentityKey;
    }

    public static Builder newBuilder() {
      return new Builder();
    }

    public static class Builder {
      private ECKeyPair             ourBaseKey;
      private ECKeyPair             ourEphemeralKey;
      private Optional<ECKeyPair>   ourPreKey;
      private IdentityKeyPair       ourIdentityKey;
      private ECPublicKey           theirBaseKey;
      private ECPublicKey           theirEphemeralKey;
      private Optional<ECPublicKey> theirPreKey;
      private IdentityKey           theirIdentityKey;

      public Builder setOurBaseKey(ECKeyPair ourBaseKey) {
        this.ourBaseKey = ourBaseKey;
        return this;
      }

      public ECKeyPair getOurBaseKey() {
        return ourBaseKey;
      }

      public Builder setOurEphemeralKey(ECKeyPair ourEphemeralKey) {
        this.ourEphemeralKey = ourEphemeralKey;
        return this;
      }

      public ECKeyPair getOurEphemeralKey() {
        return ourEphemeralKey;
      }

      public Builder setOurPreKey(Optional<ECKeyPair> ourPreKey) {
        this.ourPreKey = ourPreKey;
        return this;
      }

      public Builder setOurIdentityKey(IdentityKeyPair ourIdentityKey) {
        this.ourIdentityKey = ourIdentityKey;
        return this;
      }

      public IdentityKeyPair getOurIdentityKey() {
        return ourIdentityKey;
      }

      public Builder setTheirBaseKey(ECPublicKey theirBaseKey) {
        this.theirBaseKey = theirBaseKey;
        return this;
      }

      public ECPublicKey getTheirBaseKey() {
        return theirBaseKey;
      }

      public Builder setTheirEphemeralKey(ECPublicKey theirEphemeralKey) {
        this.theirEphemeralKey = theirEphemeralKey;
        return this;
      }

      public Builder setTheirPreKey(Optional<ECPublicKey> theirPreKey) {
        this.theirPreKey = theirPreKey;
        return this;
      }

      public Builder setTheirIdentityKey(IdentityKey theirIdentityKey) {
        this.theirIdentityKey = theirIdentityKey;
        return this;
      }

      public RatchetingSession.InitializationParameters create() {
        if (ourBaseKey == null || ourEphemeralKey == null || ourPreKey == null || ourIdentityKey == null ||
            theirBaseKey == null || theirEphemeralKey == null || theirPreKey == null || theirIdentityKey == null)
        {
          throw new IllegalArgumentException("All parameters not specified!");
        }

        return new RatchetingSession.InitializationParameters(ourBaseKey, ourEphemeralKey,
                                                              ourPreKey, ourIdentityKey,
                                                              theirBaseKey, theirEphemeralKey,
                                                              theirPreKey, theirIdentityKey);
      }
    }
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
