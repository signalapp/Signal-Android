package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.TypedValue;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.PartAuthority;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.property.Photo;

public class VcardUtil {
  public static String getVcardFormattedName(String card) {
    StringBuilder builder = new StringBuilder();
    List<VCard> vCards = Ezvcard.parse(card).all();
    for (VCard vCard : vCards) {
      String name = vCard.getFormattedName().getValue();
      if (builder.length() > 0) {
        builder.append('\n');
      }
      builder.append(name);
    }
    return builder.toString();
  }

  public static SpannableStringBuilder getVcardDisplayText(Context context, String card, boolean isOutgoing) {
    Drawable contactDrawable = null;
    int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, context.getResources().getDisplayMetrics());
    SpannableStringBuilder builder = new SpannableStringBuilder();
    List<VCard> vCards = Ezvcard.parse(card).all();
    for (VCard vCard : vCards) {
      String name = vCard.getFormattedName().getValue();
      SpannableString ss = new SpannableString("  " + name);

      Drawable d;
      List<Photo> photos = vCard.getPhotos();
      if (!photos.isEmpty()) {
        byte[] data = photos.get(0).getData();
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        d = new BitmapDrawable(bitmap);
      } else {
        if (contactDrawable == null) {
          contactDrawable = context.getResources().getDrawable(isOutgoing ?
                  R.drawable.ic_account_box_light :
                  R.drawable.ic_account_box_dark);
        }
        d = contactDrawable;
      }
      d.setBounds(0, 0, size, size);
      ImageSpan span = new ImageSpan(d, ImageSpan.ALIGN_BOTTOM);
      ss.setSpan(span, 0, 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);

      if (builder.length() > 0) {
        builder.append('\n');
      }
      builder.append(ss);
    }

    return builder;
  }

  public static String getVcardAttachment(Context context, Uri uri) {
    try {
      try (InputStream is = PartAuthority.getAttachmentStream(context, uri)) {
        return Util.readFullyAsString(is);
      }
    } catch (IOException e) {
      Log.w("vCard", e);
      return null;
    }
  }
}
