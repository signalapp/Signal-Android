package org.thoughtcrime.securesms.components.qr;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import com.google.zxing.common.BitMatrix;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.SquareImageView;
import org.thoughtcrime.securesms.qr.QrCodeUtil;

/**
 * Generates a bitmap asynchronously for the supplied {@link BitMatrix} data and displays it.
 */
public class QrView extends SquareImageView {

  private static final @ColorInt int DEFAULT_FOREGROUND_COLOR = Color.BLACK;
  private static final @ColorInt int DEFAULT_BACKGROUND_COLOR = Color.TRANSPARENT;

  private @Nullable Bitmap qrBitmap;
  private @ColorInt int    foregroundColor;
  private @ColorInt int    backgroundColor;

  public QrView(Context context) {
    super(context);
    init(null);
  }

  public QrView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  public QrView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(attrs);
  }

  private void init(@Nullable AttributeSet attrs) {
    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.QrView, 0, 0);
      foregroundColor = typedArray.getColor(R.styleable.QrView_qr_foreground_color, DEFAULT_FOREGROUND_COLOR);
      backgroundColor = typedArray.getColor(R.styleable.QrView_qr_background_color, DEFAULT_BACKGROUND_COLOR);
      typedArray.recycle();
    } else {
      foregroundColor = DEFAULT_FOREGROUND_COLOR;
      backgroundColor = DEFAULT_BACKGROUND_COLOR;
    }

    if (isInEditMode()) {
      setQrText("https://signal.org");
    }
  }

  public void setQrText(@Nullable String text) {
    setQrBitmap(QrCodeUtil.create(text, foregroundColor, backgroundColor));
  }

  private void setQrBitmap(@Nullable Bitmap qrBitmap) {
    if (this.qrBitmap == qrBitmap) {
      return;
    }

    if (this.qrBitmap != null) {
      this.qrBitmap.recycle();
    }

    this.qrBitmap = qrBitmap;

    setImageBitmap(this.qrBitmap);
  }

  public @Nullable Bitmap getQrBitmap() {
    return qrBitmap;
  }
}
