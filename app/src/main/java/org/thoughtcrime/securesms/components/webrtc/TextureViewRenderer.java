package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.webrtc.EglBase;
import org.webrtc.EglRenderer;
import org.webrtc.GlRectDrawer;
import org.webrtc.RendererCommon;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

/**
 * This class is a modified version of {@link org.webrtc.SurfaceViewRenderer} which is based on {@link TextureView}
 */
public class TextureViewRenderer extends TextureView implements TextureView.SurfaceTextureListener, VideoSink, RendererCommon.RendererEvents {

  private static final String TAG = Log.tag(TextureViewRenderer.class);

  private final SurfaceTextureEglRenderer         eglRenderer;
  private final RendererCommon.VideoLayoutMeasure videoLayoutMeasure = new RendererCommon.VideoLayoutMeasure();

  private RendererCommon.RendererEvents rendererEvents;
  private int                           rotatedFrameWidth;
  private int                           rotatedFrameHeight;
  private boolean                       enableFixedSize;
  private int                           surfaceWidth;
  private int                           surfaceHeight;
  private boolean                       isInitialized;
  private BroadcastVideoSink            attachedVideoSink;
  private Lifecycle                     lifecycle;

  public TextureViewRenderer(@NonNull Context context) {
    super(context);
    this.eglRenderer = new SurfaceTextureEglRenderer(getResourceName());
    this.setSurfaceTextureListener(this);
  }

  public TextureViewRenderer(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    this.eglRenderer = new SurfaceTextureEglRenderer(getResourceName());
    this.setSurfaceTextureListener(this);
  }

  public void init(@NonNull EglBase eglBase) {
    if (isInitialized) return;

    isInitialized = true;

    this.init(eglBase.getEglBaseContext(), null, EglBase.CONFIG_PLAIN, new GlRectDrawer());
  }

  public void init(@NonNull EglBase.Context sharedContext, @Nullable RendererCommon.RendererEvents rendererEvents, @NonNull int[] configAttributes, @NonNull RendererCommon.GlDrawer drawer) {
    ThreadUtils.checkIsOnMainThread();

    this.rendererEvents     = rendererEvents;
    this.rotatedFrameWidth  = 0;
    this.rotatedFrameHeight = 0;

    this.eglRenderer.init(sharedContext, this, configAttributes, drawer);

    this.lifecycle = ViewUtil.getActivityLifecycle(this);
    if (lifecycle != null) {
      lifecycle.addObserver(new DefaultLifecycleObserver() {
        @Override
        public void onDestroy(@NonNull LifecycleOwner owner) {
          release();
        }
      });
    }
  }

  public void attachBroadcastVideoSink(@Nullable BroadcastVideoSink videoSink) {
    if (attachedVideoSink == videoSink) {
      return;
    }

    eglRenderer.clearImage();

    if (attachedVideoSink != null) {
      attachedVideoSink.removeSink(this);
      attachedVideoSink.removeRequestingSize(this);
    }

    if (videoSink != null) {
      videoSink.addSink(this);
      videoSink.putRequestingSize(this, new Point(getWidth(), getHeight()));
    } else {
      clearImage();
    }

    attachedVideoSink = videoSink;
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if (lifecycle == null || lifecycle.getCurrentState() == Lifecycle.State.DESTROYED) {
      release();
    }
  }

  public void release() {
    eglRenderer.release();
    if (attachedVideoSink != null) {
      attachedVideoSink.removeSink(this);
      attachedVideoSink.removeRequestingSize(this);
    }
  }

  public void removeFrameListener(@NonNull EglRenderer.FrameListener listener) {
    eglRenderer.removeFrameListener(listener);
  }

  public void setMirror(boolean mirror) {
    eglRenderer.setMirror(mirror);
  }

  public void setScalingType(@NonNull RendererCommon.ScalingType scalingType) {
    ThreadUtils.checkIsOnMainThread();

    videoLayoutMeasure.setScalingType(scalingType);

    requestLayout();
  }

