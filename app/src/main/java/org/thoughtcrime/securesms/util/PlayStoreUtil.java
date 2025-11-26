package org.thoughtcrime.securesms.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.BuildConfig;

public final class PlayStoreUtil {

  private PlayStoreUtil() {}

  public static void openPlayStoreOrOurApkDownloadPage(@NonNull Context context) {
    if (BuildConfig.MANAGES_APP_UPDATES) {
      CommunicationActions.openBrowserLink(context, "https://signal.org/android/apk");
    } else {
      openPlayStore(context);
    }
  }

  public static void openPlayStoreHome(@NonNull Context context) {
    try {
      Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.android.vending"));
      intent.setPackage("com.android.vending");
      context.startActivity(intent);
    } catch (android.content.ActivityNotFoundException e) {
      CommunicationActions.openBrowserLink(context, "https://play.google.com/store/apps/");
    }
  }

  private static void openPlayStore(@NonNull Context context) {
    String packageName = context.getPackageName();

    try {
      context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
    } catch (ActivityNotFoundException e) {
      CommunicationActions.openBrowserLink(context, "https://play.google.com/store/apps/details?id=" + packageName);
    }
  }
}
