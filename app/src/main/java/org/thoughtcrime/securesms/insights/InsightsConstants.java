package org.thoughtcrime.securesms.insights;

import java.util.concurrent.TimeUnit;

public final class InsightsConstants {

  public static final long PERIOD_IN_DAYS   = 7L;
  public static final long PERIOD_IN_MILLIS = TimeUnit.DAYS.toMillis(PERIOD_IN_DAYS);

  private InsightsConstants() {
  }

}
