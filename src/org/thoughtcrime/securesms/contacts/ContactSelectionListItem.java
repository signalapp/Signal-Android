package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

public class ContactSelectionListItem extends LinearLayout implements RecipientModifiedListener {

  @SuppressWarnings("unused")
  private static final String TAG = ContactSelectionListItem.class.getSimpleName();

  private AvatarImageView contactPhotoImage;
  private TextView        numberView;
  private TextView        nameView;
  private TextView        labelView;
  private CheckBox        checkBox;

  private String        number;
  private Recipient     recipient;
  private GlideRequests glideRequests;

  public ContactSelectionListItem(Context context) {
    super(context);
  }

  public ContactSelectionListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.contactPhotoImage = findViewById(R.id.contact_photo_image);
    this.numberView        = findViewById(R.id.number);
    this.labelView         = findViewById(R.id.label);
    this.nameView          = findViewById(R.id.name);
    this.checkBox          = findViewById(R.id.check_box);

    ViewUtil.setTextViewGravityStart(this.nameView, getContext());
  }

  public void set(@NonNull GlideRequests glideRequests, int type, String name, String number, String label, int color, boolean multiSelect) {
    this.glideRequests = glideRequests;
    this.number        = number;

    if (type == ContactsDatabase.NEW_TYPE) {
      this.recipient = null;
      this.contactPhotoImage.setAvatar(glideRequests, Recipient.from(getContext(), Address.UNKNOWN, true), false);
    } else if (!TextUtils.isEmpty(number)) {
      Address address = Address.fromExternal(getContext(), number);
      this.recipient = Recipient.from(getContext(), address, true);
      this.recipient.addListener(this);

      if (this.recipient.getName() != null) {
        name = this.recipient.getName();
      }
    }

    this.nameView.setTextColor(color);
    this.numberView.setTextColor(color);
    this.contactPhotoImage.setAvatar(glideRequests, recipient, false);

    setText(type, name, number, label);

    if (multiSelect) this.checkBox.setVisibility(View.VISIBLE);
    else             this.checkBox.setVisibility(View.GONE);
  }

  public void setChecked(boolean selected) {
    this.checkBox.setChecked(selected);
  }

  public void unbind(GlideRequests glideRequests) {
    if (recipient != null) {
      recipient.removeListener(this);
      recipient = null;
    }

    contactPhotoImage.clear(glideRequests);
  }

  private void setText(int type, String name, String number, String label) {
    if (number == null || number.isEmpty()) {
      this.nameView.setEnabled(false);
      this.numberView.setText("");
      this.labelView.setVisibility(View.GONE);
    } else if (type == ContactsDatabase.PUSH_TYPE) {
      this.numberView.setText(number);
      this.nameView.setEnabled(true);
      this.labelView.setVisibility(View.GONE);
    } else {
      this.numberView.setText(number);
      this.nameView.setEnabled(true);
      this.labelView.setText(label);
      this.labelView.setVisibility(View.VISIBLE);
    }

    this.nameView.setText(name);
  }

  public String getNumber() {
    return number;
  }

  @Override
  public void onModified(final Recipient recipient) {
    if (this.recipient == recipient) {
      Util.runOnMain(() -> {
        contactPhotoImage.setAvatar(glideRequests, recipient, false);
        nameView.setText(recipient.toShortString());
      });
    }
  }
}
