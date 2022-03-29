package org.thoughtcrime.securesms.util;

import android.app.Application;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public class ExpirationUtilTest {

  private static int SECONDS_IN_WEEK   = (int) TimeUnit.DAYS.toSeconds(7);
  private static int SECONDS_IN_DAY    = (int) TimeUnit.DAYS.toSeconds(1);
  private static int SECONDS_IN_HOUR   = (int) TimeUnit.HOURS.toSeconds(1);
  private static int SECONDS_IN_MINUTE = (int) TimeUnit.MINUTES.toSeconds(1);

  private Context context;

  @Before
  public void setup() {
    context = ApplicationProvider.getApplicationContext();
  }

  @Test
  public void shouldFormatAsSeconds_whenEvenSecondUnderMinute() {
    assertEquals(1 + " second", ExpirationUtil.getExpirationDisplayValue(context, 1));
    for (int seconds = 2; seconds < 60; seconds++) {
      assertEquals(seconds + " seconds", ExpirationUtil.getExpirationDisplayValue(context, seconds));
    }
  }

  @Test
  public void shouldFormatAsMinutes_whenEvenMinuteUnderHour() {
    assertEquals(1 + " minute", ExpirationUtil.getExpirationDisplayValue(context, (int) TimeUnit.MINUTES.toSeconds(1)));
    for (int minutes = 2; minutes < 60; minutes++) {
      assertEquals(minutes + " minutes", ExpirationUtil.getExpirationDisplayValue(context, (int) TimeUnit.MINUTES.toSeconds(minutes)));
    }
  }

  @Test
  public void shouldFormatAsHours_whenEvenHourUnderDay() {
    assertEquals(1 + " hour", ExpirationUtil.getExpirationDisplayValue(context, (int) TimeUnit.HOURS.toSeconds(1)));
    for (int hours = 2; hours < 24; hours++) {
      assertEquals(hours + " hours", ExpirationUtil.getExpirationDisplayValue(context, (int) TimeUnit.HOURS.toSeconds(hours)));
    }
  }

  @Test
  public void shouldFormatAsDays_whenEvenDayUnderWeek() {
    assertEquals(1 + " day", ExpirationUtil.getExpirationDisplayValue(context, (int) TimeUnit.DAYS.toSeconds(1)));
    for (int days = 2; days < 7; days++) {
      assertEquals(days + " days", ExpirationUtil.getExpirationDisplayValue(context, (int) TimeUnit.DAYS.toSeconds(days)));
    }
  }

  @Test
  public void shouldFormatAsWeeks_whenEvenWeek() {
    assertEquals(1 + " week", ExpirationUtil.getExpirationDisplayValue(context, (int) TimeUnit.DAYS.toSeconds(7)));
    for (int weeks = 2; weeks < 52; weeks++) {
      assertEquals(weeks + " weeks", ExpirationUtil.getExpirationDisplayValue(context, (int) TimeUnit.DAYS.toSeconds(7 * weeks)));
    }
  }

  @Test
  public void shouldFormatAsBreakdown_whenLargerThanWeek() {
    assertEquals("1 week 1 day",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_WEEK + SECONDS_IN_DAY));

    assertEquals("1 week",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_WEEK + SECONDS_IN_HOUR));

    assertEquals("1 week",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_WEEK + SECONDS_IN_MINUTE));

    assertEquals("1 week",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_WEEK + 1));

    assertEquals("1 week 1 day",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_WEEK + SECONDS_IN_DAY + SECONDS_IN_HOUR));

    assertEquals("1 week 1 day",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_WEEK + SECONDS_IN_DAY + SECONDS_IN_HOUR + SECONDS_IN_MINUTE));

    assertEquals("1 week 1 day",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_WEEK + SECONDS_IN_DAY + SECONDS_IN_HOUR + SECONDS_IN_MINUTE + 1));

    assertEquals("1 week",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_WEEK + SECONDS_IN_HOUR + SECONDS_IN_MINUTE));

    assertEquals("1 week",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_WEEK + SECONDS_IN_HOUR + SECONDS_IN_MINUTE + 1));

    assertEquals("1 week",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_WEEK + SECONDS_IN_MINUTE + 1));
  }

  @Test
  public void shouldFormatAsBreakdown_whenLargerThanDay() {
    assertEquals("1 day 1 hour",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_DAY + SECONDS_IN_HOUR));

    assertEquals("1 day",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_DAY + SECONDS_IN_MINUTE));

    assertEquals("1 day",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_DAY + 1));
  }

  @Test
  public void shouldFormatAsBreakdown_whenLargerThanHour() {
    assertEquals("1 hour 1 minute",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_HOUR + SECONDS_IN_MINUTE));

    assertEquals("1 hour",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_HOUR + 1));
  }

  @Test
  public void shouldFormatAsBreakdown_whenLargerThanMinute() {
    assertEquals("1 minute 1 second",
                 ExpirationUtil.getExpirationDisplayValue(context, SECONDS_IN_MINUTE + 1));
  }
}
