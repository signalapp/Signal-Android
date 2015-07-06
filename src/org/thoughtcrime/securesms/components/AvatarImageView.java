package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.PorterDuff.Mode;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.amulyakhare.textdrawable.TextDrawable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhotoFactory;
import org.thoughtcrime.securesms.contacts.avatars.GeneratedContactPhoto;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;

public class AvatarImageView extends ImageView {

  private boolean    inverted;
  private int        selectedColor;
  private Recipients recipients;
  private boolean    quickContactEnabled;

  public AvatarImageView(Context context) {
    this(context, null);
  }

  public AvatarImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setScaleType(ScaleType.CENTER_INSIDE);

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AvatarImageView, 0, 0);
      inverted      = typedArray.getBoolean(0, false);
      selectedColor = typedArray.getColor(1, getContext().getResources().getColor(R.color.textsecure_primary));
      typedArray.recycle();
    }
  }

  @Override protected void drawableStateChanged() {
    super.drawableStateChanged();
    updateDrawable();
    Log.w("AvatarImageView", "drawableStateChanged(), isSelected() " + isSelected());
    Log.w("AvatarImageView", new Exception());
  }

  public void setAvatar(@Nullable Recipients recipients, boolean quickContactEnabled) {
    this.recipients = recipients;
    this.quickContactEnabled = quickContactEnabled;
    updateDrawable();
  }
  public void setAvatar(@Nullable Recipient recipient, boolean quickContactEnabled) {
    setAvatar(RecipientFactory.getRecipientsFor(getContext(), recipient, true), quickContactEnabled);
  }

  private void updateDrawable() {
    if (isSelected()) {
      setImageResource(R.drawable.ic_check_white_24dp);
      setBackgroundResource(R.drawable.circle_tintable);
      getBackground().setColorFilter(selectedColor, Mode.MULTIPLY);
    } else {
      setBackgroundDrawable(null);
      if (recipients != null) {
        MaterialColor backgroundColor = recipients.getColor();
        setImageDrawable(recipients.getContactPhoto().asDrawable(getContext(), backgroundColor.toConversationColor(getContext()), inverted));
        setAvatarClickHandler(recipients, quickContactEnabled);
      } else {
        setImageDrawable(ContactPhotoFactory.getDefaultContactPhoto(null).asDrawable(getContext(), ContactColors.UNKNOWN_COLOR.toConversationColor(getContext()), inverted));
        setOnClickListener(null);
      }
    }
  }

  private void setAvatarClickHandler(final Recipients recipients, boolean quickContactEnabled) {
    if (!recipients.isGroupRecipient() && quickContactEnabled) {
      setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          Recipient recipient = recipients.getPrimaryRecipient();

          if (recipient != null && recipient.getContactUri() != null) {
            ContactsContract.QuickContact.showQuickContact(getContext(), AvatarImageView.this, recipient.getContactUri(), ContactsContract.QuickContact.MODE_LARGE, null);
          } else if (recipient != null) {
            final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipient.getNumber());
            intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
            getContext().startActivity(intent);
          }
        }
      });
    } else {
      setOnClickListener(null);
    }
  }

}
