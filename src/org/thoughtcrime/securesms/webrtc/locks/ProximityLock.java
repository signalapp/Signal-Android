package org.thoughtcrime.securesms.webrtc.locks;

import android.os.Build;
import android.os.PowerManager;
import org.thoughtcrime.securesms.logging.Log;

import org.whispersystems.libsignal.util.guava.Optional;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Controls access to the proximity lock.
 * The proximity lock is not part of the public API.
 *
 * @author Stuart O. Anderson
*/
class ProximityLock {

  private static final String TAG = ProximityLock.class.getSimpleName();

  private final Method wakelockParameterizedRelease = getWakelockParamterizedReleaseMethod();
  private final Optional<PowerManager.WakeLock> proximityLock;

  private static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;
  private static final int WAIT_FOR_PROXIMITY_NEGATIVE    = 1;

  ProximityLock(PowerManager pm) {
    proximityLock = getProximityLock(pm);
  }

  private Optional<PowerManager.WakeLock> getProximityLock(PowerManager pm) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
        return Optional.fromNullable(pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                                                    "Signal Proximity Lock"));
      } else {
        return Optional.absent();
      }
    } else {
      try {
        return Optional.fromNullable(pm.newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, "RedPhone Incall"));
      } catch (Throwable t) {
        Log.e(TAG, "Failed to create proximity lock", t);
        return Optional.absent();
      }
    }
  }

  public void acquire() {
    if (!proximityLock.isPresent() || proximityLock.get().isHeld()) {
      return;
    }

    proximityLock.get().acquire();
  }

  public void release() {
    if (!proximityLock.isPresent() || !proximityLock.get().isHeld()) {
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      proximityLock.get().release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
    } else {
      boolean released = false;

      if (wakelockParameterizedRelease != null) {
        try {
          wakelockParameterizedRelease.invoke(proximityLock.get(), WAIT_FOR_PROXIMITY_NEGATIVE);
          released = true;
        } catch (IllegalAccessException e) {
          Log.w(TAG, e);
        } catch (InvocationTargetException e) {
          Log.w(TAG, e);
        }
      }

      if (!released) {
        proximityLock.get().release();
      }
    }

    Log.d(TAG, "Released proximity lock:" + proximityLock.get().isHeld());
  }

  private static Method getWakelockParamterizedReleaseMethod() {
    try {
      return PowerManager.WakeLock.class.getDeclaredMethod("release", Integer.TYPE);
    } catch (NoSuchMethodException e) {
      Log.d(TAG, "Parameterized WakeLock release not available on this device.");
    }
    return null;
  }
}
