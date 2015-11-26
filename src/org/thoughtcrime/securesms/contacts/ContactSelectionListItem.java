package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.ViewUtil;

public class ContactSelectionListItem extends LinearLayout implements Recipients.RecipientsModifiedListener {

  private AvatarImageView contactPhotoImage;
  private TextView        numberView;
  private TextView        nameView;
  private TextView        labelView;
  private CheckBox        checkBox;

  private long       id;
  private String     number;
  private Recipients recipients;

  public ContactSelectionListItem(Context context) {
    super(context);
  }

  public ContactSelectionListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.contactPhotoImage = (AvatarImageView) findViewById(R.id.contact_photo_image);
    this.numberView        = (TextView)        findViewById(R.id.number);
    this.labelView         = (TextView)        findViewById(R.id.label);
    this.nameView          = (TextView)        findViewById(R.id.name);
    this.checkBox          = (CheckBox)        findViewById(R.id.check_box);

    ViewUtil.setTextViewGravityStart(this.nameView, getContext());
  }

  public void set(long id, int type, String name, String number, String label, int color, boolean multiSelect) {
    this.id     = id;
    this.number = number;

    if (type == ContactsDatabase.NEW_TYPE) {
      this.recipients = null;
      this.contactPhotoImage.setAvatar(Recipient.getUnknownRecipient(), false);
    } else if (!TextUtils.isEmpty(number)) {
      this.recipients = RecipientFactory.getRecipientsFromString(getContext(), number, true);

      if (this.recipients.getPrimaryRecipient() != null &&
          this.recipients.getPrimaryRecipient().getName() != null)
      {
        name = this.recipients.getPrimaryRecipient().getName();
      }

      this.recipients.addListener(this);
    }

    this.nameView.setTextColor(color);
    this.numberView.setTextColor(color);
    this.contactPhotoImage.setAvatar(recipients, false);

    setText(type, name, number, label);

    if (multiSelect) this.checkBox.setVisibility(View.VISIBLE);
    else             this.checkBox.setVisibility(View.GONE);
  }

  public void setChecked(boolean selected) {
    this.checkBox.setChecked(selected);
  }

  public void unbind() {
    if (recipients != null) {
      recipients.removeListener(this);
      recipients = null;
    }
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

  public long getContactId() {
    return id;
  }

  public String getNumber() {
    return number;
  }

  @Override
  public void onModified(final Recipients recipients) {
    if (this.recipients == recipients) {
      this.contactPhotoImage.post(new Runnable() {
        @Override
        public void run() {
          contactPhotoImage.setAvatar(recipients, false);
          nameView.setText(recipients.toShortString());
        }
      });
    }
  }
}
