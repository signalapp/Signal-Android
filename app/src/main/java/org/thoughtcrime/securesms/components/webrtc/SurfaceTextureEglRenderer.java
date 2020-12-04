package org.thoughtcrime.securesms.components.webrtc;

import android.graphics.SurfaceTexture;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.webrtc.EglBase;
import org.webrtc.EglRenderer;
import org.webrtc.RendererCommon;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;

import java.util.concurrent.CountDownLatch;

/**
 * This class is a modified copy of {@link org.webrtc.SurfaceViewRenderer} designed to work with a
 * {@link SurfaceTexture} to facilitate easier animation, rounding, elevation, etc.
 */
public class SurfaceTextureEglRenderer extends EglRenderer implements TextureView.SurfaceTextureListener {

  private static final String TAG = Log.tag(SurfaceTextureEglRenderer.class);

  private final Object layoutLock = new Object();

  private RendererCommon.RendererEvents rendererEvents;
  private boolean                       isFirstFrameRendered;
  private boolean                       isRenderingPaused;
  private int                           rotatedFrameWidth;
  private int                           rotatedFrameHeight;
  private int                           frameRotation;

  public SurfaceTextureEglRenderer(@NonNull String name) {
    super(name);
  }

  public void init(@Nullable EglBase.Context sharedContext, @Nullable RendererCommon.RendererEvents rendererEvents, @NonNull int[] configAttributes, @NonNull RendererCommon.GlDrawer drawer) {
    ThreadUtils.checkIsOnMainThread();
    this.rendererEvents = rendererEvents;
    synchronized (this.layoutLock) {
      this.isFirstFrameRendered = false;
      this.rotatedFrameWidth    = 0;
      this.rotatedFrameHeight   = 0;
      this.frameRotation        = 0;
    }

    super.init(sharedContext, configAttributes, drawer);
  }

  @Override
  public void init(@Nullable EglBase.Context sharedContext, @NonNull int[] configAttributes, @NonNull RendererCommon.GlDrawer drawer) {
    this.init(sharedContext, null, configAttributes, drawer);
  }

  @Override
  public void setFpsReduction(float fps) {
    synchronized(this.layoutLock) {
      this.isRenderingPaused = fps == 0.0F;
    }

    super.setFpsReduction(fps);
  }

  @Override
  public void disableFpsReduction() {
    synchronized(this.layoutLock) {
      this.isRenderingPaused = false;
    }

    super.disableFpsReduction();
  }

  @Override
  public void pauseVideo() {
    synchronized(this.layoutLock) {
      this.isRenderingPaused = true;
    }

    super.pauseVideo();
  }

  @Override
  public void onFrame(@NonNull VideoFrame frame) {
    this.updateFrameDimensionsAndReportEvents(frame);
    super.onFrame(frame);
  }

  @Override
  public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
    ThreadUtils.checkIsOnMainThread();
    createEglSurface(surface);
  }

  @Override
  public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "onSurfaceTextureSizeChanged: size: " + width + "x" + height);
  }

  @Override
  public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
    ThreadUtils.checkIsOnMainThread();

    CountDownLatch completionLatch = new CountDownLatch(1);

    releaseEglSurface(completionLatch::countDown);
    ThreadUtils.awaitUninterruptibly(completionLatch);

    return true;
  }

  @Override
  public void onSurfaceTextureUpdated(SurfaceTexture surface) {
  }

  private void updateFrameDimensionsAndReportEvents(VideoFrame frame) {
    synchronized(this.layoutLock) {
      if (!this.isRenderingPaused) {
        if (!this.isFirstFrameRendered) {
          this.isFirstFrameRendered = true;
          Log.d(TAG, "Reporting first rendered frame.");
          if (this.rendererEvents != null) {
            this.rendererEvents.onFirstFrameRendered();
          }
        }

        if (this.rotatedFrameWidth != frame.getRotatedWidth() || this.rotatedFrameHeight != frame.getRotatedHeight() || this.frameRotation != frame.getRotation()) {
          Log.d(TAG, "Reporting frame resolution changed to " + frame.getBuffer().getWidth() + "x" + frame.getBuffer().getHeight() + " with rotation " + frame.getRotation());
          if (this.rendererEvents != null) {
            this.rendererEvents.onFrameResolutionChanged(frame.getBuffer().getWidth(), frame.getBuffer().getHeight(), frame.getRotation());
          }

          this.rotatedFrameWidth  = frame.getRotatedWidth();
          this.rotatedFrameHeight = frame.getRotatedHeight();
          this.frameRotation      = frame.getRotation();
        }
      }
    }
  }
}
