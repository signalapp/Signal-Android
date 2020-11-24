package org.signal.libsignal.metadata.certificate;


import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.libsignal.metadata.SignalProtos;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;

public class ServerCertificate {

  private final int         keyId;
  private final ECPublicKey key;

  private final byte[] serialized;
  private final byte[] certificate;
  private final byte[] signature;

  public ServerCertificate(byte[] serialized) throws InvalidCertificateException {
    try {
      SignalProtos.ServerCertificate wrapper = SignalProtos.ServerCertificate.parseFrom(serialized);

      if (!wrapper.hasCertificate() || !wrapper.hasSignature()) {
        throw new InvalidCertificateException("Missing fields");
      }

      SignalProtos.ServerCertificate.Certificate certificate = SignalProtos.ServerCertificate.Certificate.parseFrom(wrapper.getCertificate());

      if (!certificate.hasId() || !certificate.hasKey()) {
        throw new InvalidCertificateException("Missing fields");
      }

      this.keyId       = certificate.getId();
      this.key         = Curve.decodePoint(certificate.getKey().toByteArray(), 0);
      this.serialized  = serialized;
      this.certificate = wrapper.getCertificate().toByteArray();
      this.signature   = wrapper.getSignature().toByteArray();

    } catch (InvalidProtocolBufferException e) {
      throw new InvalidCertificateException(e);
    } catch (InvalidKeyException e) {
      throw new InvalidCertificateException(e);
    }
  }

  public int getKeyId() {
    return keyId;
  }

  public ECPublicKey getKey() {
    return key;
  }

  public byte[] getSerialized() {
    return serialized;
  }

  public byte[] getCertificate() {
    return certificate;
  }

  public byte[] getSignature() {
    return signature;
  }
}
