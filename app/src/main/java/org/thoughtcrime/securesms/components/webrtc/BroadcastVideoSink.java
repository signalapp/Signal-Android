package org.thoughtcrime.securesms.components.webrtc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.webrtc.EglBase;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.WeakHashMap;

public class BroadcastVideoSink implements VideoSink {

  private final EglBase                         eglBase;
  private final WeakHashMap<VideoSink, Boolean> sinks;

  public BroadcastVideoSink(@Nullable EglBase eglBase) {
    this.eglBase = eglBase;
    this.sinks   = new WeakHashMap<>();
  }

  public @Nullable EglBase getEglBase() {
    return eglBase;
  }

  public void addSink(@NonNull VideoSink sink) {
    sinks.put(sink, true);
  }

  public void removeSink(@NonNull VideoSink sink) {
    sinks.remove(sink);
  }

  @Override
  public void onFrame(@NonNull VideoFrame videoFrame) {
    for (VideoSink sink : sinks.keySet()) {
      sink.onFrame(videoFrame);
    }
  }
}
