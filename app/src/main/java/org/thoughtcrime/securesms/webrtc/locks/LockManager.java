package org.thoughtcrime.securesms.webrtc.locks;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.sensors.Orientation;

/**
 * Maintains wake lock state.
 *
 * @author Stuart O. Anderson
 */
public class LockManager {

  private static final String TAG = Log.tag(LockManager.class);

  private final PowerManager.WakeLock        fullLock;
  private final PowerManager.WakeLock        partialLock;
  private final WifiManager.WifiLock         wifiLock;
  private final ProximityLock                proximityLock;


  private PhoneState  phoneState        = PhoneState.IDLE;
  private Orientation orientation       = Orientation.PORTRAIT_BOTTOM_EDGE;
  private boolean     proximityDisabled = false;

  public enum PhoneState {
    IDLE,
    PROCESSING,  //used when the phone is active but before the user should be alerted.
    INTERACTIVE,
    IN_CALL,
    IN_HANDS_FREE_CALL,
    IN_VIDEO
  }

  private enum LockState {
    FULL,
    PARTIAL,
    SLEEP,
    PROXIMITY
  }

  public LockManager(Context context) {
    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    fullLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "signal:full");
    partialLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "signal:partial");
    proximityLock = new ProximityLock(pm);

    WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "signal:wifi");

    fullLock.setReferenceCounted(false);
    partialLock.setReferenceCounted(false);
    wifiLock.setReferenceCounted(false);
  }

  private void updateInCallLockState() {
    if (orientation == Orientation.PORTRAIT_BOTTOM_EDGE && !proximityDisabled) {
      setLockState(LockState.PROXIMITY);
    } else {
      setLockState(LockState.FULL);
    }
  }

  public void updateOrientation(@NonNull Orientation orientation) {
    Log.d(TAG, "Update orientation: " + orientation);
    this.orientation = orientation;

    if (phoneState == PhoneState.IN_CALL || phoneState == PhoneState.IN_VIDEO) {
      updateInCallLockState();
    }
  }

  public void updatePhoneState(PhoneState state) {
    this.phoneState = state;

    switch(state) {
      case IDLE:
        setLockState(LockState.SLEEP);
        break;
      case PROCESSING:
        setLockState(LockState.PARTIAL);
        break;
      case INTERACTIVE:
        setLockState(LockState.FULL);
        break;
      case IN_HANDS_FREE_CALL:
        setLockState(LockState.PARTIAL);
        proximityDisabled = true;
        break;
      case IN_VIDEO:
        proximityDisabled = true;
        updateInCallLockState();
        break;
      case IN_CALL:
        proximityDisabled = false;
        updateInCallLockState();
        break;
    }
  }

  private synchronized void setLockState(LockState newState) {
    switch(newState) {
      case FULL:
        fullLock.acquire();
        partialLock.acquire();
        wifiLock.acquire();
        proximityLock.release();
        break;
      case PARTIAL:
        partialLock.acquire();
        wifiLock.acquire();
        fullLock.release();
        proximityLock.release();
        break;
      case SLEEP:
        fullLock.release();
        partialLock.release();
        wifiLock.release();
        proximityLock.release();
        break;
      case PROXIMITY:
        partialLock.acquire();
        proximityLock.acquire();
        wifiLock.acquire();
        fullLock.release();
        break;
      default:
        throw new IllegalArgumentException("Unhandled Mode: " + newState);
    }
    Log.d(TAG, "Entered Lock State: " + newState);
  }
}
