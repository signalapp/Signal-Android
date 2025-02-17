package org.thoughtcrime.securesms.webrtc.locks;

import android.os.PowerManager;

import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;

/**
 * Controls access to the proximity lock.
 * The proximity lock is not part of the public API.
 *
 * @author Stuart O. Anderson
*/
class ProximityLock {

  private static final String TAG = Log.tag(ProximityLock.class);

  private final PowerManager.WakeLock proximityLock;

  ProximityLock(PowerManager pm) {
    proximityLock = getProximityLock(pm);
  }

  private @Nullable PowerManager.WakeLock getProximityLock(PowerManager pm) {
    if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
      return pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "signal:proximity");
    } else {
      return null;
    }
  }

  public void acquire() {
    if (proximityLock == null || proximityLock.isHeld()) {
      return;
    }

    proximityLock.acquire();
  }

  public void release() {
    if (proximityLock == null || !proximityLock.isHeld()) {
      return;
    }

    proximityLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);

    Log.d(TAG, "Released proximity lock:" + proximityLock.isHeld());
  }
}
