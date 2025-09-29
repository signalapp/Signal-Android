package org.thoughtcrime.securesms.conversation;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

public enum AttachmentKeyboardButton {

  GALLERY(R.string.AttachmentKeyboard_gallery, R.drawable.symbol_album_tilt_24),
  FILE(R.string.AttachmentKeyboard_file, R.drawable.symbol_file_24),
  PAYMENT(R.string.AttachmentKeyboard_payment, R.drawable.symbol_payment_24),
  CONTACT(R.string.AttachmentKeyboard_contact, R.drawable.symbol_person_circle_24),
  LOCATION(R.string.AttachmentKeyboard_location, R.drawable.symbol_location_circle_24);

  private final int titleRes;
  private final int iconRes;
  // TODO: add contentDescription to the icon resource for improved accessibility

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
