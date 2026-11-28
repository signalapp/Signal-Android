package org.signal.core.util;


import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.signal.core.util.logging.Log;

public class PlayServicesUtil {

  private static final String TAG = Log.tag(PlayServicesUtil.class);

  public static final String PLAY_SERVICES_PACKAGE = "com.google.android.gms";

  public enum PlayServicesStatus {
    SUCCESS,
    MISSING,
    NEEDS_UPDATE,
    TRANSIENT_ERROR
  }

  public static PlayServicesStatus getPlayServicesStatus(Context context) {
    int gcmStatus = 0;

    try {
      gcmStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
    } catch (Throwable t) {
      Log.w(TAG, t);
      return PlayServicesStatus.MISSING;
    }

    Log.i(TAG, "Play Services: " + gcmStatus);

    switch (gcmStatus) {
      case ConnectionResult.SUCCESS:
        return PlayServicesStatus.SUCCESS;
      case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
        try {
          ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(PLAY_SERVICES_PACKAGE, 0);

          if (applicationInfo != null && !applicationInfo.enabled) {
            return PlayServicesStatus.MISSING;
          }
        } catch (PackageManager.NameNotFoundException e) {
          Log.w(TAG, e);
        }

        return PlayServicesStatus.NEEDS_UPDATE;
      case ConnectionResult.SERVICE_DISABLED:
      case ConnectionResult.SERVICE_MISSING:
      case ConnectionResult.SERVICE_INVALID:
      case ConnectionResult.API_UNAVAILABLE:
      case ConnectionResult.SERVICE_MISSING_PERMISSION:
        return PlayServicesStatus.MISSING;
      default:
        return PlayServicesStatus.TRANSIENT_ERROR;
    }
  }

  /** Opens the system app info screen for Play Services, where the user can enable it. */
  public static void openPlayServicesSettings(@NonNull Context context) {
    try {
      context.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", PLAY_SERVICES_PACKAGE, null)));
    } catch (ActivityNotFoundException | SecurityException e) {
      Log.w(TAG, "Unable to open Play Services' app settings", e);
    }
  }
}
