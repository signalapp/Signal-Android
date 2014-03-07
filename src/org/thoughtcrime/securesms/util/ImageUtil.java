package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

public class ImageUtil {
  public static float getImageOrientation(Context context, Uri image) {
    Cursor cursor = context.getContentResolver().query(
        image, new String[] {MediaStore.Images.ImageColumns.ORIENTATION}, null, null, null);

    try {
      if (cursor.getCount() == 1) {
        cursor.moveToFirst();
        return cursor.getInt(0);
      }
    } finally {
      cursor.close();
    }
    return 0;
  }
}
