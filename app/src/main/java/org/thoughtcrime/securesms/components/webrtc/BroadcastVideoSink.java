package org.thoughtcrime.securesms.components.webrtc;

import android.graphics.Point;

import androidx.annotation.NonNull;

import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Video sink implementation that handles broadcasting a single source video track to
 * multiple {@link VideoSink} consumers.
 *
 * Also has logic to manage rotating frames before forwarding to prevent each renderer
 * from having to copy the frame for rotation.
 */
public class BroadcastVideoSink implements VideoSink {

  public static final int DEVICE_ROTATION_IGNORE = -1;

  private final EglBaseWrapper                  eglBase;
  private final WeakHashMap<VideoSink, Boolean> sinks;
  private final WeakHashMap<Object, Point>      requestingSizes;
  private       int                             deviceOrientationDegrees;
  private       boolean                         rotateToRightSide;
  private final boolean                         forceRotate;
  private final boolean                         rotateWithDevice;
  private       RequestedSize                   currentlyRequestedMaxSize;

  public BroadcastVideoSink() {
    this(new EglBaseWrapper(), false, true, 0);
  }

  /**
   * @param eglBase                  Rendering context
   * @param forceRotate              Always rotate video frames regardless of frame dimension
   * @param rotateWithDevice         Rotate video frame to match device orientation
   * @param deviceOrientationDegrees Device orientation in degrees
   */
  public BroadcastVideoSink(@NonNull EglBaseWrapper eglBase, boolean forceRotate, boolean rotateWithDevice, int deviceOrientationDegrees) {
    this.eglBase                  = eglBase;
    this.sinks                    = new WeakHashMap<>();
    this.requestingSizes          = new WeakHashMap<>();
    this.deviceOrientationDegrees = deviceOrientationDegrees;
    this.rotateToRightSide        = false;
    this.forceRotate              = forceRotate;
    this.rotateWithDevice         = false;
  }

  public @NonNull EglBaseWrapper getLockableEglBase() {
    return eglBase;
  }

  public synchronized void addSink(@NonNull VideoSink sink) {
    sinks.put(sink, true);
  }

  public synchronized void removeSink(@NonNull VideoSink sink) {
    sinks.remove(sink);
  }

  /**
   * Set the specific rotation desired when not rotating with device.
   *
   * Really only needed for properly rotating self camera views.
   */
  public void setRotateToRightSide(boolean rotateToRightSide) {
    this.rotateToRightSide = rotateToRightSide;
  }

  public void setDeviceOrientationDegrees(int deviceOrientationDegrees) {
    this.deviceOrientationDegrees = deviceOrientationDegrees;
  }

  @Override
  public synchronized void onFrame(@NonNull VideoFrame videoFrame) {
    boolean isDeviceRotationIgnored = deviceOrientationDegrees == DEVICE_ROTATION_IGNORE;

    if (!isDeviceRotationIgnored && forceRotate) {
      int rotation = calculateRotation();
      if (rotation > 0) {
        rotation += rotateWithDevice ? videoFrame.getRotation() : 0;
        videoFrame = new VideoFrame(videoFrame.getBuffer(), rotation % 360, videoFrame.getTimestampNs());
      }
    }

    for (VideoSink sink : sinks.keySet()) {
      sink.onFrame(videoFrame);
    }
  }

  private int calculateRotation() {
    if (forceRotate && (deviceOrientationDegrees == 0 || deviceOrientationDegrees == 180)) {
      return 0;
    }

    if (rotateWithDevice) {
      if (forceRotate) {
        return deviceOrientationDegrees;
      } else {
        return deviceOrientationDegrees != 0 && deviceOrientationDegrees != 180 ? deviceOrientationDegrees : 270;
      }
    }

    return rotateToRightSide ? 90 : 270;
  }

  void putRequestingSize(@NonNull Object object, @NonNull Point size) {
    if (size.x == 0 || size.y == 0) {
      return;
    }

    synchronized (requestingSizes) {
      requestingSizes.put(object, size);
    }
  }

  void removeRequestingSize(@NonNull Object object) {
    synchronized (requestingSizes) {
      requestingSizes.remove(object);
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

  public void setCurrentlyRequestedMaxSize(@NonNull RequestedSize currentlyRequestedMaxSize) {
    this.currentlyRequestedMaxSize = currentlyRequestedMaxSize;
  }

  public boolean needsNewRequestingSize() {
    return !getMaxRequestingSize().equals(currentlyRequestedMaxSize);
  }

  public static final class RequestedSize {
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

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final RequestedSize that = (RequestedSize) o;
      return width == that.width && height == that.height;
    }

    @Override
    public int hashCode() {
      return Objects.hash(width, height);
    }
  }
}
