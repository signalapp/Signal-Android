/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.Preconditions;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Envelope;
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceEnvelopeProto;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.util.Optional;

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
                               byte[] content,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               String uuid,
                               String destinationServiceId,
                               boolean urgent,
                               boolean story,
                               byte[] reportingToken)
  {
    Envelope.Builder builder = Envelope.newBuilder()
                                       .setType(Envelope.Type.valueOf(type))
                                       .setSourceDevice(senderDevice)
                                       .setTimestamp(timestamp)
                                       .setServerTimestamp(serverReceivedTimestamp)
                                       .setDestinationServiceId(destinationServiceId)
                                       .setUrgent(urgent)
                                       .setStory(story);

    if (sender.isPresent()) {
      builder.setSourceServiceId(sender.get().getServiceId().toString());
    }

    if (uuid != null) {
      builder.setServerGuid(uuid);
    }

    if (content != null) {
      builder.setContent(ByteString.copyFrom(content));
    }

    if (reportingToken != null) {
      builder.setReportingToken(ByteString.copyFrom(reportingToken));
    }

    this.envelope                 = builder.build();
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
  }

  public SignalServiceEnvelope(int type,
                               long timestamp,
                               byte[] content,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               String uuid,
                               String destinationServiceId,
                               boolean urgent,
                               boolean story,
                               byte[] reportingToken)
  {
    Envelope.Builder builder = Envelope.newBuilder()
                                       .setType(Envelope.Type.valueOf(type))
                                       .setTimestamp(timestamp)
                                       .setServerTimestamp(serverReceivedTimestamp)
                                       .setDestinationServiceId(destinationServiceId)
                                       .setUrgent(urgent)
                                       .setStory(story);

    if (uuid != null) {
      builder.setServerGuid(uuid);
    }

    if (content != null) {
      builder.setContent(ByteString.copyFrom(content));
    }

    if (reportingToken != null) {
      builder.setReportingToken(ByteString.copyFrom(reportingToken));
    }

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
  public boolean hasSourceServiceId() {
    return envelope.hasSourceServiceId();
  }

  /**
   * @return The envelope's sender as a UUID.
   */
  public Optional<String> getSourceServiceId() {
    return Optional.ofNullable(envelope.getSourceServiceId());
  }

  public String getSourceIdentifier() {
    return getSourceServiceId().get().toString();
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
   * @return true if the containing message is a {@link org.signal.libsignal.protocol.message.SignalMessage}
   */
  public boolean isSignalMessage() {
    return envelope.getType().getNumber() == Envelope.Type.CIPHERTEXT_VALUE;
  }

  /**
   * @return true if the containing message is a {@link org.signal.libsignal.protocol.message.PreKeySignalMessage}
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

  public boolean hasDestinationUuid() {
    return envelope.hasDestinationServiceId() && UuidUtil.isUuid(envelope.getDestinationServiceId());
  }

  public String getDestinationServiceId() {
    return envelope.getDestinationServiceId();
  }

  public boolean isUrgent() {
    return envelope.getUrgent();
  }

  public boolean isStory() {
    return envelope.getStory();
  }

  public boolean hasReportingToken() {
    return envelope.hasReportingToken();
  }

  public byte[] getReportingToken() {
    return envelope.getReportingToken().toByteArray();
  }

  public Envelope getProto() {
    return envelope;
  }

  private SignalServiceEnvelopeProto.Builder serializeToProto() {
    SignalServiceEnvelopeProto.Builder builder = SignalServiceEnvelopeProto.newBuilder()
                                                                           .setType(getType())
                                                                           .setDeviceId(getSourceDevice())
                                                                           .setTimestamp(getTimestamp())
                                                                           .setServerReceivedTimestamp(getServerReceivedTimestamp())
                                                                           .setServerDeliveredTimestamp(getServerDeliveredTimestamp())
                                                                           .setUrgent(isUrgent())
                                                                           .setStory(isStory());

    if (getSourceServiceId().isPresent()) {
      builder.setSourceServiceId(getSourceServiceId().get());
    }

    if (hasContent()) {
      builder.setContent(ByteString.copyFrom(getContent()));
    }

    if (hasServerGuid()) {
      builder.setServerGuid(getServerGuid());
    }

    if (hasDestinationUuid()) {
      builder.setDestinationServiceId(getDestinationServiceId());
    }

    if (hasReportingToken()) {
      builder.setReportingToken(ByteString.copyFrom(getReportingToken()));
    }

    return builder;
  }

  public byte[] serialize() {
    return serializeToProto().build().toByteArray();
  }

  public static SignalServiceEnvelope deserialize(byte[] serialized) {
    SignalServiceEnvelopeProto proto = null;
    try {
      proto = SignalServiceEnvelopeProto.parseFrom(serialized);
    } catch (InvalidProtocolBufferException e) {
      e.printStackTrace();
    }

    Preconditions.checkNotNull(proto);

    ServiceId sourceServiceId = proto.hasSourceServiceId() ? ServiceId.parseOrNull(proto.getSourceServiceId()) : null;

    return new SignalServiceEnvelope(proto.getType(),
                                     sourceServiceId != null ? Optional.of(new SignalServiceAddress(sourceServiceId)) : Optional.empty(),
                                     proto.getDeviceId(),
                                     proto.getTimestamp(),
                                     proto.hasContent() ? proto.getContent().toByteArray() : null,
                                     proto.getServerReceivedTimestamp(),
                                     proto.getServerDeliveredTimestamp(),
                                     proto.getServerGuid(),
                                     proto.getDestinationServiceId(),
                                     proto.getUrgent(),
                                     proto.getStory(),
                                     proto.hasReportingToken() ? proto.getReportingToken().toByteArray() : null);
  }
}
