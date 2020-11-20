package org.thoughtcrime.securesms.events;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.whispersystems.libsignal.IdentityKey;

import java.util.Objects;

public class CallParticipant {

  public static final CallParticipant EMPTY = createRemote(Recipient.UNKNOWN, null, new BroadcastVideoSink(null), false, false);

  private final @NonNull  CameraState        cameraState;
  private final @NonNull  Recipient          recipient;
  private final @Nullable IdentityKey        identityKey;
  private final @NonNull  BroadcastVideoSink videoSink;
  private final           boolean            videoEnabled;
  private final           boolean            microphoneEnabled;

  public static @NonNull CallParticipant createLocal(@NonNull CameraState cameraState,
                                                     @NonNull BroadcastVideoSink renderer,
                                                     boolean microphoneEnabled)
  {
    return new CallParticipant(Recipient.self(),
                               null,
                               renderer,
                               cameraState,
                               cameraState.isEnabled() && cameraState.getCameraCount() > 0,
                               microphoneEnabled);
  }

  public static @NonNull CallParticipant createRemote(@NonNull Recipient recipient,
                                                      @Nullable IdentityKey identityKey,
                                                      @NonNull BroadcastVideoSink renderer,
                                                      boolean audioEnabled,
                                                      boolean videoEnabled)
  {
    return new CallParticipant(recipient, identityKey, renderer, CameraState.UNKNOWN, videoEnabled, audioEnabled);
  }

  private CallParticipant(@NonNull Recipient recipient,
                          @Nullable IdentityKey identityKey,
                          @NonNull BroadcastVideoSink videoSink,
                          @NonNull CameraState cameraState,
                          boolean videoEnabled,
                          boolean microphoneEnabled)
  {
    this.recipient         = recipient;
    this.identityKey       = identityKey;
    this.videoSink         = videoSink;
    this.cameraState       = cameraState;
    this.videoEnabled      = videoEnabled;
    this.microphoneEnabled = microphoneEnabled;
  }

  public @NonNull CallParticipant withIdentityKey(@NonNull IdentityKey identityKey) {
    return new CallParticipant(recipient, identityKey, videoSink, cameraState, videoEnabled, microphoneEnabled);
  }

  public @NonNull CallParticipant withVideoEnabled(boolean videoEnabled) {
    return new CallParticipant(recipient, identityKey, videoSink, cameraState, videoEnabled, microphoneEnabled);
  }

  public @NonNull Recipient getRecipient() {
    return recipient;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CallParticipant that = (CallParticipant) o;
    return videoEnabled == that.videoEnabled &&
        microphoneEnabled == that.microphoneEnabled &&
        cameraState.equals(that.cameraState) &&
        recipient.equals(that.recipient) &&
        Objects.equals(identityKey, that.identityKey) &&
        Objects.equals(videoSink, that.videoSink);
  }

  @Override
  public int hashCode() {
    return Objects.hash(cameraState, recipient, identityKey, videoSink, videoEnabled, microphoneEnabled);
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
           '}';
  }
}
