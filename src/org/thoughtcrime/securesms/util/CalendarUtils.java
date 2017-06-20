/*
 * Copyright (C) 2017 Fernando Garcia Alvarez
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thoughtcrime.securesms.util;

import java.util.Calendar;

/**
 * Utility class to work with calendars
 *
 * @author fercarcedo
 */

public class CalendarUtils {
  public static boolean afterHoursAndMinutes(Calendar firstCalendar, Calendar secondCalendar) {
    int firstCalendarHours = firstCalendar.get(Calendar.HOUR_OF_DAY);
    int firstCalendarMinutes = firstCalendar.get(Calendar.MINUTE);
    int secondCalendarHours = secondCalendar.get(Calendar.HOUR_OF_DAY);
    int secondCalendarMinutes = secondCalendar.get(Calendar.MINUTE);

    return (firstCalendarHours > secondCalendarHours)
                || (firstCalendarHours == secondCalendarHours && firstCalendarMinutes > secondCalendarMinutes);
  }

  public static boolean beforeHoursAndMinutes(Calendar firstCalendar, Calendar secondCalendar) {
    int firstCalendarHours = firstCalendar.get(Calendar.HOUR_OF_DAY);
    int firstCalendarMinutes = firstCalendar.get(Calendar.MINUTE);
    int secondCalendarHours = secondCalendar.get(Calendar.HOUR_OF_DAY);
    int secondCalendarMinutes = secondCalendar.get(Calendar.MINUTE);

    return (firstCalendarHours < secondCalendarHours)
                || (firstCalendarHours == secondCalendarHours && firstCalendarMinutes < secondCalendarMinutes);
  }

  public static Calendar createFromMillis(long millis) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(millis);

    return calendar;
  }
}
