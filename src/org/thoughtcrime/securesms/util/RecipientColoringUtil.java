package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;

/**
 * Utility class to map contacts to colors that identify them in group chats
 *
 * @author Lukas Barth
 */
public class RecipientColoringUtil {
  public static int getColorForRecipient(Recipient recipient, Context context) {
    long nameHash = recipient.getName().hashCode();
    Resources res = context.getResources();

    TypedArray colorArray = res.obtainTypedArray(R.array.contact_tinting_colors);
    int index = Math.abs((int)(nameHash % colorArray.length()));

    int colorForRecipient = colorArray.getColor(index, 0);
    colorArray.recycle();

    return colorForRecipient;
  }
}
