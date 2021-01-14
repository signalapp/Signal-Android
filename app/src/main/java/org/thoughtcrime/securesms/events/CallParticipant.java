package org.thoughtcrime.securesms.events;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.whispersystems.libsignal.IdentityKey;

import java.util.Objects;

public final class CallParticipant {

  public static final CallParticipant EMPTY = createRemote(new CallParticipantId(Recipient.UNKNOWN), Recipient.UNKNOWN, null, new BroadcastVideoSink(null), false, false, 0, true, 0, DeviceOrdinal.PRIMARY);

  private final @NonNull  CallParticipantId  callParticipantId;
  private final @NonNull  CameraState        cameraState;
  private final @NonNull  Recipient          recipient;
  private final @Nullable IdentityKey        identityKey;
  private final @NonNull  BroadcastVideoSink videoSink;
  private final           boolean            videoEnabled;
  private final           boolean            microphoneEnabled;
  private final           long               lastSpoke;
  private final           boolean            mediaKeysReceived;
  private final           long               addedToCallTime;
  private final @NonNull  DeviceOrdinal      deviceOrdinal;

  public static @NonNull CallParticipant createLocal(@NonNull CameraState cameraState,
                                                     @NonNull BroadcastVideoSink renderer,
                                                     boolean microphoneEnabled)
  {
    return new CallParticipant(new CallParticipantId(Recipient.self()),
                               Recipient.self(),
                               null,
                               renderer,
                               cameraState,
                               cameraState.isEnabled() && cameraState.getCameraCount() > 0,
                               microphoneEnabled,
                               0,
                               true,
                               0,
                               DeviceOrdinal.PRIMARY);
  }

  public static @NonNull CallParticipant createRemote(@NonNull CallParticipantId callParticipantId,
                                                      @NonNull Recipient recipient,
                                                      @Nullable IdentityKey identityKey,
                                                      @NonNull BroadcastVideoSink renderer,
                                                      boolean audioEnabled,
                                                      boolean videoEnabled,
                                                      long lastSpoke,
                                                      boolean mediaKeysReceived,
                                                      long addedToCallTime,
                                                      @NonNull DeviceOrdinal deviceOrdinal)
  {
    return new CallParticipant(callParticipantId, recipient, identityKey, renderer, CameraState.UNKNOWN, videoEnabled, audioEnabled, lastSpoke, mediaKeysReceived, addedToCallTime, deviceOrdinal);
  }

  private CallParticipant(@NonNull CallParticipantId callParticipantId,
                          @NonNull Recipient recipient,
                          @Nullable IdentityKey identityKey,
                          @NonNull BroadcastVideoSink videoSink,
                          @NonNull CameraState cameraState,
                          boolean videoEnabled,
                          boolean microphoneEnabled,
                          long lastSpoke,
                          boolean mediaKeysReceived,
                          long addedToCallTime,
                          @NonNull DeviceOrdinal deviceOrdinal)
  {
    this.callParticipantId = callParticipantId;
    this.recipient         = recipient;
    this.identityKey       = identityKey;
    this.videoSink         = videoSink;
    this.cameraState       = cameraState;
    this.videoEnabled      = videoEnabled;
    this.microphoneEnabled = microphoneEnabled;
    this.lastSpoke         = lastSpoke;
    this.mediaKeysReceived = mediaKeysReceived;
    this.addedToCallTime   = addedToCallTime;
    this.deviceOrdinal     = deviceOrdinal;
  }

  public @NonNull CallParticipant withIdentityKey(@NonNull IdentityKey identityKey) {
    return new CallParticipant(callParticipantId, recipient, identityKey, videoSink, cameraState, videoEnabled, microphoneEnabled, lastSpoke, mediaKeysReceived, addedToCallTime, deviceOrdinal);
  }

  public @NonNull CallParticipant withVideoEnabled(boolean videoEnabled) {
    return new CallParticipant(callParticipantId, recipient, identityKey, videoSink, cameraState, videoEnabled, microphoneEnabled, lastSpoke, mediaKeysReceived, addedToCallTime, deviceOrdinal);
  }

