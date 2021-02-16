package org.thoughtcrime.securesms.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

public final class UriUtil {

  /**
   * Ensures that an external URI is valid and doesn't contain any references to internal files or
   * any other trickiness.
   */
  public static boolean isValidExternalUri(@NonNull Context context, @NonNull Uri uri) {
    if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
      try {
        File file = new File(uri.getPath());

        return file.getCanonicalPath().equals(file.getPath()) &&
               !file.getCanonicalPath().startsWith("/data")   &&
               !file.getCanonicalPath().contains(context.getPackageName());
      } catch (IOException e) {
        return false;
      }
    } else {
      return true;
    }
  }
}
