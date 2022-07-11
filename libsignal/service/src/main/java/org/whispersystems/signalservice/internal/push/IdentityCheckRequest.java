package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.signal.libsignal.protocol.IdentityKey;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.util.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.annotation.Nonnull;

public class IdentityCheckRequest {
  @JsonProperty("elements")
  private final List<AciFingerprintPair> aciFingerprintPairs;

  public IdentityCheckRequest(@Nonnull List<AciFingerprintPair> aciKeyPairs) {
    this.aciFingerprintPairs = aciKeyPairs;
  }

  public List<AciFingerprintPair> getAciFingerprintPairs() {
    return aciFingerprintPairs;
  }

  public static final class AciFingerprintPair {

    @JsonProperty
    @JsonSerialize(using = JsonUtil.ServiceIdSerializer.class)
    private final ServiceId aci;

    @JsonProperty
    private final String fingerprint;

    public AciFingerprintPair(@Nonnull ServiceId aci, @Nonnull IdentityKey identityKey) {
      this.aci = aci;

      try {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        this.fingerprint = Base64.encodeBytes(messageDigest.digest(identityKey.serialize()), 0, 4);
      } catch (NoSuchAlgorithmException e) {
        throw new AssertionError(e);
      }
    }

    public ServiceId getAci() {
      return aci;
    }

    public String getFingerprint() {
      return fingerprint;
    }
  }
}
