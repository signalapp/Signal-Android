package org.thoughtcrime.securesms.components.webrtc;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

public enum WebRtcAudioOutput {
  HANDSET(R.string.WebRtcAudioOutputToggle__phone_earpiece, R.drawable.ic_handset_solid_24),
  SPEAKER(R.string.WebRtcAudioOutputToggle__speaker, R.drawable.symbol_speaker_fill_white_24),
  BLUETOOTH_HEADSET(R.string.WebRtcAudioOutputToggle__bluetooth, R.drawable.symbol_speaker_bluetooth_fill_white_24),
  WIRED_HEADSET(R.string.WebRtcAudioOutputToggle__wired_headset, R.drawable.symbol_headphones_filed_24);

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
