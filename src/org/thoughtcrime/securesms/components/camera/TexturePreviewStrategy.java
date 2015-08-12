package org.thoughtcrime.securesms.components.camera;
/***
 Copyright (c) 2013 CommonsWare, LLC

 Licensed under the Apache License, Version 2.0 (the "License"); you may
 not use this file except in compliance with the License. You may obtain
 a copy of the License at
 http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import java.io.IOException;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class TexturePreviewStrategy implements PreviewStrategy,
    TextureView.SurfaceTextureListener {
  private final static String TAG = TexturePreviewStrategy.class.getSimpleName();
  private final CameraView cameraView;
  private TextureView widget=null;
  private SurfaceTexture surface=null;

  TexturePreviewStrategy(CameraView cameraView) {
    this.cameraView=cameraView;
    widget=new TextureView(cameraView.getContext());
    widget.setSurfaceTextureListener(this);
  }

  @Override
  public void onSurfaceTextureAvailable(SurfaceTexture surface,
                                        int width, int height) {
    Log.w(TAG, "onSurfaceTextureAvailable()");
    this.surface=surface;
    synchronized (cameraView) { cameraView.notifyAll(); }
  }

  @Override
  public void onSurfaceTextureSizeChanged(SurfaceTexture surface,
                                          int width, int height) {
    Log.w(TAG, "onSurfaceTextureChanged()");
  }

  @Override
  public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
    Log.w(TAG, "onSurfaceTextureDestroyed()");
    cameraView.onPause();

    return(true);
  }

  @Override
  public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    // no-op
  }

  @Override
  public void attach(Camera camera) throws IOException {
    camera.setPreviewTexture(surface);
  }

  @Override
  public void attach(MediaRecorder recorder) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      // no-op
    }
    else {
      throw new IllegalStateException(
          "Cannot use TextureView with MediaRecorder");
    }
  }

  @Override
  public boolean isReady() {
    return widget.isAvailable();
  }

  @Override
  public View getWidget() {
    return(widget);
  }
}