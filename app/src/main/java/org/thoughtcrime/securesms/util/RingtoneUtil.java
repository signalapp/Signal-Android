package org.thoughtcrime.securesms.util;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Some custom ROMs and some Samsung Android 11 devices have quirks around accessing the default ringtone. This attempts to deal
 * with them with progressively worse approaches.
 */
public final class RingtoneUtil {

  private static final String TAG = Log.tag(RingtoneUtil.class);

  private RingtoneUtil() {}

  public static @Nullable Ringtone getRingtone(@NonNull Context context, @NonNull Uri uri) {
    Ringtone tone;
    try {
      tone = RingtoneManager.getRingtone(context, uri);
    } catch (SecurityException e) {
      Log.w(TAG, "Unable to get default ringtone due to permission", e);
      tone = RingtoneManager.getRingtone(context, RingtoneUtil.getActualDefaultRingtoneUri(context));
    }
    return tone;
  }

  public static @Nullable Uri getActualDefaultRingtoneUri(@NonNull Context context) {
    Log.i(TAG, "Attempting to get default ringtone directly via normal way");
    try {
      return RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE);
    } catch (SecurityException e) {
      Log.w(TAG, "Failed to get ringtone with first fallback approach", e);
    }

    Log.i(TAG, "Attempting to get default ringtone directly via reflection");
    String uriString   = getStringForUser(context.getContentResolver(), getUserId(context));
    Uri    ringtoneUri = uriString != null ? Uri.parse(uriString) : null;

    if (ringtoneUri != null && getUserIdFromAuthority(ringtoneUri.getAuthority(), getUserId(context)) == getUserId(context)) {
      ringtoneUri = getUriWithoutUserId(ringtoneUri);
    }

    return ringtoneUri;
  }

  @SuppressWarnings("JavaReflectionMemberAccess")
  @SuppressLint("DiscouragedPrivateApi")
  private static @Nullable String getStringForUser(@NonNull ContentResolver resolver, int userHandle) {
    try {
      Method getStringForUser = Settings.System.class.getMethod("getStringForUser", ContentResolver.class, String.class, int.class);
      return (String) getStringForUser.invoke(Settings.System.class, resolver, Settings.System.RINGTONE, userHandle);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      Log.w(TAG, "Unable to getStringForUser via reflection", e);
    }
    return null;
  }

  @SuppressWarnings("JavaReflectionMemberAccess")
  @SuppressLint("DiscouragedPrivateApi")
  private static int getUserId(@NonNull Context context) {
    try {
      Object userId = Context.class.getMethod("getUserId").invoke(context);
      if (userId instanceof Integer) {
        return (Integer) userId;
      } else {
        Log.w(TAG, "getUserId did not return an integer");
      }
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      Log.w(TAG, "Unable to getUserId via reflection", e);
    }
    return 0;
  }

  private static @Nullable Uri getUriWithoutUserId(@Nullable Uri uri) {
    if (uri == null) {
      return null;
    }
    Uri.Builder builder = uri.buildUpon();
    builder.authority(getAuthorityWithoutUserId(uri.getAuthority()));
    return builder.build();
  }

  private static @Nullable String getAuthorityWithoutUserId(@Nullable String auth) {
    if (auth == null) {
      return null;
    }
    int end = auth.lastIndexOf('@');
    return auth.substring(end + 1);
  }

  private static int getUserIdFromAuthority(@Nullable String authority, int defaultUserId) {
    if (authority == null) {
      return defaultUserId;
    }

    int end = authority.lastIndexOf('@');
    if (end == -1) {
      return defaultUserId;
    }

    String userIdString = authority.substring(0, end);
    try {
      return Integer.parseInt(userIdString);
    } catch (NumberFormatException e) {
      return defaultUserId;
    }
  }
}
