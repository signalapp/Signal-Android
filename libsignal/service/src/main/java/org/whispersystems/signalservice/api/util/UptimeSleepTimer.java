package org.whispersystems.signalservice.api.util;

import org.whispersystems.signalservice.api.util.SleepTimer;

/**
 * A simle sleep timer.  Since Thread.sleep is based on uptime
 * this will not work properly in low-power sleep modes, when
 * the CPU is suspended and uptime does not elapse.
 *
 */
public class UptimeSleepTimer implements SleepTimer {
  @Override
  public void sleep(long millis) throws InterruptedException {
    Thread.sleep(millis);
  }
}
