package org.thoughtcrime.securesms.util;


import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;

import java.io.File;

public class FileProviderUtil {

  private static final String AUTHORITY = "org.thoughtcrime.securesms.fileprovider";

  public static Uri getUriFor(@NonNull Context context, @NonNull File file) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) return FileProvider.getUriForFile(context, AUTHORITY, file);
    else                                                       return Uri.fromFile(file);
  }

}
