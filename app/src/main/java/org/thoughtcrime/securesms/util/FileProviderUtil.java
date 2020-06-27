package org.thoughtcrime.securesms.util;


import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;

public class FileProviderUtil {

  private static final String AUTHORITY = "org.thoughtcrime.securesms.fileprovider";

  public static Uri getUriFor(@NonNull Context context, @NonNull File file) {
    if (Build.VERSION.SDK_INT >= 24) return FileProvider.getUriForFile(context, AUTHORITY, file);
    else                             return Uri.fromFile(file);
  }

  public static boolean isAuthority(@NonNull Uri uri) {
    return AUTHORITY.equals(uri.getAuthority());
  }

  public static boolean delete(@NonNull Context context, @NonNull Uri uri) {
    if (AUTHORITY.equals(uri.getAuthority())) {
      return context.getContentResolver().delete(uri, null, null) > 0;
    }
    return new File(uri.getPath()).delete();
  }
}
