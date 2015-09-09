package org.thoughtcrime.redphone.call;

import android.os.PowerManager;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Controls access to the proximity lock.
 * The proximity lock is not part of the public API.
 *
 * @author Stuart O. Anderson
*/
class ProximityLock {
  private final Method wakelockParameterizedRelease = getWakelockParamterizedReleaseMethod();
  private final PowerManager.WakeLock proximityLock;

  private static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;
  private static final int WAIT_FOR_PROXIMITY_NEGATIVE = 1;

  ProximityLock(PowerManager pm) {
    proximityLock = maybeGetProximityLock(pm);
  }

  private PowerManager.WakeLock maybeGetProximityLock(PowerManager pm) {
    /*try {
      PowerManager.WakeLock lock = pm.newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, "RedPhone Incall");
      if (lock != null) {
        return lock;
      }
    } catch (Throwable t) {
      Log.e("LockManager", "Failed to create proximity lock", t);
    }*/
    return null;
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
    boolean released = false;
    if (wakelockParameterizedRelease != null) {
      try {
        wakelockParameterizedRelease.invoke(proximityLock, new Integer(WAIT_FOR_PROXIMITY_NEGATIVE));
        released = true;
      } catch (IllegalAccessException e) {
        Log.d("LockManager", "Failed to invoke release method", e);
      } catch (InvocationTargetException e) {
        Log.d("LockManager", "Failed to invoke release method", e);
      }
    }

    if(!released) {
      proximityLock.release();
    }
    Log.d("LockManager", "Released proximity lock:" + proximityLock.isHeld());
  }

  private static Method getWakelockParamterizedReleaseMethod() {
    try {
      return PowerManager.WakeLock.class.getDeclaredMethod("release", Integer.TYPE);
    } catch (NoSuchMethodException e) {
      Log.d("LockManager", "Parameterized WakeLock release not available on this device.");
    }
    return null;
  }
}