  public void setScalingType(@NonNull RendererCommon.ScalingType scalingTypeMatchOrientation,
                             @NonNull RendererCommon.ScalingType scalingTypeMismatchOrientation)
  {
    ThreadUtils.checkIsOnMainThread();

    videoLayoutMeasure.setScalingType(scalingTypeMatchOrientation, scalingTypeMismatchOrientation);

    requestLayout();
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    ThreadUtils.checkIsOnMainThread();

    widthSpec  = MeasureSpec.makeMeasureSpec(resolveSizeAndState(0, widthSpec, 0), MeasureSpec.AT_MOST);
    heightSpec = MeasureSpec.makeMeasureSpec(resolveSizeAndState(0, heightSpec, 0), MeasureSpec.AT_MOST);

    Point size = videoLayoutMeasure.measure(widthSpec, heightSpec, this.rotatedFrameWidth, this.rotatedFrameHeight);

    setMeasuredDimension(size.x, size.y);

    if (attachedVideoSink != null) {
      attachedVideoSink.putRequestingSize(this, size);
    }
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    ThreadUtils.checkIsOnMainThread();

    eglRenderer.setLayoutAspectRatio((float)(right - left) / (float)(bottom - top));

    updateSurfaceSize();
  }

  private void updateSurfaceSize() {
    ThreadUtils.checkIsOnMainThread();

    if (!isAvailable()) {
      return;
    }

    if (this.enableFixedSize && this.rotatedFrameWidth != 0 && this.rotatedFrameHeight != 0 && this.getWidth() != 0 && this.getHeight() != 0) {

      float layoutAspectRatio = (float)this.getWidth() / (float)this.getHeight();
      float frameAspectRatio  = (float)this.rotatedFrameWidth / (float)this.rotatedFrameHeight;

      int drawnFrameWidth;
      int drawnFrameHeight;

      if (frameAspectRatio > layoutAspectRatio) {
        drawnFrameWidth  = (int)((float)this.rotatedFrameHeight * layoutAspectRatio);
        drawnFrameHeight = this.rotatedFrameHeight;
      } else {
        drawnFrameWidth  = this.rotatedFrameWidth;
        drawnFrameHeight = (int)((float)this.rotatedFrameWidth / layoutAspectRatio);
      }

      int width  = Math.min(this.getWidth(), drawnFrameWidth);
      int height = Math.min(this.getHeight(), drawnFrameHeight);

      Log.d(TAG, "updateSurfaceSize. Layout size: " + this.getWidth() + "x" + this.getHeight() + ", frame size: " + this.rotatedFrameWidth + "x" + this.rotatedFrameHeight + ", requested surface size: " + width + "x" + height + ", old surface size: " + this.surfaceWidth + "x" + this.surfaceHeight);

      if (width != this.surfaceWidth || height != this.surfaceHeight) {
        this.surfaceWidth  = width;
        this.surfaceHeight = height;
        getSurfaceTexture().setDefaultBufferSize(width, height);
      }
    } else {
      this.surfaceWidth = this.surfaceHeight = 0;
      this.getSurfaceTexture().setDefaultBufferSize(getMeasuredWidth(), getMeasuredHeight());
    }
  }

  @Override
  public void onFirstFrameRendered() {
    if (this.rendererEvents != null) {
      this.rendererEvents.onFirstFrameRendered();
    }
  }

  @Override
  public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {
    if (this.rendererEvents != null) {
      this.rendererEvents.onFrameResolutionChanged(videoWidth, videoHeight, rotation);
    }

    int rotatedWidth = rotation != 0 && rotation != 180 ? videoHeight : videoWidth;
    int rotatedHeight = rotation != 0 && rotation != 180 ? videoWidth : videoHeight;
    this.postOrRun(() -> {
      this.rotatedFrameWidth = rotatedWidth;
      this.rotatedFrameHeight = rotatedHeight;
      this.updateSurfaceSize();
      this.requestLayout();
    });
  }

  @Override
  public void onFrame(VideoFrame videoFrame) {
    if (isAttachedToWindow()) {
      eglRenderer.onFrame(videoFrame);
    }
  }

  @Override
  public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
    ThreadUtils.checkIsOnMainThread();

    surfaceWidth  = 0;
    surfaceHeight = 0;

    updateSurfaceSize();

    eglRenderer.onSurfaceTextureAvailable(surface, width, height);
  }

  @Override
  public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    eglRenderer.onSurfaceTextureSizeChanged(surface, width, height);
  }

  @Override
  public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
    return eglRenderer.onSurfaceTextureDestroyed(surface);
  }

  @Override
  public void onSurfaceTextureUpdated(SurfaceTexture surface) {
  }

  private String getResourceName() {
    try {
      return this.getResources().getResourceEntryName(this.getId());
    } catch (Resources.NotFoundException var2) {
      return "";
    }
  }

  public void clearImage() {
    this.eglRenderer.clearImage();
  }

  private void postOrRun(Runnable r) {
    if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
      r.run();
    } else {
      this.post(r);
    }

  }
}
