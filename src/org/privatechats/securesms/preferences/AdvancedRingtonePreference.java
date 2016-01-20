package org.privatechats.securesms.preferences;

import android.content.Context;
import android.net.Uri;
import android.preference.RingtonePreference;
import android.util.AttributeSet;


public class AdvancedRingtonePreference extends RingtonePreference {

  private Uri currentRingtone;

  public AdvancedRingtonePreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }
  public AdvancedRingtonePreference(Context context, AttributeSet attrs) {
    super(context, attrs);
  }
  public AdvancedRingtonePreference(Context context) {
    super(context);
  }

  @Override
  protected Uri onRestoreRingtone() {
    return currentRingtone;
  }

  public void setCurrentRingtone(Uri uri) {
    currentRingtone = uri;
  }
}
