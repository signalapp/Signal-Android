package org.thoughtcrime.securesms.lock;

import androidx.annotation.StringRes;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * Reminder intervals for Signal PINs.
 */
public class SignalPinReminders {

  private static final String TAG = Log.tag(SignalPinReminders.class);

  private static final long ONE_DAY     = TimeUnit.DAYS.toMillis(1);
  private static final long THREE_DAYS  = TimeUnit.DAYS.toMillis(3);
  private static final long ONE_WEEK    = TimeUnit.DAYS.toMillis(7);
  private static final long TWO_WEEKS   = TimeUnit.DAYS.toMillis(14);
  private static final long FOUR_WEEKS  = TimeUnit.DAYS.toMillis(28);

  private static final NavigableSet<Long> INTERVALS = new TreeSet<Long>() {{
    add(ONE_DAY);
    add(THREE_DAYS);
    add(ONE_WEEK);
    add(TWO_WEEKS);
    add(FOUR_WEEKS);
  }};

  private static final Map<Long, Integer> STRINGS = new HashMap<Long, Integer>() {{
    put(ONE_DAY, R.string.SignalPinReminders_well_remind_you_again_tomorrow);
    put(THREE_DAYS, R.string.SignalPinReminders_well_remind_you_again_in_a_few_days);
    put(ONE_WEEK, R.string.SignalPinReminders_well_remind_you_again_in_a_week);
    put(TWO_WEEKS, R.string.SignalPinReminders_well_remind_you_again_in_a_couple_weeks);
    put(FOUR_WEEKS, R.string.SignalPinReminders_well_remind_you_again_in_a_month);
  }};

  private static final Map<Long, Integer> SKIP_STRINGS = new HashMap<Long, Integer>() {{
    put(ONE_DAY, R.string.SignalPinReminders__well_remind_you_again_tomorrow);
    put(THREE_DAYS, R.string.SignalPinReminders__well_remind_you_again_in_a_few_days);
    put(ONE_WEEK, R.string.SignalPinReminders__well_remind_you_again_in_a_week);
    put(TWO_WEEKS, R.string.SignalPinReminders__well_remind_you_again_in_a_couple_weeks);
    put(FOUR_WEEKS, R.string.SignalPinReminders__well_remind_you_again_in_a_month);
  }};

  public static final long INITIAL_INTERVAL = INTERVALS.first();

  public static long getNextInterval(long currentInterval) {
    Long next = INTERVALS.higher(currentInterval);
    return next != null ? next : INTERVALS.last();
  }

  public static long getPreviousInterval(long currentInterval) {
    Long previous = INTERVALS.lower(currentInterval);
    return previous != null ? previous : INTERVALS.first();
  }

  public static @StringRes int getReminderString(long interval) {
    Integer stringRes = STRINGS.get(interval);

    if (stringRes != null) {
      return stringRes;
    } else {
      Log.w(TAG, "Couldn't find a string for interval " + interval);
      return R.string.SignalPinReminders_well_remind_you_again_later;
    }
  }

  public static @StringRes int getSkipReminderString(long interval) {
    Integer stringRes = SKIP_STRINGS.get(interval);

    if (stringRes != null) {
      return stringRes;
    } else {
      Log.w(TAG, "Couldn't find a string for interval " + interval);
      return R.string.SignalPinReminders__well_remind_you_again_later;
    }
  }
}
