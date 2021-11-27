package org.thoughtcrime.securesms.service.webrtc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.ringrtc.CallId;
import org.signal.ringrtc.CallManager;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;

import java.util.UUID;

/**
 * Collection of classes to ease passing calling data around.
 */
public class WebRtcData {

  /**
   * Low-level metadata Information about the call.
   */
  public static class CallMetadata {
    private final @NonNull RemotePeer remotePeer;
    private final          int        remoteDevice;

    public CallMetadata(@NonNull RemotePeer remotePeer, int remoteDevice) {
      this.remotePeer   = remotePeer;
      this.remoteDevice = remoteDevice;
    }

    @NonNull RemotePeer getRemotePeer() {
      return remotePeer;
    }

    @NonNull CallId getCallId() {
      return remotePeer.getCallId();
    }

    int getRemoteDevice() {
      return remoteDevice;
    }
  }

  /**
   * Metadata for a call offer to be sent or received.
   */
  public static class OfferMetadata {
    private final @Nullable byte[]            opaque;
    private final @Nullable String            sdp;
    private final @NonNull  OfferMessage.Type offerType;

    public OfferMetadata(@Nullable byte[] opaque, @Nullable String sdp, @NonNull OfferMessage.Type offerType) {
      this.opaque    = opaque;
      this.sdp       = sdp;
      this.offerType = offerType;
    }

    @Nullable byte[] getOpaque() {
      return opaque;
    }

    @Nullable String getSdp() {
      return sdp;
    }

    @NonNull OfferMessage.Type getOfferType() {
      return offerType;
    }
  }

  /**
   * Additional metadata for a received call.
   */
  public static class ReceivedOfferMetadata {
    private final @NonNull byte[]  remoteIdentityKey;
    private final          long    serverReceivedTimestamp;
    private final          long    serverDeliveredTimestamp;
    private final          boolean isMultiRing;

    public ReceivedOfferMetadata(@NonNull byte[] remoteIdentityKey, long serverReceivedTimestamp, long serverDeliveredTimestamp, boolean isMultiRing) {
      this.remoteIdentityKey        = remoteIdentityKey;
      this.serverReceivedTimestamp  = serverReceivedTimestamp;
      this.serverDeliveredTimestamp = serverDeliveredTimestamp;
      this.isMultiRing              = isMultiRing;
    }

    @NonNull byte[] getRemoteIdentityKey() {
      return remoteIdentityKey;
    }

    long getServerReceivedTimestamp() {
      return serverReceivedTimestamp;
    }

    long getServerDeliveredTimestamp() {
      return serverDeliveredTimestamp;
    }

    boolean isMultiRing() {
      return isMultiRing;
    }
  }

  /**
   * Metadata for an answer to be sent or received.
   */
  public static class AnswerMetadata {
    private final @Nullable byte[] opaque;
    private final @Nullable String sdp;

    public AnswerMetadata(@Nullable byte[] opaque, @Nullable String sdp) {
      this.opaque = opaque;
      this.sdp    = sdp;
    }

    @Nullable byte[] getOpaque() {
      return opaque;
    }

    @Nullable String getSdp() {
      return sdp;
    }
  }

  /**
   * Additional metadata for a received answer.
   */
  public static class ReceivedAnswerMetadata {
    private final @NonNull byte[]  remoteIdentityKey;
    private final          boolean isMultiRing;

    public ReceivedAnswerMetadata(@NonNull byte[] remoteIdentityKey, boolean isMultiRing) {
      this.remoteIdentityKey = remoteIdentityKey;
      this.isMultiRing       = isMultiRing;
    }

    @NonNull byte[] getRemoteIdentityKey() {
      return remoteIdentityKey;
    }

    boolean isMultiRing() {
      return isMultiRing;
    }
  }

  /**
   * Metadata for a remote or local hangup.
   */
  public static class HangupMetadata {
    private final @NonNull HangupMessage.Type type;
    private final          boolean            isLegacy;
    private final          int                deviceId;

    static @NonNull HangupMetadata fromType(@NonNull HangupMessage.Type type) {
      return new HangupMetadata(type, true, 0);
    }

    public HangupMetadata(@NonNull HangupMessage.Type type, boolean isLegacy, int deviceId) {
      this.type     = type;
      this.isLegacy = isLegacy;
      this.deviceId = deviceId;
    }

    @NonNull HangupMessage.Type getType() {
      return type;
    }

    @NonNull CallManager.HangupType getCallHangupType() {
      switch (type) {
        case ACCEPTED:        return CallManager.HangupType.ACCEPTED;
        case BUSY:            return CallManager.HangupType.BUSY;
        case NORMAL:          return CallManager.HangupType.NORMAL;
        case DECLINED:        return CallManager.HangupType.DECLINED;
        case NEED_PERMISSION: return CallManager.HangupType.NEED_PERMISSION;
        default:              throw new IllegalArgumentException("Unexpected hangup type: " + type);
      }
    }

    boolean isLegacy() {
      return isLegacy;
    }

    int getDeviceId() {
      return deviceId;
    }
  }

  /**
   * An opaque calling message.
   */
  public static class OpaqueMessageMetadata {
    private final UUID   uuid;
    private final byte[] opaque;
    private final int    remoteDeviceId;
    private final long   messageAgeSeconds;

    public OpaqueMessageMetadata(@NonNull UUID uuid, @NonNull byte[] opaque, int remoteDeviceId, long messageAgeSeconds) {
      this.uuid              = uuid;
      this.opaque            = opaque;
      this.remoteDeviceId    = remoteDeviceId;
      this.messageAgeSeconds = messageAgeSeconds;
    }

    @NonNull UUID getUuid() {
      return uuid;
    }

    @NonNull byte[] getOpaque() {
      return opaque;
    }

    int getRemoteDeviceId() {
      return remoteDeviceId;
    }

    long getMessageAgeSeconds() {
      return messageAgeSeconds;
    }
  }
}
