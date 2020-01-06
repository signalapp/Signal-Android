package org.thoughtcrime.securesms.components.camera;

import android.content.Context;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
  private boolean ready;

  @SuppressWarnings("deprecation")
  public CameraSurfaceView(Context context) {
    super(context);
    getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    getHolder().addCallback(this);
  }

  public boolean isReady() {
    return ready;
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    ready = true;
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    ready = false;
  }
}
