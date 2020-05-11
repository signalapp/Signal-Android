package org.thoughtcrime.securesms.qr;

import android.content.res.Configuration;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.thoughtcrime.securesms.logging.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.thoughtcrime.securesms.components.camera.CameraView;
import org.thoughtcrime.securesms.components.camera.CameraView.PreviewFrame;
import org.thoughtcrime.securesms.util.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ScanningThread extends Thread implements CameraView.PreviewCallback {

  private static final String TAG = ScanningThread.class.getSimpleName();

  private final QRCodeReader                  reader       = new QRCodeReader();
  private final AtomicReference<ScanListener> scanListener = new AtomicReference<>();
  private final Map<DecodeHintType, String>   hints        = new HashMap<>();

  private boolean scanning = true;
  private PreviewFrame previewFrame;

  public void setCharacterSet(String characterSet) {
    hints.put(DecodeHintType.CHARACTER_SET, characterSet);
  }

  public void setScanListener(ScanListener scanListener) {
    this.scanListener.set(scanListener);
  }

  @Override
  public void onPreviewFrame(@NonNull PreviewFrame previewFrame) {
    try {
      synchronized (this) {
        this.previewFrame = previewFrame;
        this.notify();
      }
    } catch (RuntimeException e) {
      Log.w(TAG, e);
    }
  }


  @Override
  public void run() {
    while (true) {
      PreviewFrame ourFrame;

      synchronized (this) {
        while (scanning && previewFrame == null) {
          Util.wait(this, 0);
        }

        if (!scanning) return;
        else           ourFrame = previewFrame;

        previewFrame = null;
      }

      String       data         = getScannedData(ourFrame.getData(), ourFrame.getWidth(), ourFrame.getHeight(), ourFrame.getOrientation());
      ScanListener scanListener = this.scanListener.get();

      if (data != null && scanListener != null) {
        scanListener.onQrDataFound(data);
        return;
      }
    }
  }

  public void stopScanning() {
    synchronized (this) {
      scanning = false;
      notify();
    }
  }

  private @Nullable String getScannedData(byte[] data, int width, int height, int orientation) {
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
      Result       result = reader.decode(bitmap, hints);

      if (result != null) return result.getText();

    } catch (NullPointerException | ChecksumException | FormatException e) {
      Log.w(TAG, e);
    } catch (NotFoundException e) {
      // Thanks ZXing...
    }

    return null;
  }
}
