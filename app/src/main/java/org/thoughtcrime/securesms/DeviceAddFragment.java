package org.thoughtcrime.securesms;

import android.animation.Animator;
import android.annotation.TargetApi;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.components.camera.CameraView;
import org.thoughtcrime.securesms.qr.ScanListener;
import org.thoughtcrime.securesms.qr.ScanningThread;
import org.thoughtcrime.securesms.util.ViewUtil;

public class DeviceAddFragment extends LoggingFragment {

  private ViewGroup      container;
  private LinearLayout   overlay;
  private ImageView      devicesImage;
  private CameraView     scannerView;
  private ScanningThread scanningThread;
  private ScanListener   scanListener;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
    this.container    = ViewUtil.inflate(inflater, viewGroup, R.layout.device_add_fragment);
    this.overlay      = this.container.findViewById(R.id.overlay);
    this.scannerView  = this.container.findViewById(R.id.scanner);
    this.devicesImage = this.container.findViewById(R.id.devices);

    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
      this.overlay.setOrientation(LinearLayout.HORIZONTAL);
    } else {
      this.overlay.setOrientation(LinearLayout.VERTICAL);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      this.container.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                   int oldLeft, int oldTop, int oldRight, int oldBottom)
        {
          v.removeOnLayoutChangeListener(this);

          Animator reveal = ViewAnimationUtils.createCircularReveal(v, right, bottom, 0, (int) Math.hypot(right, bottom));
          reveal.setInterpolator(new DecelerateInterpolator(2f));
          reveal.setDuration(800);
          reveal.start();
        }
      });
    }

    return this.container;
  }

  @Override
  public void onResume() {
    super.onResume();
    this.scanningThread = new ScanningThread();
    this.scanningThread.setScanListener(scanListener);
    this.scannerView.onResume();
    this.scannerView.setPreviewCallback(scanningThread);
    this.scanningThread.start();
  }

  @Override
  public void onPause() {
    super.onPause();
    this.scannerView.onPause();
    this.scanningThread.stopScanning();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfiguration) {
    super.onConfigurationChanged(newConfiguration);

    this.scannerView.onPause();

    if (newConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      overlay.setOrientation(LinearLayout.HORIZONTAL);
    } else {
      overlay.setOrientation(LinearLayout.VERTICAL);
    }

    this.scannerView.onResume();
    this.scannerView.setPreviewCallback(scanningThread);
  }


  public ImageView getDevicesImage() {
    return devicesImage;
  }

  public void setScanListener(ScanListener scanListener) {
    this.scanListener = scanListener;

    if (this.scanningThread != null) {
      this.scanningThread.setScanListener(scanListener);
    }
  }


}
