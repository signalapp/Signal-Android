package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

public class ServiceUtil {
  public static InputMethodManager getInputMethodManager(Context context) {
    return (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
  }

  public static WindowManager getWindowManager(Context context) {
    return (WindowManager) context.getSystemService(Activity.WINDOW_SERVICE);
  }

  public static ConnectivityManager getConnectivityManager(Context context) {
    return (ConnectivityManager) context.getSystemService(Activity.CONNECTIVITY_SERVICE);
  }

  public static NotificationManager getNotificationManager(Context context) {
    return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
  }
}
