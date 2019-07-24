package org.thoughtcrime.securesms.components.webrtc;


import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import com.tomergoldst.tooltips.ToolTip;
import com.tomergoldst.tooltips.ToolTipsManager;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AccessibleToggleButton;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

public class WebRtcCallControls extends LinearLayout {

  private static final String TAG = WebRtcCallControls.class.getSimpleName();

  private AccessibleToggleButton audioMuteButton;
  private AccessibleToggleButton videoMuteButton;
  private AccessibleToggleButton speakerButton;
  private AccessibleToggleButton bluetoothButton;
  private AccessibleToggleButton cameraFlipButton;
  private boolean                cameraFlipAvailable;

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public WebRtcCallControls(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public WebRtcCallControls(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  public WebRtcCallControls(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public WebRtcCallControls(Context context) {
    super(context);
    initialize();
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.webrtc_call_controls, this, true);

    this.speakerButton   = ViewUtil.findById(this, R.id.speakerButton);
    this.bluetoothButton = ViewUtil.findById(this, R.id.bluetoothButton);
    this.audioMuteButton = ViewUtil.findById(this, R.id.muteButton);
    this.videoMuteButton = ViewUtil.findById(this, R.id.video_mute_button);
    this.cameraFlipButton = ViewUtil.findById(this, R.id.camera_flip_button);
  }

  public void setAudioMuteButtonListener(final MuteButtonListener listener) {
    audioMuteButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        listener.onToggle(b);
      }
    });
  }

  public void setVideoMuteButtonListener(final MuteButtonListener listener) {
    videoMuteButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        boolean videoMuted = !isChecked;
        listener.onToggle(videoMuted);
        cameraFlipButton.setVisibility(!videoMuted && cameraFlipAvailable ? View.VISIBLE : View.GONE);
      }
    });
  }

  public void setCameraFlipButtonListener(final CameraFlipButtonListener listener) {
    cameraFlipButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        listener.onToggle();
        updateCameraFlipIcon(isChecked);
        cameraFlipButton.setEnabled(false);
      }
    });
  }

  public void setSpeakerButtonListener(final SpeakerButtonListener listener) {
    speakerButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        listener.onSpeakerChange(isChecked);
        updateAudioState(bluetoothButton.getVisibility() == View.VISIBLE);
      }
    });
  }

  public void setBluetoothButtonListener(final BluetoothButtonListener listener) {
    bluetoothButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        listener.onBluetoothChange(isChecked);
        updateAudioState(true);
      }
    });
  }

  public void updateAudioState(boolean isBluetoothAvailable) {
    AudioManager audioManager = ServiceUtil.getAudioManager(getContext());

    if (!isBluetoothAvailable) {
      bluetoothButton.setVisibility(View.GONE);
    } else {
      bluetoothButton.setVisibility(View.VISIBLE);
    }

    if (audioManager.isBluetoothScoOn()) {
      bluetoothButton.setChecked(true, false);
      speakerButton.setChecked(false, false);
    } else if (audioManager.isSpeakerphoneOn()) {
      speakerButton.setChecked(true, false);
      bluetoothButton.setChecked(false, false);
    } else {
      speakerButton.setChecked(false, false);
      bluetoothButton.setChecked(false, false);
    }
  }

  public boolean isVideoEnabled() {
    return videoMuteButton.isChecked();
  }

  public void setVideoEnabled(boolean enabled) {
    videoMuteButton.setChecked(enabled, false);
  }

  public void setVideoAvailable(boolean available) {
    videoMuteButton.setVisibility(available ? VISIBLE : GONE);
  }

  public void setCameraFlipButtonEnabled(boolean enabled) {
    cameraFlipButton.setChecked(enabled, false);
    updateCameraFlipIcon(cameraFlipButton.isChecked());
  }

  private void updateCameraFlipIcon(boolean isChecked) {
    cameraFlipButton.setBackgroundResource(isChecked ? R.drawable.webrtc_camera_front_button
                                                     : R.drawable.webrtc_camera_rear_button);
  }

  public void setCameraFlipAvailable(boolean available) {
    cameraFlipAvailable = available;
    cameraFlipButton.setVisibility(cameraFlipAvailable && isVideoEnabled() ? View.VISIBLE : View.GONE);
  }

  public void setCameraFlipClickable(boolean clickable) {
    setControlEnabled(cameraFlipButton, clickable);
  }

  public void setMicrophoneEnabled(boolean enabled) {
    audioMuteButton.setChecked(!enabled, false);
  }

  public void setControlsEnabled(boolean enabled) {
    setControlEnabled(speakerButton, enabled);
    setControlEnabled(bluetoothButton, enabled);
    setControlEnabled(videoMuteButton, enabled);
    setControlEnabled(cameraFlipButton, enabled);
    setControlEnabled(audioMuteButton, enabled);
  }

  private void setControlEnabled(@NonNull View view, boolean enabled) {
    if (enabled) {
      view.setAlpha(1.0f);
      view.setEnabled(true);
    } else {
      view.setAlpha(0.3f);
      view.setEnabled(false);
    }
  }

  public void displayVideoTooltip(ViewGroup viewGroup) {
    if (videoMuteButton.getVisibility() == VISIBLE) {
      final ToolTipsManager toolTipsManager = new ToolTipsManager();

      ToolTip toolTip = new ToolTip.Builder(getContext(), videoMuteButton, viewGroup,
                                            getContext().getString(R.string.WebRtcCallControls_tap_to_enable_your_video),
                                            ToolTip.POSITION_BELOW).build();
      toolTipsManager.show(toolTip);

      videoMuteButton.postDelayed(() -> toolTipsManager.findAndDismiss(videoMuteButton), 4000);
    }
  }

  public static interface MuteButtonListener {
    public void onToggle(boolean isMuted);
  }

  public static interface CameraFlipButtonListener {
    public void onToggle();
  }

  public static interface SpeakerButtonListener {
    public void onSpeakerChange(boolean isSpeaker);
  }

  public static interface BluetoothButtonListener {
    public void onBluetoothChange(boolean isBluetooth);
  }





}
