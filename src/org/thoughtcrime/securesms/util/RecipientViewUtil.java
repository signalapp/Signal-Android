package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.Contacts.Intents;
import android.provider.ContactsContract.QuickContact;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

public class RecipientViewUtil {
  public static CharSequence formatFrom(Context context, Recipient recipient) {
    return formatFrom(context, new Recipients(recipient));
  }

  public static CharSequence formatFrom(Context context, Recipients from) {
    return formatFrom(context, from, true);
  }

  public static CharSequence formatFrom(Context context, Recipients from, boolean read) {
    int attributes[]  = new int[] {R.attr.conversation_list_item_count_color};
    TypedArray colors = context.obtainStyledAttributes(attributes);

    final String fromString;
    final boolean isUnnamedGroup = from.isGroupRecipient() && TextUtils.isEmpty(from.getPrimaryRecipient().getName());
    if (isUnnamedGroup) {
      fromString = context.getString(R.string.ConversationActivity_unnamed_group);
    } else {
      fromString = from.toShortString();
    }
    SpannableStringBuilder builder = new SpannableStringBuilder(fromString);

    final int typeface;
    if (isUnnamedGroup) {
      if (!read) typeface = Typeface.BOLD_ITALIC;
      else       typeface = Typeface.ITALIC;
    } else if (!read) {
      typeface = Typeface.BOLD;
    } else {
      typeface = Typeface.NORMAL;
    }

    builder.setSpan(new StyleSpan(typeface), 0, builder.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);


    colors.recycle();
    return builder;
  }

  public static void setContactPhoto(final Context context, final ImageView imageView, final Recipient recipient, boolean showQuickContact) {
    if (recipient == null) return;

    imageView.setImageBitmap(recipient.getCircleCroppedContactPhoto());

    if (!recipient.isGroupRecipient() && showQuickContact) {
      imageView.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (recipient.getContactUri() != null) {
            QuickContact.showQuickContact(context, imageView, recipient.getContactUri(), QuickContact.MODE_LARGE, null);
          } else {
            Intent intent = new Intent(Intents.SHOW_OR_CREATE_CONTACT,  Uri.fromParts("tel", recipient.getNumber(), null));
            context.startActivity(intent);
          }
        }
      });
    } else {
      imageView.setOnClickListener(null);
    }
  }

}
