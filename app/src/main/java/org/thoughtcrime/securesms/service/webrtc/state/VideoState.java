package org.thoughtcrime.securesms.service.webrtc.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.components.webrtc.EglBaseWrapper;
import org.thoughtcrime.securesms.ringrtc.OutgoingVideoSourceRouter;

import java.util.Objects;

/**
 * Local device video state and infrastructure.
 */
public final class VideoState {
  EglBaseWrapper            eglBase;
  BroadcastVideoSink        localSink;
  OutgoingVideoSourceRouter router;

  VideoState() {
    this(null, null, null);
  }

  VideoState(@NonNull VideoState toCopy) {
    this(toCopy.eglBase, toCopy.localSink, toCopy.router);
  }

  VideoState(@Nullable EglBaseWrapper eglBase,
             @Nullable BroadcastVideoSink localSink,
             @Nullable OutgoingVideoSourceRouter router)
  {
    this.eglBase   = eglBase;
    this.localSink = localSink;
    this.router    = router;
  }

  public @NonNull EglBaseWrapper getLockableEglBase() {
    return eglBase;
  }

  public @Nullable BroadcastVideoSink getLocalSink() {
    return localSink;
  }

  public @NonNull BroadcastVideoSink requireLocalSink() {
    return Objects.requireNonNull(localSink);
  }

  public @Nullable OutgoingVideoSourceRouter getRouter() {
    return router;
  }

  public @NonNull OutgoingVideoSourceRouter requireRouter() {
    return Objects.requireNonNull(router);
  }
}
