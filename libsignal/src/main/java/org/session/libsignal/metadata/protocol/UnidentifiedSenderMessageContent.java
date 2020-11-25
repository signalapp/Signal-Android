package org.session.libsignal.metadata.protocol;


import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.session.libsignal.metadata.InvalidMetadataMessageException;
import org.session.libsignal.metadata.SignalProtos;
import org.session.libsignal.metadata.certificate.InvalidCertificateException;
import org.session.libsignal.metadata.certificate.SenderCertificate;
import org.session.libsignal.libsignal.protocol.CiphertextMessage;

public class UnidentifiedSenderMessageContent {

  private final int               type;
  private final SenderCertificate senderCertificate;
  private final byte[]            content;
  private final byte[]            serialized;

  public UnidentifiedSenderMessageContent(byte[] serialized) throws InvalidMetadataMessageException, InvalidCertificateException {
    try {
      SignalProtos.UnidentifiedSenderMessage.Message message = SignalProtos.UnidentifiedSenderMessage.Message.parseFrom(serialized);

      if (!message.hasType() || !message.hasSenderCertificate() || !message.hasContent()) {
        throw new InvalidMetadataMessageException("Missing fields");
      }

      switch (message.getType()) {
        case MESSAGE:                 this.type = CiphertextMessage.WHISPER_TYPE;            break;
        case PREKEY_MESSAGE:          this.type = CiphertextMessage.PREKEY_TYPE;             break;
        case FALLBACK_MESSAGE:        this.type = CiphertextMessage.FALLBACK_MESSAGE_TYPE;   break;
        default:                      throw new InvalidMetadataMessageException("Unknown type: " + message.getType().getNumber());
      }

      this.senderCertificate = new SenderCertificate(message.getSenderCertificate().toByteArray());
      this.content           = message.getContent().toByteArray();
      this.serialized        = serialized;
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidMetadataMessageException(e);
    }
  }

  public UnidentifiedSenderMessageContent(int type, SenderCertificate senderCertificate, byte[] content) {
    try {
      this.serialized = SignalProtos.UnidentifiedSenderMessage.Message.newBuilder()
                                                                      .setType(SignalProtos.UnidentifiedSenderMessage.Message.Type.valueOf(getProtoType(type)))
                                                                      .setSenderCertificate(SignalProtos.SenderCertificate.parseFrom(senderCertificate.getSerialized()))
                                                                      .setContent(ByteString.copyFrom(content))
                                                                      .build()
                                                                      .toByteArray();

      this.type = type;
      this.senderCertificate = senderCertificate;
      this.content = content;
    } catch (InvalidProtocolBufferException e) {
      throw new AssertionError(e);
    }
  }

  public int getType() {
    return type;
  }

  public SenderCertificate getSenderCertificate() {
    return senderCertificate;
  }

  public byte[] getContent() {
    return content;
  }

  public byte[] getSerialized() {
    return serialized;
  }

  private int getProtoType(int type) {
    switch (type) {
      case CiphertextMessage.WHISPER_TYPE:            return SignalProtos.UnidentifiedSenderMessage.Message.Type.MESSAGE_VALUE;
      case CiphertextMessage.PREKEY_TYPE:             return SignalProtos.UnidentifiedSenderMessage.Message.Type.PREKEY_MESSAGE_VALUE;
      case CiphertextMessage.FALLBACK_MESSAGE_TYPE:   return SignalProtos.UnidentifiedSenderMessage.Message.Type.FALLBACK_MESSAGE_VALUE;
      default:                                        throw new AssertionError(type);
    }
  }

}
