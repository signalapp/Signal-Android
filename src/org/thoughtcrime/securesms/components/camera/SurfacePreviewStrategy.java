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

package org.thoughtcrime.securesms.components.camera;

import android.hardware.Camera;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.io.IOException;

class SurfacePreviewStrategy implements PreviewStrategy,
    SurfaceHolder.Callback {
  private final static String TAG = SurfacePreviewStrategy.class.getSimpleName();
  private final CameraView cameraView;
  private SurfaceView preview=null;
  private SurfaceHolder previewHolder=null;
  private boolean ready = false;

  @SuppressWarnings("deprecation")
  SurfacePreviewStrategy(CameraView cameraView) {
    this.cameraView=cameraView;
    preview=new SurfaceView(cameraView.getContext());
    previewHolder=preview.getHolder();
    previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    previewHolder.addCallback(this);
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    Log.w(TAG, "surfaceCreated()");
    ready = true;
    synchronized (cameraView) { cameraView.notifyAll(); }
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format,
                             int width, int height) {
    Log.w(TAG, "surfaceChanged()");
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    Log.w(TAG, "surfaceDestroyed()");
    cameraView.onPause();
  }

  @Override
  public void attach(Camera camera) throws IOException {
    Log.w(TAG, "attach(Camera)");
    camera.setPreviewDisplay(previewHolder);
  }

  @Override
  public void attach(MediaRecorder recorder) {
    recorder.setPreviewDisplay(previewHolder.getSurface());
  }

  @Override
  public View getWidget() {
    return(preview);
  }

  @Override
  public boolean isReady() {
    return ready;
  }
}