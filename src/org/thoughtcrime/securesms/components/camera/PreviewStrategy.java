package org.thoughtcrime.securesms.components.camera;

import android.hardware.Camera;
import android.media.MediaRecorder;
import android.view.View;

import java.io.IOException;

@SuppressWarnings("deprecation")
public interface PreviewStrategy extends com.commonsware.cwac.camera.PreviewStrategy {
  boolean isReady();
}
