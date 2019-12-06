package org.whispersystems.signalservice.internal.contacts.crypto;

import org.whispersystems.util.Base64;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class SigningCertificate {

  private final CertPath path;

  public SigningCertificate(String certificateChain, KeyStore trustStore)
      throws CertificateException, CertPathValidatorException
  {
    try {
      CertificateFactory          certificateFactory     = CertificateFactory.getInstance("X.509");
      Collection<X509Certificate> certificatesCollection = (Collection<X509Certificate>) certificateFactory.generateCertificates(new ByteArrayInputStream(certificateChain.getBytes()));
      List<X509Certificate>       certificates           = new LinkedList<>(certificatesCollection);
      PKIXParameters              pkixParameters         = new PKIXParameters(trustStore);
      CertPathValidator           validator              = CertPathValidator.getInstance("PKIX");

      if (certificates.isEmpty()) {
        throw new CertificateException("No certificates available! Badly-formatted cert chain?");
      }

      this.path = certificateFactory.generateCertPath(certificates);

      pkixParameters.setRevocationEnabled(false);
      validator.validate(path, pkixParameters);
      verifyDistinguishedName(path);
    } catch (KeyStoreException | InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public void verifySignature(String body, String encodedSignature)
      throws SignatureException
  {
    try {
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initVerify(path.getCertificates().get(0));
      signature.update(body.getBytes());
      if (!signature.verify(Base64.decode(encodedSignature.getBytes()))) {
        throw new SignatureException("Signature verification failed.");
      }
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  private void verifyDistinguishedName(CertPath path) throws CertificateException {
    X509Certificate leaf              = (X509Certificate) path.getCertificates().get(0);
    String          distinguishedName = leaf.getSubjectX500Principal().getName();

    if (!"CN=Intel SGX Attestation Report Signing,O=Intel Corporation,L=Santa Clara,ST=CA,C=US".equals(distinguishedName)) {
      throw new CertificateException("Bad DN: " + distinguishedName);
    }
  }

}
