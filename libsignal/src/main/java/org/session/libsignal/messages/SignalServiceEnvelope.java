/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.session.libsignal.messages;

import com.google.protobuf.ByteString;

import org.session.libsignal.utilities.SignalServiceAddress;
import org.session.libsignal.protos.SignalServiceProtos.Envelope;

/**
 * This class represents an encrypted Signal Service envelope.
 *
 * The envelope contains the wrapping information, such as the sender, the
 * message timestamp, the encrypted message type, etc.
 *
  * @author  Moxie Marlinspike
 */
public class SignalServiceEnvelope {

  private final Envelope envelope;

  public SignalServiceEnvelope(Envelope proto) {
    Envelope.Builder builder = Envelope.newBuilder();
    builder.setType(Envelope.Type.valueOf(proto.getType().getNumber()));
    if (proto.getSource() != null) {
      builder.setSource(proto.getSource());
    }
    if (proto.getSourceDevice() > 0) {
      builder.setSourceDevice(proto.getSourceDevice());
    }
    builder.setTimestamp(proto.getTimestamp());
    builder.setServerTimestamp(proto.getServerTimestamp());
    if (proto.getContent() != null) {
      builder.setContent(ByteString.copyFrom(proto.getContent().toByteArray()));
    }
    this.envelope = builder.build();
  }

  public SignalServiceEnvelope(int type, String sender, int senderDevice, long timestamp, byte[] content, long serverTimestamp) {
    Envelope.Builder builder = Envelope.newBuilder()
                                       .setType(Envelope.Type.valueOf(type))
                                       .setSource(sender)
                                       .setSourceDevice(senderDevice)
                                       .setTimestamp(timestamp)
                                       .setServerTimestamp(serverTimestamp);

    if (content != null)       builder.setContent(ByteString.copyFrom(content));

    this.envelope = builder.build();
  }

  public boolean hasSource() {
    return envelope.hasSource() && envelope.getSource().length() > 0;
  }

  /**
   * @return The envelope's sender.
   */
  public String getSource() {
    return envelope.getSource();
  }

  public boolean hasSourceDevice() {
    return envelope.hasSourceDevice();
  }

  /**
   * @return The envelope's sender device ID.
   */
  public int getSourceDevice() {
    return envelope.getSourceDevice();
  }

  /**
   * @return The envelope's sender as a SignalServiceAddress.
   */
  public SignalServiceAddress getSourceAddress() {
    return new SignalServiceAddress(envelope.getSource());
  }

  /**
   * @return The envelope content type.
   */
  public int getType() {
    return envelope.getType().getNumber();
  }

  /**
   * @return The timestamp this envelope was sent.
   */
  public long getTimestamp() {
    return envelope.getTimestamp();
  }

  public long getServerTimestamp() {
    return envelope.getServerTimestamp();
  }

  /**
   * @return Whether the envelope contains an encrypted SignalServiceContent
   */
  public boolean hasContent() {
    return envelope.hasContent();
  }

  /**
   * @return The envelope's encrypted SignalServiceContent.
   */
  public byte[] getContent() {
    return envelope.getContent().toByteArray();
  }

  public boolean isUnidentifiedSender() {
    return envelope.getType().getNumber() == Envelope.Type.SESSION_MESSAGE_VALUE;
  }

  public boolean isClosedGroupCiphertext() {
      return envelope.getType().getNumber() == Envelope.Type.CLOSED_GROUP_MESSAGE_VALUE;
  }
}
