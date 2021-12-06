package com.google.android.gms.common;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;

public class GoogleApiAvailability {
  public static GoogleApiAvailability getInstance() {
    return new GoogleApiAvailability();
  }

  public int isGooglePlayServicesAvailable(Context context) {
    return ConnectionResult.SERVICE_MISSING;
  }

  public Dialog getErrorDialog(Activity activity, int errorCode, int requestCode) {
    return null;
  }
}
