package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.preference.RingtonePreference;
import android.support.annotation.RequiresApi;
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

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public AdvancedRingtonePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  protected Uri onRestoreRingtone() {
    if (currentRingtone == null) return super.onRestoreRingtone();
    else                         return currentRingtone;
  }

  public void setCurrentRingtone(Uri uri) {
    currentRingtone = uri;
  }
}
