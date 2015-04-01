/**
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.content.Context;

import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class SkipRegistrationInstrumentationTestCase extends RoutedInstrumentationTestCase {
  private static final String TAG = SkipRegistrationInstrumentationTestCase.class.getSimpleName();

  public SkipRegistrationInstrumentationTestCase() {
    super();
  }

  public SkipRegistrationInstrumentationTestCase(Context context) {
    super(context);
  }

  @Override
  public void initAppState() throws Exception {
    super.initAppState();
    TextSecurePreferences.setPromptedPushRegistration(getContext(), true);
  }

}
