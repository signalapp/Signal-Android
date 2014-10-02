/**
 * Copyright (C) 2014 Whisper Systems
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;

import com.doomonafireball.betterpickers.hmspicker.HmsPickerBuilder;
import com.doomonafireball.betterpickers.hmspicker.HmsPickerDialogFragment;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.ApplicationPreferencesFragment;
import org.thoughtcrime.securesms.R;

/**
 * @author Lukas Barth
 */
public class TimeDeltaPreference  extends Preference {
  private static final int TOGGLE_ENABLE = 0;
  private static final int TOGGLE_DISABLE = 1;

  private boolean enableToggle;
  private String enableText;
  private String disableText;

  private void initializeResources(Context context, AttributeSet attrs) {
    int[] attrsArray = new int[] {
            R.attr.enable_toggle,
            R.attr.enable_text,
            R.attr.disable_text,
            R.attr.title,
    };

    TypedArray typedAttrs = context.obtainStyledAttributes(attrs, attrsArray);
    if (typedAttrs.hasValue(R.styleable.TimeDeltaPreference_enable_toggle)) {
      this.enableToggle = typedAttrs.getBoolean(R.styleable.TimeDeltaPreference_enable_toggle, true);
    } else {
      this.enableToggle = true;
    }

    if (typedAttrs.hasValue(R.styleable.TimeDeltaPreference_enable_text)) {
      this.enableText = typedAttrs.getString(R.styleable.TimeDeltaPreference_enable_text);
    } else {
      this.enableText = context.getResources().getString(R.string.timedeltapreference__default_enable);
    }

    if (typedAttrs.hasValue(R.styleable.TimeDeltaPreference_disable_text)) {
      this.disableText = typedAttrs.getString(R.styleable.TimeDeltaPreference_disable_text);
    } else {
      this.disableText = context.getResources().getString(R.string.timedeltapreference__default_disable);
    }

    typedAttrs.recycle();
  }

  public TimeDeltaPreference(Context context, AttributeSet attrs) {
    super(context, attrs);

    this.initializeResources(context, attrs);
  }

  private class ToggleListener implements DialogInterface.OnClickListener {
    public void onClick(DialogInterface dialog, int which) {
      if (which == TOGGLE_ENABLE) {
        TimeDeltaPreference.this.showSelector();
      } else {
        setValue(0);
      }
    }
  }

  protected void setValue(int seconds) {
    persistInt(seconds);
  }

  private class HmsDialogListener implements HmsPickerDialogFragment.HmsPickerDialogHandler {
    public void onDialogHmsSet(int reference, int hours, int minutes, int seconds) {
      int timeInSeconds = ((hours * 60) + minutes) * 60 + seconds;
      setValue(timeInSeconds);
    }
  }

  private void showToggle() {
    CharSequence[] choices = new CharSequence[2];
    choices[TOGGLE_ENABLE] = this.enableText;
    choices[TOGGLE_DISABLE] = this.disableText;

    AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
    builder.setTitle(this.getTitle())
            .setItems(choices, new ToggleListener());
    builder.show();
  }

  private void showSelector() {
    // This is ugly, but we need a SupportFragmentManager, and with Preferences, the
    // Context is always the (Preference)Activity.
    ApplicationPreferencesActivity activity = (ApplicationPreferencesActivity) getContext();
    HmsPickerBuilder builder = new HmsPickerBuilder()
            .setStyleResId(R.style.BetterPickersDialogFragment)
            .setFragmentManager(activity.getSupportFragmentManager())
            .addHmsPickerDialogHandler(new HmsDialogListener());
    builder.show();
  }

  @Override
  public void onClick() {
    if (this.enableToggle) {
      this.showToggle();
    } else {
      this.showSelector();
    }
  }
}
