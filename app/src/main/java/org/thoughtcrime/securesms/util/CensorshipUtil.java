package org.thoughtcrime.securesms.util;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;

public final class CensorshipUtil {

  private CensorshipUtil() {}

  public static boolean isCensored(@NonNull Context context) {
    return new SignalServiceNetworkAccess(context).isCensored(context);
  }
}
