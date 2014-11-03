package org.whispersystems.libaxolotl.ratchet;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.util.guava.Optional;

public class AliceAxolotlParameters {

  private final IdentityKeyPair       ourIdentityKey;
  private final ECKeyPair             ourBaseKey;

  private final IdentityKey           theirIdentityKey;
  private final ECPublicKey           theirSignedPreKey;
  private final Optional<ECPublicKey> theirOneTimePreKey;
  private final ECPublicKey           theirRatchetKey;

  private AliceAxolotlParameters(IdentityKeyPair ourIdentityKey, ECKeyPair ourBaseKey,
                                 IdentityKey theirIdentityKey, ECPublicKey theirSignedPreKey,
                                 ECPublicKey theirRatchetKey, Optional<ECPublicKey> theirOneTimePreKey)
  {
    this.ourIdentityKey     = ourIdentityKey;
    this.ourBaseKey         = ourBaseKey;
    this.theirIdentityKey   = theirIdentityKey;
    this.theirSignedPreKey  = theirSignedPreKey;
    this.theirRatchetKey    = theirRatchetKey;
    this.theirOneTimePreKey = theirOneTimePreKey;

    if (ourIdentityKey == null || ourBaseKey == null || theirIdentityKey == null ||
        theirSignedPreKey == null || theirRatchetKey == null || theirOneTimePreKey == null)
    {
      throw new IllegalArgumentException("Null values!");
    }
  }

  public IdentityKeyPair getOurIdentityKey() {
    return ourIdentityKey;
  }

  public ECKeyPair getOurBaseKey() {
    return ourBaseKey;
  }

  public IdentityKey getTheirIdentityKey() {
    return theirIdentityKey;
  }

  public ECPublicKey getTheirSignedPreKey() {
    return theirSignedPreKey;
  }

  public Optional<ECPublicKey> getTheirOneTimePreKey() {
    return theirOneTimePreKey;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public ECPublicKey getTheirRatchetKey() {
    return theirRatchetKey;
  }

  public static class Builder {
    private IdentityKeyPair       ourIdentityKey;
    private ECKeyPair             ourBaseKey;

    private IdentityKey           theirIdentityKey;
    private ECPublicKey           theirSignedPreKey;
    private ECPublicKey           theirRatchetKey;
    private Optional<ECPublicKey> theirOneTimePreKey;

    public Builder setOurIdentityKey(IdentityKeyPair ourIdentityKey) {
      this.ourIdentityKey = ourIdentityKey;
      return this;
    }

    public Builder setOurBaseKey(ECKeyPair ourBaseKey) {
      this.ourBaseKey = ourBaseKey;
      return this;
    }

    public Builder setTheirRatchetKey(ECPublicKey theirRatchetKey) {
      this.theirRatchetKey = theirRatchetKey;
      return this;
    }

    public Builder setTheirIdentityKey(IdentityKey theirIdentityKey) {
      this.theirIdentityKey = theirIdentityKey;
      return this;
    }

    public Builder setTheirSignedPreKey(ECPublicKey theirSignedPreKey) {
      this.theirSignedPreKey = theirSignedPreKey;
      return this;
    }

    public Builder setTheirOneTimePreKey(Optional<ECPublicKey> theirOneTimePreKey) {
      this.theirOneTimePreKey = theirOneTimePreKey;
      return this;
    }

    public AliceAxolotlParameters create() {
      return new AliceAxolotlParameters(ourIdentityKey, ourBaseKey, theirIdentityKey,
                                        theirSignedPreKey, theirRatchetKey, theirOneTimePreKey);
    }
  }
}
