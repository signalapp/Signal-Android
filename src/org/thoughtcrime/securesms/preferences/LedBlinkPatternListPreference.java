/** 
 * Copyright (C) 2011 Whisper Systems
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

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Parcelable;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

/**
 * List preference for LED blink pattern notification.
 * 
 * @author Moxie Marlinspike
 */

public class LedBlinkPatternListPreference extends ListPreference implements OnSeekBarChangeListener {
	
  private Context context;
  private SeekBar seekBarOn;
  private SeekBar seekBarOff;

  private TextView seekBarOnLabel;
  private TextView seekBarOffLabel;
    
  private boolean dialogInProgress;

  public LedBlinkPatternListPreference(Context context) {
    super(context);
    this.context = context;
  }

  public LedBlinkPatternListPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  @Override
    protected void onDialogClosed(boolean positiveResult) {
    super.onDialogClosed(positiveResult);
        
    if (positiveResult) {
      String blinkPattern = PreferenceManager.getDefaultSharedPreferences(context).getString(ApplicationPreferencesActivity.LED_BLINK_PREF, "500,2000");
      if (blinkPattern.equals("custom")) showDialog();
    }
  }

  private void initializeSeekBarValues() {
    String patternString  = PreferenceManager.getDefaultSharedPreferences(context).getString(ApplicationPreferencesActivity.LED_BLINK_PREF_CUSTOM, "500,2000");
    String[] patternArray = patternString.split(",");
    seekBarOn.setProgress(Integer.parseInt(patternArray[0]));
    seekBarOff.setProgress(Integer.parseInt(patternArray[1]));
  }
    
  private void initializeDialog(View view) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setIcon(android.R.drawable.ic_dialog_info);
    builder.setTitle("Set Custom LED Blink Pattern");
    builder.setView(view);
    builder.setOnCancelListener(new CustomDialogCancelListener());
    builder.setNegativeButton(android.R.string.cancel, new CustomDialogCancelListener());
    builder.setPositiveButton(android.R.string.ok, new CustomDialogClickListener());
    builder.show();
  }
    
  private void showDialog() {
    LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View view               = inflater.inflate(R.layout.led_pattern_dialog, null);

    this.seekBarOn       = (SeekBar)view.findViewById(R.id.SeekBarOn);
    this.seekBarOff      = (SeekBar)view.findViewById(R.id.SeekBarOff);
    this.seekBarOnLabel  = (TextView)view.findViewById(R.id.SeekBarOnMsLabel);
    this.seekBarOffLabel = (TextView)view.findViewById(R.id.SeekBarOffMsLabel);

    this.seekBarOn.setOnSeekBarChangeListener(this);
    this.seekBarOff.setOnSeekBarChangeListener(this);

    initializeSeekBarValues();
    initializeDialog(view);
    dialogInProgress = true;
        
    dialogInProgress = true;
  }

  @Override
    protected void onRestoreInstanceState(Parcelable state) {
    super.onRestoreInstanceState(state);
    if (dialogInProgress) {
      showDialog();
    }
  }

  @Override
    protected View onCreateDialogView() {
    dialogInProgress = false;
    return super.onCreateDialogView();
  }

  public void onProgressChanged(SeekBar seekbar, int progress, boolean fromTouch) {
    if (seekbar.equals(seekBarOn)) {
      seekBarOnLabel.setText(Integer.toString(progress));
    } else if (seekbar.equals(seekBarOff)) {
      seekBarOffLabel.setText(Integer.toString(progress));
    }
  }

  public void onStartTrackingTouch(SeekBar seekBar) {
  }

  public void onStopTrackingTouch(SeekBar seekBar) {
  }

  private class CustomDialogCancelListener implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
    public void onClick(DialogInterface dialog, int which) {
      dialogInProgress = false;			
    }

    public void onCancel(DialogInterface dialog) {
      dialogInProgress = false;
    }   	
  }
    
  private class CustomDialogClickListener implements DialogInterface.OnClickListener {

    public void onClick(DialogInterface dialog, int which) {
      String pattern   = seekBarOnLabel.getText() + "," + seekBarOffLabel.getText();
      dialogInProgress = false;
            
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
      preferences.edit().putString(ApplicationPreferencesActivity.LED_BLINK_PREF_CUSTOM, pattern).commit();
      Toast.makeText(context, "Custom LED blink pattern set!", Toast.LENGTH_LONG).show();
    }
    	
  }
    
}
