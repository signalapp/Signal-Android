package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;

import java.io.InputStream;

public class ContactPhotoFactory {

  private static final String TAG = ContactPhotoFactory.class.getSimpleName();
  
  public static ContactPhoto getLoadingPhoto() {
    return new TransparentContactPhoto();
  }

  public static ContactPhoto getDefaultContactPhoto(@Nullable String name) {
    if (!TextUtils.isEmpty(name)) return new GeneratedContactPhoto(name);
    else                          return new GeneratedContactPhoto("#");
  }

  public static ContactPhoto getResourceContactPhoto(@DrawableRes int resourceId) {
    return new ResourceContactPhoto(resourceId);
  }

  public static ContactPhoto getDefaultGroupPhoto() {
    return new ResourceContactPhoto(R.drawable.ic_group_white_24dp);
  }

  public static ContactPhoto getContactPhoto(Context context, Uri uri, String name) {
    try {
      InputStream inputStream = getContactPhotoStream(context, uri);
      int         targetSize  = context.getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size);

      if (inputStream != null) {
        return new BitmapContactPhoto(BitmapUtil.createScaledBitmap(inputStream, getContactPhotoStream(context, uri), targetSize, targetSize));
      }
    } catch (BitmapDecodingException e) {
      Log.w(TAG, e);
    }

    return getDefaultContactPhoto(name);
  }

  public static ContactPhoto getGroupContactPhoto(@Nullable byte[] avatar) {
    if (avatar == null) return getDefaultGroupPhoto();

    return new BitmapContactPhoto(BitmapFactory.decodeByteArray(avatar, 0, avatar.length));
  }

  private static InputStream getContactPhotoStream(Context context, Uri uri) {
    if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {
      return ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(), uri, true);
    } else {
      return ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(), uri);
    }
  }
}
