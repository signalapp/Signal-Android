package org.thoughtcrime.securesms.components.webrtc;

import android.graphics.Point;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.webrtc.EglBase;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.whispersystems.libsignal.util.Pair;

import java.util.WeakHashMap;

public class BroadcastVideoSink implements VideoSink {

  private final EglBase                         eglBase;
  private final WeakHashMap<VideoSink, Boolean> sinks;
  private final WeakHashMap<Object, Point>      requestingSizes;
  private       boolean                         dirtySizes;

  public BroadcastVideoSink(@Nullable EglBase eglBase) {
    this.eglBase         = eglBase;
    this.sinks           = new WeakHashMap<>();
    this.requestingSizes = new WeakHashMap<>();
    this.dirtySizes      = true;
  }

  public @Nullable EglBase getEglBase() {
    return eglBase;
  }

  public synchronized void addSink(@NonNull VideoSink sink) {
    sinks.put(sink, true);
  }

  public synchronized void removeSink(@NonNull VideoSink sink) {
    sinks.remove(sink);
  }

  @Override
  public synchronized void onFrame(@NonNull VideoFrame videoFrame) {
    for (VideoSink sink : sinks.keySet()) {
      sink.onFrame(videoFrame);
    }
  }

  void putRequestingSize(@NonNull Object object, @NonNull Point size) {
    synchronized (requestingSizes) {
      requestingSizes.put(object, size);
      dirtySizes = true;
    }
  }

  void removeRequestingSize(@NonNull Object object) {
    synchronized (requestingSizes) {
      requestingSizes.remove(object);
      dirtySizes = true;
    }
  }

  public @NonNull RequestedSize getMaxRequestingSize() {
    int width  = 0;
    int height = 0;

    synchronized (requestingSizes) {
      for (Point size : requestingSizes.values()) {
        if (width < size.x) {
          width  = size.x;
          height = size.y;
        }
      }
    }

    return new RequestedSize(width, height);
  }

  public void newSizeRequested() {
    dirtySizes = false;
  }

  public boolean needsNewRequestingSize() {
    return dirtySizes;
  }

  public static class RequestedSize {
    private final int width;
    private final int height;

    private RequestedSize(int width, int height) {
      this.width  = width;
      this.height = height;
    }

    public int getWidth() {
      return width;
    }

    public int getHeight() {
      return height;
    }
  }
}
