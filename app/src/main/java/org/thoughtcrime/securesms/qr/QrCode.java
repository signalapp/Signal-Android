package org.thoughtcrime.securesms.qr;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Stopwatch;

public final class QrCode {

  private QrCode() {
  }

  public static final String TAG = Log.tag(QrCode.class);

  public static @NonNull Bitmap create(@Nullable String data) {
    return create(data, Color.BLACK, Color.TRANSPARENT);
  }

  public static @NonNull Bitmap create(@Nullable String data,
                                       @ColorInt int foregroundColor,
                                       @ColorInt int backgroundColor)
  {
    if (data == null || data.length() == 0) {
      Log.w(TAG, "No data");
      return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
    }

    try {
      Stopwatch    stopwatch    = new Stopwatch("QrGen");
      QRCodeWriter qrCodeWriter = new QRCodeWriter();
      BitMatrix    qrData       = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, 512, 512);
      int          qrWidth      = qrData.getWidth();
      int          qrHeight     = qrData.getHeight();
      int[]        pixels       = new int[qrWidth * qrHeight];

      for (int y = 0; y < qrHeight; y++) {
        int offset = y * qrWidth;

        for (int x = 0; x < qrWidth; x++) {
          pixels[offset + x] = qrData.get(x, y) ? foregroundColor : backgroundColor;
        }
      }
      stopwatch.split("Write pixels");

      Bitmap bitmap = Bitmap.createBitmap(pixels, qrWidth, qrHeight, Bitmap.Config.ARGB_8888);

      stopwatch.split("Create bitmap");
      stopwatch.stop(TAG);

      return bitmap;
    } catch (WriterException e) {
      Log.w(TAG, e);
      return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
    }
  }

}
