package org.thoughtcrime.securesms.qr;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.annotation.NonNull;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.thoughtcrime.securesms.logging.Log;

import java.util.HashMap;

public class QrCode {

  public static final String TAG = QrCode.class.getSimpleName();

  public static @NonNull Bitmap create(String data) {
    return create(data, 1024);
  }
  public static @NonNull Bitmap create(String data, int size) { return create(data, size, 2); }
  public static @NonNull Bitmap create(String data, int size, int margin) {
    try {
      HashMap<EncodeHintType, Integer> hintMap = new HashMap<>();
      hintMap.put(EncodeHintType.MARGIN, margin);
      BitMatrix result = new QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size, hintMap);
      Bitmap    bitmap = Bitmap.createBitmap(result.getWidth(), result.getHeight(), Bitmap.Config.ARGB_8888);

      for (int y = 0; y < result.getHeight(); y++) {
        for (int x = 0; x < result.getWidth(); x++) {
          if (result.get(x, y)) {
            bitmap.setPixel(x, y, Color.BLACK);
          }
        }
      }

      return bitmap;
    } catch (WriterException e) {
      Log.w(TAG, e);
      return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
    }
  }

}
