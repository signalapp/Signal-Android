package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhotoFactory;
import org.thoughtcrime.securesms.recipients.Recipient;

public class AvatarImageView extends android.support.v7.widget.AppCompatImageView {

  private boolean inverted;
  private OnClickListener listener = null;

  public AvatarImageView(Context context) {
    super(context);
    setScaleType(ScaleType.CENTER_CROP);
  }

  public AvatarImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setScaleType(ScaleType.CENTER_CROP);

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AvatarImageView, 0, 0);
      inverted = typedArray.getBoolean(0, false);
      typedArray.recycle();
    }
  }

  @Override
  public void setOnClickListener(OnClickListener listener) {
    this.listener = listener;
    super.setOnClickListener(listener);
  }

  public void setAvatar(final @Nullable Recipient recipient, boolean quickContactEnabled) {
    if (recipient != null) {
      MaterialColor backgroundColor = recipient.getColor();
      setImageDrawable(recipient.getContactPhoto().asDrawable(getContext(), backgroundColor.toConversationColor(getContext()), inverted));
      setAvatarClickHandler(recipient, quickContactEnabled);
    } else {
      setImageDrawable(ContactPhotoFactory.getDefaultContactPhoto(null).asDrawable(getContext(), ContactColors.UNKNOWN_COLOR.toConversationColor(getContext()), inverted));
      super.setOnClickListener(listener);
    }
  }

  private void setAvatarClickHandler(final Recipient recipient, boolean quickContactEnabled) {
    if (!recipient.isGroupRecipient() && quickContactEnabled) {
      setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (recipient.getContactUri() != null) {
            ContactsContract.QuickContact.showQuickContact(getContext(), AvatarImageView.this, recipient.getContactUri(), ContactsContract.QuickContact.MODE_LARGE, null);
          } else {
            final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            if (recipient.getAddress().isEmail()) {
              intent.putExtra(ContactsContract.Intents.Insert.EMAIL, recipient.getAddress().toEmailString());
            } else {
              intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipient.getAddress().toPhoneString());
            }
            intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
            getContext().startActivity(intent);
          }
        }
      });
    } else {
      super.setOnClickListener(listener);
    }
  }
}
