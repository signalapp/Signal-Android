package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;

import java.util.Arrays;
import java.util.List;

public class WebRtcAudioOutputToggleButton extends AppCompatImageView {

  private static final String STATE_OUTPUT_INDEX      = "audio.output.toggle.state.output.index";
  private static final String STATE_HEADSET_ENABLED   = "audio.output.toggle.state.headset.enabled";
  private static final String STATE_PARENT            = "audio.output.toggle.state.parent";

  private static final int[]                   OUTPUT_HANDSET          = { R.attr.state_handset };
  private static final int[]                   OUTPUT_SPEAKER          = { R.attr.state_speaker };
  private static final int[]                   OUTPUT_HEADSET          = { R.attr.state_headset };
  private static final int[][]                 OUTPUT_ENUM             = { OUTPUT_HANDSET, OUTPUT_SPEAKER, OUTPUT_HEADSET };
  private static final List<WebRtcAudioOutput> OUTPUT_MODES            = Arrays.asList(WebRtcAudioOutput.HANDSET, WebRtcAudioOutput.SPEAKER, WebRtcAudioOutput.HEADSET);
  private static final WebRtcAudioOutput       OUTPUT_FALLBACK         = WebRtcAudioOutput.HANDSET;

  private boolean                      isHeadsetAvailable;
  private int                          outputIndex;
  private OnAudioOutputChangedListener audioOutputChangedListener;
  private AlertDialog                  picker;

  public WebRtcAudioOutputToggleButton(Context context) {
    this(context, null);
  }

  public WebRtcAudioOutputToggleButton(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public WebRtcAudioOutputToggleButton(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    super.setOnClickListener((v) -> {
      if (isHeadsetAvailable) showPicker();
      else                    setAudioOutput(OUTPUT_MODES.get((outputIndex + 1) % OUTPUT_ENUM.length));
    });
  }

  @Override
  public int[] onCreateDrawableState(int extraSpace) {
    final int[] extra         = OUTPUT_ENUM[outputIndex];
    final int[] drawableState = super.onCreateDrawableState(extraSpace + extra.length);
    mergeDrawableStates(drawableState, extra);
    return drawableState;
  }

  @Override
  public void setOnClickListener(@Nullable OnClickListener l) {
    throw new UnsupportedOperationException("This View does not support custom click listeners.");
  }

  public void setIsHeadsetAvailable(boolean isHeadsetAvailable) {
    this.isHeadsetAvailable = isHeadsetAvailable;
    setAudioOutput(OUTPUT_MODES.get(outputIndex));
  }

  public void setAudioOutput(@NonNull WebRtcAudioOutput audioOutput) {
    int oldIndex = outputIndex;
    outputIndex  = resolveAudioOutputIndex(OUTPUT_MODES.indexOf(audioOutput), isHeadsetAvailable);

    if (oldIndex != outputIndex) {
      refreshDrawableState();
      notifyListener();
    }
  }

  public void setOnAudioOutputChangedListener(@Nullable OnAudioOutputChangedListener listener) {
    this.audioOutputChangedListener = listener;
  }

  private void showPicker() {
    RecyclerView rv = new RecyclerView(getContext());
    rv.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
    rv.setAdapter(new AudioOutputAdapter(this::setAudioOutputViaDialog, OUTPUT_MODES));

    picker = new AlertDialog.Builder(getContext())
                            .setView(rv)
                            .show();
  }

  private void hidePicker() {
    if (picker != null) {
      picker.dismiss();
      picker = null;
    }
  }

  @Override
  protected Parcelable onSaveInstanceState() {
    Parcelable parentState = super.onSaveInstanceState();
    Bundle     bundle      = new Bundle();

    bundle.putParcelable(STATE_PARENT, parentState);
    bundle.putInt(STATE_OUTPUT_INDEX, outputIndex);
    bundle.putBoolean(STATE_HEADSET_ENABLED, isHeadsetAvailable);
    return bundle;
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    if (state instanceof Bundle) {
      Bundle savedState = (Bundle) state;

      isHeadsetAvailable = savedState.getBoolean(STATE_HEADSET_ENABLED);
      setAudioOutput(OUTPUT_MODES.get(
          resolveAudioOutputIndex(savedState.getInt(STATE_OUTPUT_INDEX), isHeadsetAvailable))
      );

      super.onRestoreInstanceState(savedState.getParcelable(STATE_PARENT));
    } else {
      super.onRestoreInstanceState(state);
    }
  }

  private void notifyListener() {
    if (audioOutputChangedListener == null) return;

    audioOutputChangedListener.audioOutputChanged(OUTPUT_MODES.get(outputIndex));
  }

  private void setAudioOutputViaDialog(@NonNull WebRtcAudioOutput audioOutput) {
    setAudioOutput(audioOutput);
    hidePicker();
  }

  private static int resolveAudioOutputIndex(int desiredAudioOutputIndex, boolean isHeadsetAvailable) {
    if (isIllegalAudioOutputIndex(desiredAudioOutputIndex)) {
      throw new IllegalArgumentException("Unsupported index: " + desiredAudioOutputIndex);
    }
    if (isUnsupportedAudioOutput(desiredAudioOutputIndex, isHeadsetAvailable)) {
      return OUTPUT_MODES.indexOf(OUTPUT_FALLBACK);
    }
    return desiredAudioOutputIndex;
  }

  private static boolean isIllegalAudioOutputIndex(int desiredFlashIndex) {
    return desiredFlashIndex < 0 || desiredFlashIndex > OUTPUT_ENUM.length;
  }

  private static boolean isUnsupportedAudioOutput(int desiredAudioOutputIndex, boolean isHeadsetAvailable) {
    return OUTPUT_MODES.get(desiredAudioOutputIndex) == WebRtcAudioOutput.HEADSET && !isHeadsetAvailable;
  }

  public interface OnAudioOutputChangedListener {
    void audioOutputChanged(WebRtcAudioOutput audioOutput);
  }
}