  public @NonNull CallParticipantId getCallParticipantId() {
    return callParticipantId;
  }

  public @NonNull Recipient getRecipient() {
    return recipient;
  }

  public @NonNull String getRecipientDisplayName(@NonNull Context context) {
    if (recipient.isSelf() && isPrimary()) {
      return context.getString(R.string.CallParticipant__you);
    } else if (recipient.isSelf()) {
      return context.getString(R.string.CallParticipant__you_on_another_device);
    } else if (isPrimary()) {
      return recipient.getDisplayName(context);
    } else {
      return context.getString(R.string.CallParticipant__s_on_another_device, recipient.getDisplayName(context));
    }
  }

  public @NonNull String getShortRecipientDisplayName(@NonNull Context context) {
    if (recipient.isSelf() && isPrimary()) {
      return context.getString(R.string.CallParticipant__you);
    } else if (recipient.isSelf()) {
      return context.getString(R.string.CallParticipant__you_on_another_device);
    } else if (isPrimary()) {
      return recipient.getShortDisplayName(context);
    } else {
      return context.getString(R.string.CallParticipant__s_on_another_device, recipient.getShortDisplayName(context));
    }
  }

  public @Nullable IdentityKey getIdentityKey() {
    return identityKey;
  }

  public @NonNull BroadcastVideoSink getVideoSink() {
    return videoSink;
  }

  public @NonNull CameraState getCameraState() {
    return cameraState;
  }

  public boolean isVideoEnabled() {
    return videoEnabled;
  }

  public boolean isMicrophoneEnabled() {
    return microphoneEnabled;
  }

  public @NonNull CameraState.Direction getCameraDirection() {
    if (cameraState.getActiveDirection() == CameraState.Direction.BACK) {
      return cameraState.getActiveDirection();
    }
    return CameraState.Direction.FRONT;
  }

  public boolean isMoreThanOneCameraAvailable() {
    return cameraState.getCameraCount() > 1;
  }

  public long getLastSpoke() {
    return lastSpoke;
  }

  public boolean isMediaKeysReceived() {
    return mediaKeysReceived;
  }

  public long getAddedToCallTime() {
    return addedToCallTime;
  }

  public boolean isPrimary() {
    return deviceOrdinal == DeviceOrdinal.PRIMARY;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CallParticipant that = (CallParticipant) o;
    return callParticipantId.equals(that.callParticipantId) &&
           videoEnabled == that.videoEnabled &&
           microphoneEnabled == that.microphoneEnabled &&
           lastSpoke == that.lastSpoke &&
           mediaKeysReceived == that.mediaKeysReceived &&
           addedToCallTime == that.addedToCallTime &&
           cameraState.equals(that.cameraState) &&
           recipient.equals(that.recipient) &&
           Objects.equals(identityKey, that.identityKey) &&
           Objects.equals(videoSink, that.videoSink);
  }

  @Override
  public int hashCode() {
    return Objects.hash(callParticipantId, cameraState, recipient, identityKey, videoSink, videoEnabled, microphoneEnabled, lastSpoke, mediaKeysReceived, addedToCallTime);
  }

  @Override
  public @NonNull String toString() {
    return "CallParticipant{" +
           "cameraState=" + cameraState +
           ", recipient=" + recipient.getId() +
           ", identityKey=" + (identityKey == null ? "absent" : "present") +
           ", videoSink=" + (videoSink.getEglBase() == null ? "not initialized" : "initialized") +
           ", videoEnabled=" + videoEnabled +
           ", microphoneEnabled=" + microphoneEnabled +
           ", lastSpoke=" + lastSpoke +
           ", mediaKeysReceived=" + mediaKeysReceived +
           ", addedToCallTime=" + addedToCallTime +
           '}';
  }

  public enum DeviceOrdinal {
    PRIMARY,
    SECONDARY
  }
}
