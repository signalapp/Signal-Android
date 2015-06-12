package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.ImageView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.makeramen.roundedimageview.RoundedDrawable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.LRUCache;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

public class ContactPhotoFactory {
  private static final String TAG = ContactPhotoFactory.class.getSimpleName();

  private static final ColorGenerator COLOR_GENERATOR = ColorGenerator.MATERIAL;
  private static final int            UNKNOWN_COLOR   = 0xff9E9E9E;

  private static final Object defaultPhotoLock      = new Object();
  private static final Object defaultGroupPhotoLock = new Object();
  private static final Object loadingPhotoLock      = new Object();

  private static Drawable defaultContactPhoto;
  private static Drawable defaultGroupContactPhoto;
  private static Drawable loadingPhoto;

  private static final Map<Uri,Bitmap> localUserContactPhotoCache =
      Collections.synchronizedMap(new LRUCache<Uri,Bitmap>(2));

  public static Drawable getLoadingPhoto(Context context) {
    synchronized (loadingPhotoLock) {
      if (loadingPhoto == null)
        loadingPhoto = RoundedDrawable.fromDrawable(context.getResources().getDrawable(android.R.color.transparent));

      return loadingPhoto;
    }
  }

  public static Drawable getDefaultContactPhoto(Context context, @Nullable String name) {
    int targetSize = context.getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size);

    if (name != null && !name.isEmpty()) {
      return TextDrawable.builder().beginConfig()
                         .width(targetSize)
                         .height(targetSize)
                         .endConfig()
                         .buildRound(String.valueOf(name.charAt(0)),
                                     COLOR_GENERATOR.getColor(name));
    }

    synchronized (defaultPhotoLock) {
      if (defaultContactPhoto == null)
        defaultContactPhoto = TextDrawable.builder().beginConfig()
                                          .width(targetSize)
                                          .height(targetSize)
                                          .endConfig()
                                          .buildRound("#", UNKNOWN_COLOR);

      return defaultContactPhoto;
    }
  }

  public static Drawable getDefaultGroupPhoto(Context context) {
    synchronized (defaultGroupPhotoLock) {
      if (defaultGroupContactPhoto == null) {
        Drawable        background = TextDrawable.builder().buildRound(" ", UNKNOWN_COLOR);
        RoundedDrawable foreground = (RoundedDrawable) RoundedDrawable.fromDrawable(context.getResources().getDrawable(R.drawable.ic_group_white_24dp));
        foreground.setScaleType(ImageView.ScaleType.CENTER);


        defaultGroupContactPhoto = new ExpandingLayerDrawable(new Drawable[] {background, foreground});
      }

      return defaultGroupContactPhoto;
    }
  }

  public static void clearCache() {
    localUserContactPhotoCache.clear();
  }

  public static Drawable getContactPhoto(Context context, Uri uri, String name) {
    final InputStream inputStream = getContactPhotoStream(context, uri);
    final int         targetSize  = context.getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size);

    if (inputStream != null) {
      try {
        return RoundedDrawable.fromBitmap(BitmapUtil.createScaledBitmap(inputStream,
                                                                        getContactPhotoStream(context, uri),
                                                                        targetSize,
                                                                        targetSize))
                              .setScaleType(ImageView.ScaleType.CENTER_CROP)
                              .setOval(true);
      } catch (BitmapDecodingException bde) {
        Log.w(TAG, bde);
      }
    }

    return getDefaultContactPhoto(context, name);
  }

  public static Drawable getGroupContactPhoto(Context context, @Nullable byte[] avatar) {
    if (avatar == null) return getDefaultGroupPhoto(context);

    return RoundedDrawable.fromBitmap(BitmapFactory.decodeByteArray(avatar, 0, avatar.length))
                          .setScaleType(ImageView.ScaleType.CENTER_CROP)
                          .setOval(true);
  }

  private static InputStream getContactPhotoStream(Context context, Uri uri) {
    if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {
      return ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(), uri, true);
    } else {
      return ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(), uri);
    }
  }

  private static class ExpandingLayerDrawable extends LayerDrawable {

    public ExpandingLayerDrawable(Drawable[] layers) {
      super(layers);
    }

    @Override
    public int getIntrinsicWidth() {
      return -1;
    }

    @Override
    public int getIntrinsicHeight() {
      return -1;
    }
  }
}
