package org.whispersystems.libaxolotl.ratchet;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;

public class SymmetricAxolotlParameters {

  private final ECKeyPair       ourBaseKey;
  private final ECKeyPair       ourRatchetKey;
  private final IdentityKeyPair ourIdentityKey;

  private final ECPublicKey     theirBaseKey;
  private final ECPublicKey     theirRatchetKey;
  private final IdentityKey     theirIdentityKey;

  SymmetricAxolotlParameters(ECKeyPair ourBaseKey, ECKeyPair ourRatchetKey,
                             IdentityKeyPair ourIdentityKey, ECPublicKey theirBaseKey,
                             ECPublicKey theirRatchetKey, IdentityKey theirIdentityKey)
  {
    this.ourBaseKey       = ourBaseKey;
    this.ourRatchetKey    = ourRatchetKey;
    this.ourIdentityKey   = ourIdentityKey;
    this.theirBaseKey     = theirBaseKey;
    this.theirRatchetKey  = theirRatchetKey;
    this.theirIdentityKey = theirIdentityKey;

    if (ourBaseKey == null || ourRatchetKey == null || ourIdentityKey == null ||
        theirBaseKey == null || theirRatchetKey == null || theirIdentityKey == null)
    {
      throw new IllegalArgumentException("Null values!");
    }
  }

  public ECKeyPair getOurBaseKey() {
    return ourBaseKey;
  }

  public ECKeyPair getOurRatchetKey() {
    return ourRatchetKey;
  }

  public IdentityKeyPair getOurIdentityKey() {
    return ourIdentityKey;
  }

  public ECPublicKey getTheirBaseKey() {
    return theirBaseKey;
  }

  public ECPublicKey getTheirRatchetKey() {
    return theirRatchetKey;
  }

  public IdentityKey getTheirIdentityKey() {
    return theirIdentityKey;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private ECKeyPair       ourBaseKey;
    private ECKeyPair       ourRatchetKey;
    private IdentityKeyPair ourIdentityKey;

    private ECPublicKey     theirBaseKey;
    private ECPublicKey     theirRatchetKey;
    private IdentityKey     theirIdentityKey;

    public Builder setOurBaseKey(ECKeyPair ourBaseKey) {
      this.ourBaseKey = ourBaseKey;
      return this;
    }

    public Builder setOurRatchetKey(ECKeyPair ourRatchetKey) {
      this.ourRatchetKey = ourRatchetKey;
      return this;
    }

    public Builder setOurIdentityKey(IdentityKeyPair ourIdentityKey) {
      this.ourIdentityKey = ourIdentityKey;
      return this;
    }

    public Builder setTheirBaseKey(ECPublicKey theirBaseKey) {
      this.theirBaseKey = theirBaseKey;
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

    public SymmetricAxolotlParameters create() {
      return new SymmetricAxolotlParameters(ourBaseKey, ourRatchetKey, ourIdentityKey,
                                            theirBaseKey, theirRatchetKey, theirIdentityKey);
    }
  }
}
