package org.whispersystems.signalservice.internal.contacts.crypto;

import org.signal.libsignal.protocol.util.ByteUtil;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.internal.contacts.entities.RemoteAttestationResponse;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.IOException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SignatureException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class RemoteAttestationCipher {

  private static final Set<String> ALLOWED_ADVISORIES = new HashSet<String>() {{
    add("INTEL-SA-00334");
    add("INTEL-SA-00615");
  }};

  private RemoteAttestationCipher() {
  }

  private static final Set<Long> SIGNATURE_BODY_VERSIONS = new HashSet<Long>() {{
    add(3L);
    add(4L);
  }};

  public static byte[] getRequestId(RemoteAttestationKeys keys, RemoteAttestationResponse response) throws InvalidCiphertextException {
    return AESCipher.decrypt(keys.getServerKey(), response.getIv(), response.getCiphertext(), response.getTag());
  }

  public static void verifyServerQuote(Quote quote, byte[] serverPublicStatic, String mrenclave)
      throws UnauthenticatedQuoteException
  {
    try {
      byte[] theirServerPublicStatic = new byte[serverPublicStatic.length];
      System.arraycopy(quote.getReportData(), 0, theirServerPublicStatic, 0, theirServerPublicStatic.length);

      if (!MessageDigest.isEqual(theirServerPublicStatic, serverPublicStatic)) {
        throw new UnauthenticatedQuoteException("Response quote has unauthenticated report data!");
      }

      if (!MessageDigest.isEqual(Hex.fromStringCondensed(mrenclave), quote.getMrenclave())) {
        throw new UnauthenticatedQuoteException("The response quote has the wrong mrenclave value in it: " + Hex.toStringCondensed(quote.getMrenclave()));
      }

      if (quote.isDebugQuote()) {
        throw new UnauthenticatedQuoteException("Received quote for debuggable enclave");
      }
    } catch (IOException e) {
      throw new UnauthenticatedQuoteException(e);
    }
  }

  public static void verifyIasSignature(KeyStore trustStore, String certificates, String signatureBody, String signature, Quote quote)
      throws SignatureException
  {
    if (certificates == null || certificates.isEmpty()) {
      throw new SignatureException("No certificates.");
    }

    try {
      SigningCertificate signingCertificate = new SigningCertificate(certificates, trustStore);
      signingCertificate.verifySignature(signatureBody, signature);

      SignatureBodyEntity signatureBodyEntity = JsonUtil.fromJson(signatureBody, SignatureBodyEntity.class);

      if (!SIGNATURE_BODY_VERSIONS.contains(signatureBodyEntity.getVersion())) {
        throw new SignatureException("Unexpected signed quote version " + signatureBodyEntity.getVersion());
      }

      if (!MessageDigest.isEqual(ByteUtil.trim(signatureBodyEntity.getIsvEnclaveQuoteBody(), 432), ByteUtil.trim(quote.getQuoteBytes(), 432))) {
        throw new SignatureException("Signed quote is not the same as RA quote: " + Hex.toStringCondensed(signatureBodyEntity.getIsvEnclaveQuoteBody()) + " vs " + Hex.toStringCondensed(quote.getQuoteBytes()));
      }

      if (!hasValidStatus(signatureBodyEntity)) {
        throw new SignatureException("Quote status is: " + signatureBodyEntity.getIsvEnclaveQuoteStatus() + " and advisories are: " + Arrays.toString(signatureBodyEntity.getAdvisoryIds()));
      }

      if (Instant.from(ZonedDateTime.of(LocalDateTime.from(DateTimeFormatter.ofPattern("yyy-MM-dd'T'HH:mm:ss.SSSSSS").parse(signatureBodyEntity.getTimestamp())), ZoneId.of("UTC")))
                 .plus(Period.ofDays(1))
                 .isBefore(Instant.now()))
      {
        throw new SignatureException("Signature is expired");
      }

    } catch (CertificateException | CertPathValidatorException | IOException e) {
      throw new SignatureException(e);
    }
  }

  private static boolean hasValidStatus(SignatureBodyEntity entity) {
    if ("OK".equals(entity.getIsvEnclaveQuoteStatus())) {
      return true;
    } else if ("SW_HARDENING_NEEDED".equals(entity.getIsvEnclaveQuoteStatus())) {
      return Arrays.stream(entity.getAdvisoryIds()).allMatch(ALLOWED_ADVISORIES::contains);
    } else {
      return false;
    }
  }
}
