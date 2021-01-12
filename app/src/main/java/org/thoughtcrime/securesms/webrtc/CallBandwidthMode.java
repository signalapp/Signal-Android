package org.thoughtcrime.securesms.webrtc;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.NetworkUtil;

/**
 * Represents the user's desired bandwidth mode for calls.
 */
public enum CallBandwidthMode {
  LOW_ALWAYS(0),
  HIGH_ON_WIFI(1),
  HIGH_ALWAYS(2);

  private final int code;

  CallBandwidthMode(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  public static CallBandwidthMode fromCode(int code) {
    switch (code) {
      case 1:
        return HIGH_ON_WIFI;
      case 2:
        return HIGH_ALWAYS;
      default:
        return LOW_ALWAYS;
    }
  }
}
