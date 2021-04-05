package org.thoughtcrime.securesms.service.webrtc;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.ringrtc.CallId;
import org.signal.ringrtc.CallManager;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;

import java.util.UUID;

import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_GROUP_CALL_ERA_ID;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_GROUP_CALL_UPDATE_GROUP;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_GROUP_CALL_UPDATE_SENDER;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_HANGUP_DEVICE_ID;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_HANGUP_IS_LEGACY;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_HANGUP_TYPE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_HTTP_REQUEST_ID;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_HTTP_RESPONSE_BODY;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_HTTP_RESPONSE_STATUS;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_MESSAGE_AGE_SECONDS;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_SERVER_DELIVERED_TIMESTAMP;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_SERVER_RECEIVED_TIMESTAMP;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getRecipientId;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getRemoteDevice;

/**
 * Collection of classes to ease parsing data from intents and passing said data
 * around.
 */
public class WebRtcData {

  /**
   * Low-level metadata Information about the call.
   */
  static class CallMetadata {
    private final @NonNull RemotePeer remotePeer;
    private final @NonNull CallId     callId;
    private final          int        remoteDevice;

    public static @NonNull CallMetadata fromIntent(@NonNull Intent intent) {
      return new CallMetadata(WebRtcIntentParser.getRemotePeer(intent), WebRtcIntentParser.getCallId(intent), WebRtcIntentParser.getRemoteDevice(intent));
    }

    private CallMetadata(@NonNull RemotePeer remotePeer, @NonNull CallId callId, int remoteDevice) {
      this.remotePeer   = remotePeer;
      this.callId       = callId;
      this.remoteDevice = remoteDevice;
    }

    @NonNull RemotePeer getRemotePeer() {
      return remotePeer;
    }

    @NonNull CallId getCallId() {
      return callId;
    }

    int getRemoteDevice() {
      return remoteDevice;
    }
  }

  /**
   * Metadata for a call offer to be sent or received.
   */
  static class OfferMetadata {
    private final @Nullable byte[]            opaque;
    private final @Nullable String            sdp;
    private final @NonNull  OfferMessage.Type offerType;

    static @NonNull OfferMetadata fromIntent(@NonNull Intent intent) {
      return new OfferMetadata(WebRtcIntentParser.getOfferOpaque(intent),
                               WebRtcIntentParser.getOfferSdp(intent),
                               WebRtcIntentParser.getOfferMessageType(intent));
    }

    private OfferMetadata(@Nullable byte[] opaque, @Nullable String sdp, @NonNull OfferMessage.Type offerType) {
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
  static class ReceivedOfferMetadata {
    private final @NonNull byte[]  remoteIdentityKey;
    private final          long    serverReceivedTimestamp;
    private final          long    serverDeliveredTimestamp;
    private final          boolean isMultiRing;

    static @NonNull ReceivedOfferMetadata fromIntent(@NonNull Intent intent) {
      return new ReceivedOfferMetadata(WebRtcIntentParser.getRemoteIdentityKey(intent),
                                       intent.getLongExtra(EXTRA_SERVER_RECEIVED_TIMESTAMP, -1),
                                       intent.getLongExtra(EXTRA_SERVER_DELIVERED_TIMESTAMP, -1),
                                       WebRtcIntentParser.getMultiRingFlag(intent));
    }

    ReceivedOfferMetadata(@NonNull byte[] remoteIdentityKey, long serverReceivedTimestamp, long serverDeliveredTimestamp, boolean isMultiRing) {
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
  static class AnswerMetadata {
    private final @Nullable byte[]            opaque;
    private final @Nullable String            sdp;

    static @NonNull AnswerMetadata fromIntent(@NonNull Intent intent) {
      return new AnswerMetadata(WebRtcIntentParser.getAnswerOpaque(intent), WebRtcIntentParser.getAnswerSdp(intent));
    }

    private AnswerMetadata(@Nullable byte[] opaque, @Nullable String sdp) {
      this.opaque    = opaque;
      this.sdp       = sdp;
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
  static class ReceivedAnswerMetadata {
    private final @NonNull byte[]  remoteIdentityKey;
    private final          boolean isMultiRing;

    static @NonNull ReceivedAnswerMetadata fromIntent(@NonNull Intent intent) {
      return new ReceivedAnswerMetadata(WebRtcIntentParser.getRemoteIdentityKey(intent), WebRtcIntentParser.getMultiRingFlag(intent));
    }

    ReceivedAnswerMetadata(@NonNull byte[] remoteIdentityKey, boolean isMultiRing) {
      this.remoteIdentityKey        = remoteIdentityKey;
      this.isMultiRing              = isMultiRing;
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
  static class HangupMetadata {
    private final @NonNull HangupMessage.Type type;
    private final          boolean            isLegacy;
    private final          int                deviceId;

    static @NonNull HangupMetadata fromIntent(@NonNull Intent intent) {
      return new HangupMetadata(HangupMessage.Type.fromCode(intent.getStringExtra(EXTRA_HANGUP_TYPE)),
                                intent.getBooleanExtra(EXTRA_HANGUP_IS_LEGACY, true),
                                intent.getIntExtra(EXTRA_HANGUP_DEVICE_ID, 0));
    }

    static @NonNull HangupMetadata fromType(@NonNull HangupMessage.Type type) {
      return new HangupMetadata(type, true, 0);
    }

    HangupMetadata(@NonNull HangupMessage.Type type, boolean isLegacy, int deviceId) {
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
   * Http response data.
   */
  static class HttpData {
    private final long   requestId;
    private final int    status;
    private final byte[] body;

    static @NonNull HttpData fromIntent(@NonNull Intent intent) {
      return new HttpData(intent.getLongExtra(EXTRA_HTTP_REQUEST_ID, -1),
                          intent.getIntExtra(EXTRA_HTTP_RESPONSE_STATUS, -1),
                          intent.getByteArrayExtra(EXTRA_HTTP_RESPONSE_BODY));
    }

    HttpData(long requestId, int status, @Nullable byte[] body) {
      this.requestId = requestId;
      this.status    = status;
      this.body      = body;
    }

    long getRequestId() {
      return requestId;
    }

    int getStatus() {
      return status;
    }

    @Nullable byte[] getBody() {
      return body;
    }
  }

  /**
   * An opaque calling message.
   */
  static class OpaqueMessageMetadata {
    private final UUID   uuid;
    private final byte[] opaque;
    private final int    remoteDeviceId;
    private final long   messageAgeSeconds;

    static @NonNull OpaqueMessageMetadata fromIntent(@NonNull Intent intent) {
      return new OpaqueMessageMetadata(WebRtcIntentParser.getUuid(intent),
                                       WebRtcIntentParser.getOpaque(intent),
                                       getRemoteDevice(intent),
                                       intent.getLongExtra(EXTRA_MESSAGE_AGE_SECONDS, 0));
    }

    OpaqueMessageMetadata(@NonNull UUID uuid, @NonNull byte[] opaque, int remoteDeviceId, long messageAgeSeconds) {
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
