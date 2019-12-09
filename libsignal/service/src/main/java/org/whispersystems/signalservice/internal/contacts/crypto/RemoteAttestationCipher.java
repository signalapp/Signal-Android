package org.whispersystems.signalservice.internal.contacts.crypto;

import org.threeten.bp.Instant;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.Period;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.whispersystems.libsignal.util.ByteUtil;
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

public final class RemoteAttestationCipher {

  private RemoteAttestationCipher() {
  }

  private static final long SIGNATURE_BODY_VERSION = 3L;

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

      if (signatureBodyEntity.getVersion() != SIGNATURE_BODY_VERSION) {
        throw new SignatureException("Unexpected signed quote version " + signatureBodyEntity.getVersion());
      }

      if (!MessageDigest.isEqual(ByteUtil.trim(signatureBodyEntity.getIsvEnclaveQuoteBody(), 432), ByteUtil.trim(quote.getQuoteBytes(), 432))) {
        throw new SignatureException("Signed quote is not the same as RA quote: " + Hex.toStringCondensed(signatureBodyEntity.getIsvEnclaveQuoteBody()) + " vs " + Hex.toStringCondensed(quote.getQuoteBytes()));
      }

      if (!"OK".equals(signatureBodyEntity.getIsvEnclaveQuoteStatus())) {
        throw new SignatureException("Quote status is: " + signatureBodyEntity.getIsvEnclaveQuoteStatus());
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
}
