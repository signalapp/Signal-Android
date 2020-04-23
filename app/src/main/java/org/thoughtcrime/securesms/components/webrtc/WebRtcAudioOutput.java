package org.thoughtcrime.securesms.components.webrtc;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

public enum WebRtcAudioOutput {
  HANDSET(R.string.WebRtcAudioOutputToggle__phone, R.drawable.ic_phone_right_black_28),
  SPEAKER(R.string.WebRtcAudioOutputToggle__speaker, R.drawable.ic_speaker_solid_black_28),
  HEADSET(R.string.WebRtcAudioOutputToggle__bluetooth, R.drawable.ic_speaker_bt_solid_black_28);

  private final @StringRes int labelRes;
  private final @DrawableRes int iconRes;

  WebRtcAudioOutput(@StringRes int labelRes, @DrawableRes int iconRes) {
    this.labelRes = labelRes;
    this.iconRes = iconRes;
  }

  public int getIconRes() {
    return iconRes;
  }

  public int getLabelRes() {
    return labelRes;
  }
}
