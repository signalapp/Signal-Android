package org.thoughtcrime.securesms.components;

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
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

public abstract class RecipientListItem extends RelativeLayout {
  public RecipientListItem(Context context) {
    super(context);
  }

  public RecipientListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  protected CharSequence formatFrom(Recipient recipient) {
    return formatFrom(new Recipients(recipient));
  }

  protected CharSequence formatFrom(Recipients from) {
    return formatFrom(from, true);
  }

  protected CharSequence formatFrom(Recipients from, boolean read) {
    int attributes[]  = new int[] {R.attr.conversation_list_item_count_color};
    TypedArray colors = getContext().obtainStyledAttributes(attributes);

    final String fromString;
    final boolean isUnnamedGroup = from.isGroupRecipient() && TextUtils.isEmpty(from.getPrimaryRecipient().getName());
    if (isUnnamedGroup) {
      fromString = getContext().getString(R.string.ConversationActivity_unnamed_group);
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

  protected void setContactPhoto(final ImageView imageView, final Recipient recipient, boolean showQuickContact) {
    if (recipient == null) return;

    imageView.setImageBitmap(recipient.getCircleCroppedContactPhoto());

    if (!recipient.isGroupRecipient() && showQuickContact) {
      imageView.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (recipient.getContactUri() != null) {
            QuickContact.showQuickContact(getContext(), imageView, recipient.getContactUri(), QuickContact.MODE_LARGE, null);
          } else {
            Intent intent = new Intent(Intents.SHOW_OR_CREATE_CONTACT,  Uri.fromParts("tel", recipient.getNumber(), null));
            getContext().startActivity(intent);
          }
        }
      });
    } else {
      imageView.setOnClickListener(null);
    }
  }

}
