package org.thoughtcrime.securesms.mediasend.camerax;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.camera.core.ImageCapture;

import org.thoughtcrime.securesms.R;

import java.util.Arrays;
import java.util.List;

public final class CameraXFlashToggleView extends AppCompatImageView {

  private static final String STATE_FLASH_INDEX  = "flash.toggle.state.flash.index";
  private static final String STATE_SUPPORT_AUTO = "flash.toggle.state.support.auto";
  private static final String STATE_PARENT       = "flash.toggle.state.parent";

  private static final int[]           FLASH_AUTO     = { R.attr.state_flash_auto         };
  private static final int[]           FLASH_OFF      = { R.attr.state_flash_off          };
  private static final int[]           FLASH_ON       = { R.attr.state_flash_on           };
  private static final int[][]         FLASH_ENUM     = { FLASH_AUTO, FLASH_OFF, FLASH_ON };
  private static final List<FlashMode> FLASH_MODES    = Arrays.asList(FlashMode.AUTO, FlashMode.OFF, FlashMode.ON);
  private static final FlashMode       FLASH_FALLBACK = FlashMode.OFF;

  private boolean                    supportsFlashModeAuto = true;
  private int                        flashIndex;
  private OnFlashModeChangedListener flashModeChangedListener;

  public CameraXFlashToggleView(Context context) {
    this(context, null);
  }

  public CameraXFlashToggleView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public CameraXFlashToggleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    super.setOnClickListener((v) -> setFlash(FLASH_MODES.get((flashIndex + 1) % FLASH_ENUM.length).getFlashMode()));
  }

  @Override
  public int[] onCreateDrawableState(int extraSpace) {
    final int[] extra         = FLASH_ENUM[flashIndex];
    final int[] drawableState = super.onCreateDrawableState(extraSpace + extra.length);
    mergeDrawableStates(drawableState, extra);
    return drawableState;
  }

  @Override
  public void setOnClickListener(@Nullable OnClickListener l) {
    throw new IllegalStateException("This View does not support custom click listeners.");
  }

  public void setAutoFlashEnabled(boolean isAutoEnabled) {
    supportsFlashModeAuto = isAutoEnabled;
    setFlash(FLASH_MODES.get(flashIndex).getFlashMode());
  }

  public void setFlash(@ImageCapture.FlashMode int mode) {
    FlashMode flashMode = FlashMode.fromImageCaptureFlashMode(mode);

    flashIndex = resolveFlashIndex(FLASH_MODES.indexOf(flashMode), supportsFlashModeAuto);
    refreshDrawableState();
    notifyListener();
  }

  public void setOnFlashModeChangedListener(@Nullable OnFlashModeChangedListener listener) {
    this.flashModeChangedListener = listener;
  }

  @Override
  protected Parcelable onSaveInstanceState() {
    Parcelable parentState = super.onSaveInstanceState();
    Bundle     bundle      = new Bundle();

    bundle.putParcelable(STATE_PARENT, parentState);
    bundle.putInt(STATE_FLASH_INDEX, flashIndex);
    bundle.putBoolean(STATE_SUPPORT_AUTO, supportsFlashModeAuto);
    return bundle;
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    if (state instanceof Bundle) {
      Bundle savedState = (Bundle) state;

      supportsFlashModeAuto = savedState.getBoolean(STATE_SUPPORT_AUTO);
      setFlash(FLASH_MODES.get(
          resolveFlashIndex(savedState.getInt(STATE_FLASH_INDEX), supportsFlashModeAuto)).getFlashMode()
      );

      super.onRestoreInstanceState(savedState.getParcelable(STATE_PARENT));
    } else {
      super.onRestoreInstanceState(state);
    }
  }

  private void notifyListener() {
    if (flashModeChangedListener == null) return;

    flashModeChangedListener.flashModeChanged(FLASH_MODES.get(flashIndex).getFlashMode());
  }

  private static int resolveFlashIndex(int desiredFlashIndex, boolean supportsFlashModeAuto) {
    if (isIllegalFlashIndex(desiredFlashIndex)) {
      throw new IllegalArgumentException("Unsupported index: " + desiredFlashIndex);
    }
    if (isUnsupportedFlashMode(desiredFlashIndex, supportsFlashModeAuto)) {
      return FLASH_MODES.indexOf(FLASH_FALLBACK);
    }
    return desiredFlashIndex;
  }

  private static boolean isIllegalFlashIndex(int desiredFlashIndex) {
    return desiredFlashIndex < 0 || desiredFlashIndex > FLASH_ENUM.length;
  }

  private static boolean isUnsupportedFlashMode(int desiredFlashIndex, boolean supportsFlashModeAuto) {
    return FLASH_MODES.get(desiredFlashIndex) == FlashMode.AUTO && !supportsFlashModeAuto;
  }

  public interface OnFlashModeChangedListener {
    void flashModeChanged(int flashMode);
  }

  private enum FlashMode {

    AUTO(ImageCapture.FLASH_MODE_AUTO),
    OFF(ImageCapture.FLASH_MODE_OFF),
    ON(ImageCapture.FLASH_MODE_ON);

    private final @ImageCapture.FlashMode int flashMode;

    FlashMode(@ImageCapture.FlashMode int flashMode) {
      this.flashMode = flashMode;
    }

    @ImageCapture.FlashMode int getFlashMode() {
      return flashMode;
    }

    private static FlashMode fromImageCaptureFlashMode(@ImageCapture.FlashMode int flashMode) {
      for (FlashMode mode : values()) {
        if (mode.getFlashMode() == flashMode) {
          return mode;
        }
      }

      throw new AssertionError();
    }
  }
}
