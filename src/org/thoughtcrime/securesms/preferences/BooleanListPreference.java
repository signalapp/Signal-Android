/**
 * Copyright (C) 2017 Whisper Systems
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
package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

import org.thoughtcrime.securesms.R;

/**
 * List preference that disables dependents when set to "none", similar to a CheckBoxPreference.
 *
 * @author Taylor Kline
 */

public class BooleanListPreference extends ListPreference {

  public BooleanListPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public BooleanListPreference(Context context) {
    super(context);
  }

  @Override
  public void setValue(String value) {
    CharSequence oldEntry = getEntry();
    super.setValue(value);
    CharSequence newEntry = getEntry();
    if (oldEntry != newEntry) {
      notifyDependencyChange(shouldDisableDependents());
    }
  }

  @Override
  public boolean shouldDisableDependents() {
    CharSequence newEntry = getEntry();
    String noneEntry = getContext().getString(R.string.preferences__none);
    boolean shouldDisable = newEntry.equals(noneEntry);
    return shouldDisable || super.shouldDisableDependents();
  }
}
