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
import android.graphics.drawable.GradientDrawable;
import android.preference.ListPreference;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;

/**
 * List preference that disables dependents when set to "none", similar to a CheckBoxPreference.
 *
 * @author Taylor Kline
 */

public class LEDColorListPreference extends ListPreference {

  private static final String TAG = LEDColorListPreference.class.getSimpleName();

  private ImageView colorImageView;

  public LEDColorListPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWidgetLayoutResource(R.layout.led_color_preference_widget);
  }

  public LEDColorListPreference(Context context) {
    super(context);
    setWidgetLayoutResource(R.layout.led_color_preference_widget);
  }

  @Override
  public void setValue(String value) {
    CharSequence oldEntry = getEntry();
    super.setValue(value);
    CharSequence newEntry = getEntry();
    if (oldEntry != newEntry) {
      notifyDependencyChange(shouldDisableDependents());
    }

    if (value != null) setPreviewColor(value);
  }

  @Override
  public boolean shouldDisableDependents() {
    CharSequence newEntry = getValue();
    boolean shouldDisable = newEntry.equals("none");
    return shouldDisable || super.shouldDisableDependents();
  }

  @Override
  protected void onBindView(View view) {
    super.onBindView(view);
    this.colorImageView = (ImageView)view.findViewById(R.id.color_view);
    setPreviewColor(getValue());
  }

  @Override
  public void setSummary(CharSequence summary) {
    super.setSummary(null);
  }

  private void setPreviewColor(@NonNull String value) {
    int color;

    switch (value) {
      case "green":   color = getContext().getResources().getColor(R.color.green_500);   break;
      case "red":     color = getContext().getResources().getColor(R.color.red_500);     break;
      case "blue":    color = getContext().getResources().getColor(R.color.blue_500);    break;
      case "yellow":  color = getContext().getResources().getColor(R.color.yellow_500);  break;
      case "cyan":    color = getContext().getResources().getColor(R.color.cyan_500);    break;
      case "magenta": color = getContext().getResources().getColor(R.color.pink_500);    break;
      case "white":   color = getContext().getResources().getColor(R.color.white);       break;
      default:        color = getContext().getResources().getColor(R.color.transparent); break;
    }

    if (colorImageView != null) {
      GradientDrawable drawable = new GradientDrawable();
      drawable.setShape(GradientDrawable.OVAL);
      drawable.setColor(color);

      colorImageView.setImageDrawable(drawable);
    }
  }



}
