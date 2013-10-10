package org.thoughtcrime.securesms.preferences;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NotificationsDatabase;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.AttributeSet;

public class SoundPreference extends android.preference.RingtonePreference {

  Context context;
  String notificationId;

  public SoundPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  public SoundPreference(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    this.context = context;
  }

  public void setNotificationId(long notificationId) {
    this.notificationId = String.valueOf(notificationId);
  }

  public Uri getSound() {
    Cursor c = DatabaseFactory.getNotificationsDatabase(context).getDefaultNotification();
    String sound = c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.SOUND));
    c.close();

    if (sound.equals("")) {
      return null;
    } else {
      return Uri.parse(sound);
    }
  }

  @Override
  protected Uri onRestoreRingtone() {
    return getSound();
  }

}
