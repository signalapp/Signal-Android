package org.thoughtcrime.securesms.conversation;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

public enum AttachmentKeyboardButton {

  GALLERY(R.string.AttachmentKeyboard_gallery, R.drawable.ic_photo_album_outline_32),
  FILE(R.string.AttachmentKeyboard_file, R.drawable.ic_file_outline_32),
  PAYMENT(R.string.AttachmentKeyboard_payment, R.drawable.ic_payments_32),
  CONTACT(R.string.AttachmentKeyboard_contact, R.drawable.ic_contact_circle_outline_32),
  LOCATION(R.string.AttachmentKeyboard_location, R.drawable.ic_location_outline_32);

  private final int titleRes;
  private final int iconRes;

  AttachmentKeyboardButton(@StringRes int titleRes, @DrawableRes int iconRes) {
    this.titleRes = titleRes;
    this.iconRes = iconRes;
  }

  public @StringRes int getTitleRes() {
    return titleRes;
  }

  public @DrawableRes int getIconRes() {
    return iconRes;
  }
}
