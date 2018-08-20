package org.thoughtcrime.securesms.webrtc.locks;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.provider.Settings;
import org.thoughtcrime.securesms.logging.Log;

/**
 * Maintains wake lock state.
 *
 * @author Stuart O. Anderson
 */
public class LockManager {

  private static final String TAG = LockManager.class.getSimpleName();

  private final PowerManager.WakeLock        fullLock;
  private final PowerManager.WakeLock        partialLock;
  private final WifiManager.WifiLock         wifiLock;
  private final ProximityLock                proximityLock;

  private final AccelerometerListener accelerometerListener;
  private final boolean               wifiLockEnforced;


  private int orientation = AccelerometerListener.ORIENTATION_UNKNOWN;
  private boolean proximityDisabled = false;

  public enum PhoneState {
    IDLE,
    PROCESSING,  //used when the phone is active but before the user should be alerted.
    INTERACTIVE,
    IN_CALL,
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
    fullLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "RedPhone Full");
    partialLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RedPhone Partial");
    proximityLock = new ProximityLock(pm);

    WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RedPhone Wifi");

    fullLock.setReferenceCounted(false);
    partialLock.setReferenceCounted(false);
    wifiLock.setReferenceCounted(false);

    accelerometerListener = new AccelerometerListener(context, new AccelerometerListener.OrientationListener() {
      @Override
      public void orientationChanged(int newOrientation) {
        orientation = newOrientation;
        Log.d(TAG, "Orentation Update: " + newOrientation);
        updateInCallLockState();
      }
    });

    wifiLockEnforced = isWifiPowerActiveModeEnabled(context);
  }

  private boolean isWifiPowerActiveModeEnabled(Context context) {
    int wifi_pwr_active_mode = Settings.Secure.getInt(context.getContentResolver(), "wifi_pwr_active_mode", -1);
    Log.d(TAG, "Wifi Activity Policy: " + wifi_pwr_active_mode);

    if (wifi_pwr_active_mode == 0) {
      return false;
    }

    return true;
  }

  private void updateInCallLockState() {
    if (orientation != AccelerometerListener.ORIENTATION_HORIZONTAL && wifiLockEnforced && !proximityDisabled) {
      setLockState(LockState.PROXIMITY);
    } else {
      setLockState(LockState.FULL);
    }
  }

  public void updatePhoneState(PhoneState state) {
    switch(state) {
      case IDLE:
        setLockState(LockState.SLEEP);
        accelerometerListener.enable(false);
        break;
      case PROCESSING:
        setLockState(LockState.PARTIAL);
        accelerometerListener.enable(false);
        break;
      case INTERACTIVE:
        setLockState(LockState.FULL);
        accelerometerListener.enable(false);
        break;
      case IN_VIDEO:
        proximityDisabled = true;
        accelerometerListener.enable(false);
        updateInCallLockState();
        break;
      case IN_CALL:
        proximityDisabled = false;
        accelerometerListener.enable(true);
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
