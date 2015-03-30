package de.gdata.messaging.util;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;

/**
 * Created by jan on 23.02.15.
 */
public class PrivacyContentObserver extends ContentObserver {

  public PrivacyContentObserver(Handler handler) {
    super(handler);
  }

  @Override
  public void onChange(boolean selfChange) {
    this.onChange(selfChange, null);
  }

  @Override
  public void onChange(boolean selfChange, Uri uri) {
   GService.refreshPrivacyData(true);
  }
  @Override
  public boolean deliverSelfNotifications() {
    return true;
  }
}