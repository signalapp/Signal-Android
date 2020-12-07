package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.content.DialogInterface;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WebRtcAudioOutputToggleButton extends AppCompatImageView {

  private static final String STATE_OUTPUT_INDEX      = "audio.output.toggle.state.output.index";
  private static final String STATE_HEADSET_ENABLED   = "audio.output.toggle.state.headset.enabled";
  private static final String STATE_HANDSET_ENABLED   = "audio.output.toggle.state.handset.enabled";
  private static final String STATE_PARENT            = "audio.output.toggle.state.parent";

  private static final int[]                   SPEAKER_OFF    = { R.attr.state_speaker_off };
  private static final int[]                   SPEAKER_ON     = { R.attr.state_speaker_on };
  private static final int[]                   OUTPUT_HANDSET = { R.attr.state_handset_selected };
  private static final int[]                   OUTPUT_SPEAKER = { R.attr.state_speaker_selected };
  private static final int[]                   OUTPUT_HEADSET = { R.attr.state_headset_selected };
  private static final int[][]                 OUTPUT_ENUM    = { SPEAKER_OFF, SPEAKER_ON, OUTPUT_HANDSET, OUTPUT_SPEAKER, OUTPUT_HEADSET };
  private static final List<WebRtcAudioOutput> OUTPUT_MODES   = Arrays.asList(WebRtcAudioOutput.HANDSET, WebRtcAudioOutput.SPEAKER, WebRtcAudioOutput.HANDSET, WebRtcAudioOutput.SPEAKER, WebRtcAudioOutput.HEADSET);

  private boolean                      isHeadsetAvailable;
  private boolean                      isHandsetAvailable;
  private int                          outputIndex;
  private OnAudioOutputChangedListener audioOutputChangedListener;
  private DialogInterface              picker;

  public WebRtcAudioOutputToggleButton(@NonNull Context context) {
    this(context, null);
  }

  public WebRtcAudioOutputToggleButton(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public WebRtcAudioOutputToggleButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    super.setOnClickListener((v) -> {
      List<WebRtcAudioOutput> availableModes = buildOutputModeList(isHeadsetAvailable, isHandsetAvailable);

      if (availableModes.size() > 2 || !isHandsetAvailable) showPicker(availableModes);
      else                                                  setAudioOutput(OUTPUT_MODES.get((outputIndex + 1) % OUTPUT_MODES.size()), true);
    });
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    hidePicker();
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

  public void setControlAvailability(boolean isHandsetAvailable, boolean isHeadsetAvailable) {
    this.isHandsetAvailable = isHandsetAvailable;
    this.isHeadsetAvailable = isHeadsetAvailable;
  }

  public void setAudioOutput(@NonNull WebRtcAudioOutput audioOutput, boolean notifyListener) {
    int oldIndex = outputIndex;
    outputIndex = resolveAudioOutputIndex(OUTPUT_MODES.lastIndexOf(audioOutput));

    if (oldIndex != outputIndex) {
      refreshDrawableState();

      if (notifyListener) {
        notifyListener();
      }
    }
  }

  public void setOnAudioOutputChangedListener(@Nullable OnAudioOutputChangedListener listener) {
    this.audioOutputChangedListener = listener;
  }

  private void showPicker(@NonNull List<WebRtcAudioOutput> availableModes) {
    RecyclerView       rv      = new RecyclerView(getContext());
    AudioOutputAdapter adapter = new AudioOutputAdapter(audioOutput -> {
                                                          setAudioOutput(audioOutput, true);
                                                          hidePicker();
                                                        },
                                                        availableModes);

    adapter.setSelectedOutput(OUTPUT_MODES.get(outputIndex));

    rv.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
    rv.setAdapter(adapter);

    picker = new AlertDialog.Builder(getContext(), R.style.Theme_Signal_AlertDialog_Dark_Cornered)
                            .setTitle(R.string.WebRtcAudioOutputToggle__audio_output)
                            .setView(rv)
                            .setCancelable(true)
                            .show();
  }

  @Override
  protected Parcelable onSaveInstanceState() {
    Parcelable parentState = super.onSaveInstanceState();
    Bundle     bundle      = new Bundle();

    bundle.putParcelable(STATE_PARENT, parentState);
    bundle.putInt(STATE_OUTPUT_INDEX, outputIndex);
    bundle.putBoolean(STATE_HEADSET_ENABLED, isHeadsetAvailable);
    bundle.putBoolean(STATE_HANDSET_ENABLED, isHandsetAvailable);
    return bundle;
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    if (state instanceof Bundle) {
      Bundle savedState = (Bundle) state;

      isHeadsetAvailable = savedState.getBoolean(STATE_HEADSET_ENABLED);
      isHandsetAvailable = savedState.getBoolean(STATE_HANDSET_ENABLED);

      setAudioOutput(OUTPUT_MODES.get(
          resolveAudioOutputIndex(savedState.getInt(STATE_OUTPUT_INDEX))),
          false
      );

      super.onRestoreInstanceState(savedState.getParcelable(STATE_PARENT));
    } else {
      super.onRestoreInstanceState(state);
    }
  }

  private void hidePicker() {
    if (picker != null) {
      picker.dismiss();
      picker = null;
    }
  }

  private void notifyListener() {
    if (audioOutputChangedListener == null) return;

    audioOutputChangedListener.audioOutputChanged(OUTPUT_MODES.get(outputIndex));
  }

  private static List<WebRtcAudioOutput> buildOutputModeList(boolean isHeadsetAvailable, boolean isHandsetAvailable) {
    List<WebRtcAudioOutput> modes = new ArrayList(3);

    modes.add(WebRtcAudioOutput.SPEAKER);

    if (isHeadsetAvailable) {
      modes.add(WebRtcAudioOutput.HEADSET);
    }

    if (isHandsetAvailable) {
      modes.add(WebRtcAudioOutput.HANDSET);
    }

    return modes;
  };

  private int resolveAudioOutputIndex(int desiredAudioOutputIndex) {
    if (isIllegalAudioOutputIndex(desiredAudioOutputIndex)) {
      throw new IllegalArgumentException("Unsupported index: " + desiredAudioOutputIndex);
    }
    if (isUnsupportedAudioOutput(desiredAudioOutputIndex, isHeadsetAvailable, isHandsetAvailable)) {
      if (!isHandsetAvailable) {
        return OUTPUT_MODES.lastIndexOf(WebRtcAudioOutput.SPEAKER);
      } else {
        return OUTPUT_MODES.indexOf(WebRtcAudioOutput.HANDSET);
      }
    }

    if (!isHeadsetAvailable) {
      return desiredAudioOutputIndex % 2;
    }

    return desiredAudioOutputIndex;
  }

  private static boolean isIllegalAudioOutputIndex(int desiredAudioOutputIndex) {
    return desiredAudioOutputIndex < 0 || desiredAudioOutputIndex > OUTPUT_MODES.size();
  }

  private static boolean isUnsupportedAudioOutput(int desiredAudioOutputIndex, boolean isHeadsetAvailable, boolean isHandsetAvailable) {
    return (OUTPUT_MODES.get(desiredAudioOutputIndex) == WebRtcAudioOutput.HEADSET && !isHeadsetAvailable) ||
           (OUTPUT_MODES.get(desiredAudioOutputIndex) == WebRtcAudioOutput.HANDSET && !isHandsetAvailable);
  }
}
