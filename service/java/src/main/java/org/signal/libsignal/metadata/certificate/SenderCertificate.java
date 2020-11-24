package org.signal.libsignal.metadata.certificate;


import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.libsignal.metadata.SignalProtos;


public class SenderCertificate {

  private final int               senderDeviceId;
  private final String            sender;

  private final byte[] serialized;
  private final byte[] certificate;

  public SenderCertificate(byte[] serialized) throws InvalidCertificateException {
    try {
        SignalProtos.SenderCertificate certificate = SignalProtos.SenderCertificate.parseFrom(serialized);

        if (!certificate.hasSenderDevice() || !certificate.hasSender()) {
            throw new InvalidCertificateException("Missing fields");
        }

      this.sender         = certificate.getSender();
      this.senderDeviceId = certificate.getSenderDevice();

      this.serialized  = serialized;
      this.certificate = certificate.toByteArray();
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidCertificateException(e);
    }
  }


  public int getSenderDeviceId() {
    return senderDeviceId;
  }

  public String getSender() {
    return sender;
  }

  public byte[] getSerialized() {
    return serialized;
  }

  public byte[] getCertificate() {
    return certificate;
  }
}
