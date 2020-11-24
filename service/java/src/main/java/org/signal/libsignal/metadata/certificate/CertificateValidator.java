package org.signal.libsignal.metadata.certificate;


import java.util.HashSet;
import java.util.Set;

public class CertificateValidator {

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private static final Set<Integer> REVOKED = new HashSet<Integer>() {{

  }};

  public void validate(SenderCertificate certificate, long validationTime) throws InvalidCertificateException {
      if (certificate.getSender() == null || certificate.getSenderDeviceId() <= 0) {
          throw new InvalidCertificateException("Sender or sender device id is invalid");
      }
  }

  // VisibleForTesting
  void validate(ServerCertificate certificate) throws InvalidCertificateException {
  }
}

