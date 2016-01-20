package org.privatechats.securesms;

import android.animation.Animator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.privatechats.securesms.components.camera.CameraView;
import org.privatechats.securesms.components.camera.CameraView.PreviewCallback;
import org.privatechats.securesms.components.camera.CameraView.PreviewFrame;
import org.privatechats.securesms.util.Util;
import org.privatechats.securesms.util.ViewUtil;

public class DeviceAddFragment extends Fragment implements PreviewCallback {

  private static final String TAG = DeviceAddFragment.class.getSimpleName();

  private final QRCodeReader reader = new QRCodeReader();

  private ViewGroup      container;
  private LinearLayout   overlay;
  private ImageView      devicesImage;
  private CameraView     scannerView;
  private PreviewFrame   previewFrame;
  private ScanningThread scanningThread;
  private ScanListener   scanListener;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
    this.container    = ViewUtil.inflate(inflater, viewGroup, R.layout.device_add_fragment);
    this.overlay      = ViewUtil.findById(this.container, R.id.overlay);
    this.scannerView  = ViewUtil.findById(this.container, R.id.scanner);
    this.devicesImage = ViewUtil.findById(this.container, R.id.devices);
    this.scannerView.onResume();
    this.scannerView.setPreviewCallback(this);

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
    this.scannerView.onResume();
    this.scannerView.setPreviewCallback(this);
    this.previewFrame   = null;
    this.scanningThread = new ScanningThread();
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
    this.scannerView.setPreviewCallback(this);
  }

  @Override
  public void onPreviewFrame(@NonNull PreviewFrame previewFrame) {
    Context context = getActivity();

    try {
      if (context != null) {
        synchronized (this) {
          this.previewFrame = previewFrame;
          this.notify();
        }
      }
    } catch (RuntimeException e) {
      Log.w(TAG, e);
    }
  }

  public ImageView getDevicesImage() {
    return devicesImage;
  }

  public void setScanListener(ScanListener scanListener) {
    this.scanListener = scanListener;
  }

  private class ScanningThread extends Thread {

    private boolean scanning = true;

    @Override
    public void run() {
      while (true) {
        PreviewFrame ourFrame;

        synchronized (DeviceAddFragment.this) {
          while (scanning && previewFrame == null) {
            Util.wait(DeviceAddFragment.this, 0);
          }

          if (!scanning) return;
          else           ourFrame = previewFrame;

          previewFrame = null;
        }

        String url = getUrl(ourFrame.getData(), ourFrame.getWidth(), ourFrame.getHeight(), ourFrame.getOrientation());

        if (url != null && scanListener != null) {
          Uri uri = Uri.parse(url);
          scanListener.onUrlFound(uri);
          return;
        }
      }
    }

    public void stopScanning() {
      synchronized (DeviceAddFragment.this) {
        scanning = false;
        DeviceAddFragment.this.notify();
      }
    }

    private @Nullable String getUrl(byte[] data, int width, int height, int orientation) {
      try {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
          byte[] rotatedData = new byte[data.length];

          for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
              rotatedData[x * height + height - y - 1] = data[x + y * width];
            }
          }

          int tmp = width;
          width  = height;
          height = tmp;
          data   = rotatedData;
        }

        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data, width, height,
                                                                       0, 0, width, height,
                                                                       false);

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        Result result = reader.decode(bitmap);

        if (result != null) return result.getText();

      } catch (NullPointerException | ChecksumException | FormatException e) {
        Log.w(TAG, e);
      } catch (NotFoundException e) {
        // Thanks ZXing...
      }

      return null;
    }
  }

  public interface ScanListener {
    public void onUrlFound(Uri uri);
  }
}
