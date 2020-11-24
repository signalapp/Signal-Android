package org.whispersystems.signalservice.internal.contacts.crypto;


import org.threeten.bp.Instant;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.Period;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.whispersystems.libsignal.util.ByteUtil;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryRequest;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryResponse;
import org.whispersystems.signalservice.internal.contacts.entities.RemoteAttestationResponse;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class ContactDiscoveryCipher {

  private static final int  TAG_LENGTH_BYTES       = 16;
  private static final int  TAG_LENGTH_BITS        = TAG_LENGTH_BYTES * 8;
  private static final long SIGNATURE_BODY_VERSION = 3L;

  public DiscoveryRequest createDiscoveryRequest(List<String> addressBook, RemoteAttestation remoteAttestation) {
    try {
      ByteArrayOutputStream requestDataStream = new ByteArrayOutputStream();

      for (String address : addressBook) {
        requestDataStream.write(ByteUtil.longToByteArray(Long.parseLong(address)));
      }

      byte[]         requestData = requestDataStream.toByteArray();
      byte[]         nonce       = Util.getSecretBytes(12);
      Cipher         cipher      = Cipher.getInstance("AES/GCM/NoPadding");

      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(remoteAttestation.getKeys().getClientKey(), "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
      cipher.updateAAD(remoteAttestation.getRequestId());

      byte[]   cipherText = cipher.doFinal(requestData);
      byte[][] parts      = ByteUtil.split(cipherText, cipherText.length - TAG_LENGTH_BYTES, TAG_LENGTH_BYTES);

      return new DiscoveryRequest(addressBook.size(), remoteAttestation.getRequestId(), nonce, parts[0], parts[1]);
    } catch (IOException e) {
      throw new AssertionError(e);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    } catch (InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  public byte[] getDiscoveryResponseData(DiscoveryResponse response, RemoteAttestation remoteAttestation) throws InvalidCiphertextException {
    return decrypt(remoteAttestation.getKeys().getServerKey(), response.getIv(), response.getData(), response.getMac());
  }

  public byte[] getRequestId(RemoteAttestationKeys keys, RemoteAttestationResponse response) throws InvalidCiphertextException {
    return decrypt(keys.getServerKey(), response.getIv(), response.getCiphertext(), response.getTag());
  }

  public void verifyServerQuote(Quote quote, byte[] serverPublicStatic, String mrenclave)
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

  public void verifyIasSignature(KeyStore trustStore, String certificates, String signatureBody, String signature, Quote quote)
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

    } catch (CertificateException e) {
      throw new SignatureException(e);
    } catch (CertPathValidatorException e) {
      throw new SignatureException(e);
    } catch (IOException e) {
      throw new SignatureException(e);
    }
  }

  private byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext, byte[] tag) throws InvalidCiphertextException {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));

      return cipher.doFinal(ByteUtil.combine(ciphertext, tag));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    } catch(InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new InvalidCiphertextException(e);
    } catch (BadPaddingException e) {
      throw new InvalidCiphertextException(e);
    }
  }
}
