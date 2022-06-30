package org.thoughtcrime.securesms.conversation;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

public enum AttachmentKeyboardButton {

  GALLERY(R.string.AttachmentKeyboard_gallery, R.drawable.ic_gallery_outline_24),
  FILE(R.string.AttachmentKeyboard_file, R.drawable.ic_file_outline_24),
  PAYMENT(R.string.AttachmentKeyboard_payment, R.drawable.ic_payments_24),
  CONTACT(R.string.AttachmentKeyboard_contact, R.drawable.ic_contact_outline_24),
  LOCATION(R.string.AttachmentKeyboard_location, R.drawable.ic_location_outline_24);

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
