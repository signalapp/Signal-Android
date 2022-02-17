/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Envelope;
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceEnvelopeProto;
import org.whispersystems.util.Base64;

import java.io.IOException;

/**
 * This class represents an encrypted Signal Service envelope.
 *
 * The envelope contains the wrapping information, such as the sender, the
 * message timestamp, the encrypted message type, etc.
 *
  * @author  Moxie Marlinspike
 */
public class SignalServiceEnvelope {

  private static final String TAG = SignalServiceEnvelope.class.getSimpleName();

  private final Envelope envelope;
  private final long     serverDeliveredTimestamp;

  /**
   * Construct an envelope from a serialized, Base64 encoded SignalServiceEnvelope, encrypted
   * with a signaling key.
   *
   * @param message The serialized SignalServiceEnvelope, base64 encoded and encrypted.
   */
  public SignalServiceEnvelope(String message, long serverDeliveredTimestamp) throws IOException {
    this(Base64.decode(message), serverDeliveredTimestamp);
  }

  /**
   * Construct an envelope from a serialized SignalServiceEnvelope, encrypted with a signaling key.
   *
   * @param input The serialized and (optionally) encrypted SignalServiceEnvelope.
   */
  public SignalServiceEnvelope(byte[] input, long serverDeliveredTimestamp) throws IOException {
    this.envelope                 = Envelope.parseFrom(input);
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
  }

  public SignalServiceEnvelope(int type,
                               Optional<SignalServiceAddress> sender,
                               int senderDevice,
                               long timestamp,
                               byte[] legacyMessage,
                               byte[] content,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               String uuid)
  {
    Envelope.Builder builder = Envelope.newBuilder()
                                       .setType(Envelope.Type.valueOf(type))
                                       .setSourceDevice(senderDevice)
                                       .setTimestamp(timestamp)
                                       .setServerTimestamp(serverReceivedTimestamp);

    if (sender.isPresent()) {
      builder.setSourceUuid(sender.get().getServiceId().toString());

      if (sender.get().getNumber().isPresent()) {
        builder.setSourceE164(sender.get().getNumber().get());
      }
    }

    if (uuid != null) {
      builder.setServerGuid(uuid);
    }

    if (legacyMessage != null) builder.setLegacyMessage(ByteString.copyFrom(legacyMessage));
    if (content != null)       builder.setContent(ByteString.copyFrom(content));

    this.envelope                 = builder.build();
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
  }

  public SignalServiceEnvelope(int type,
                               long timestamp,
                               byte[] legacyMessage,
                               byte[] content,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               String uuid)
  {
    Envelope.Builder builder = Envelope.newBuilder()
                                       .setType(Envelope.Type.valueOf(type))
                                       .setTimestamp(timestamp)
                                       .setServerTimestamp(serverReceivedTimestamp);

    if (uuid != null) {
      builder.setServerGuid(uuid);
    }

    if (legacyMessage != null) builder.setLegacyMessage(ByteString.copyFrom(legacyMessage));
    if (content != null)       builder.setContent(ByteString.copyFrom(content));

    this.envelope                 = builder.build();
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
  }

  public String getServerGuid() {
    return envelope.getServerGuid();
  }

  public boolean hasServerGuid() {
    return envelope.hasServerGuid();
  }

  /**
   * @return True if either a source E164 or UUID is present.
   */
  public boolean hasSourceUuid() {
    return envelope.hasSourceUuid();
  }

  /**
   * @return The envelope's sender as an E164 number.
   */
  public Optional<String> getSourceE164() {
    return Optional.fromNullable(envelope.getSourceE164());
  }

  /**
   * @return The envelope's sender as a UUID.
   */
  public Optional<String> getSourceUuid() {
    return Optional.fromNullable(envelope.getSourceUuid());
  }

  public String getSourceIdentifier() {
    return getSourceUuid().or(getSourceE164()).orNull();
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
    return new SignalServiceAddress(ACI.parseOrNull(envelope.getSourceUuid()), envelope.getSourceE164());
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

  /**
   * @return The server timestamp of when the server received the envelope.
   */
  public long getServerReceivedTimestamp() {
    return envelope.getServerTimestamp();
  }

  /**
   * @return The server timestamp of when the envelope was delivered to us.
   */
  public long getServerDeliveredTimestamp() {
    return serverDeliveredTimestamp;
  }

  /**
   * @return Whether the envelope contains a SignalServiceDataMessage
   */
  public boolean hasLegacyMessage() {
    return envelope.hasLegacyMessage();
  }

  /**
   * @return The envelope's containing SignalService message.
   */
  public byte[] getLegacyMessage() {
    return envelope.getLegacyMessage().toByteArray();
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

  /**
   * @return true if the containing message is a {@link org.whispersystems.libsignal.protocol.SignalMessage}
   */
  public boolean isSignalMessage() {
    return envelope.getType().getNumber() == Envelope.Type.CIPHERTEXT_VALUE;
  }

  /**
   * @return true if the containing message is a {@link org.whispersystems.libsignal.protocol.PreKeySignalMessage}
   */
  public boolean isPreKeySignalMessage() {
    return envelope.getType().getNumber() == Envelope.Type.PREKEY_BUNDLE_VALUE;
  }

  /**
   * @return true if the containing message is a delivery receipt.
   */
  public boolean isReceipt() {
    return envelope.getType().getNumber() == Envelope.Type.RECEIPT_VALUE;
  }

  public boolean isUnidentifiedSender() {
    return envelope.getType().getNumber() == Envelope.Type.UNIDENTIFIED_SENDER_VALUE;
  }

  public boolean isPlaintextContent() {
    return envelope.getType().getNumber() == Envelope.Type.PLAINTEXT_CONTENT_VALUE;
  }

  public byte[] serialize() {
    SignalServiceEnvelopeProto.Builder builder = SignalServiceEnvelopeProto.newBuilder()
                                                                           .setType(getType())
                                                                           .setDeviceId(getSourceDevice())
                                                                           .setTimestamp(getTimestamp())
                                                                           .setServerReceivedTimestamp(getServerReceivedTimestamp())
                                                                           .setServerDeliveredTimestamp(getServerDeliveredTimestamp());

    if (getSourceUuid().isPresent()) {
      builder.setSourceUuid(getSourceUuid().get());
    }

    if (getSourceE164().isPresent()) {
      builder.setSourceE164(getSourceE164().get());
    }

    if (hasLegacyMessage()) {
      builder.setLegacyMessage(ByteString.copyFrom(getLegacyMessage()));
    }

    if (hasContent()) {
      builder.setContent(ByteString.copyFrom(getContent()));
    }

    if (hasServerGuid()) {
      builder.setServerGuid(getServerGuid());
    }

    return builder.build().toByteArray();
  }

  public static SignalServiceEnvelope deserialize(byte[] serialized) {
    SignalServiceEnvelopeProto proto = null;
    try {
      proto = SignalServiceEnvelopeProto.parseFrom(serialized);
    } catch (InvalidProtocolBufferException e) {
      e.printStackTrace();
    }

    return new SignalServiceEnvelope(proto.getType(),
                                     SignalServiceAddress.fromRaw(proto.getSourceUuid(), proto.getSourceE164()),
                                     proto.getDeviceId(),
                                     proto.getTimestamp(),
                                     proto.hasLegacyMessage() ? proto.getLegacyMessage().toByteArray() : null,
                                     proto.hasContent() ? proto.getContent().toByteArray() : null,
                                     proto.getServerReceivedTimestamp(),
                                     proto.getServerDeliveredTimestamp(),
                                     proto.getServerGuid());
  }
}
