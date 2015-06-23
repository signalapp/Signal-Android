package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;

public class ContactSelectionListItem extends RelativeLayout implements Recipient.RecipientModifiedListener {

  private ImageView contactPhotoImage;
  private TextView  numberView;
  private TextView  nameView;
  private TextView  labelView;
  private CheckBox  checkBox;

  private long      id;
  private String    number;
  private Recipient recipient;

  public ContactSelectionListItem(Context context) {
    super(context);
  }

  public ContactSelectionListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ContactSelectionListItem(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected void onFinishInflate() {
    this.contactPhotoImage = (ImageView) findViewById(R.id.contact_photo_image);
    this.numberView        = (TextView)  findViewById(R.id.number);
    this.labelView         = (TextView)  findViewById(R.id.label);
    this.nameView          = (TextView)  findViewById(R.id.name);
    this.checkBox          = (CheckBox)  findViewById(R.id.check_box);
  }

  public void set(long id, int type, String name, String number, String label, int color, boolean multiSelect) {
    this.id     = id;
    this.number = number;

    if (number != null) {
      this.recipient = RecipientFactory.getRecipientsFromString(getContext(), number, true)
                                       .getPrimaryRecipient();
    }

    this.nameView.setTextColor(color);
    this.numberView.setTextColor(color);

    setText(type, name, number, label);
    setContactPhotoImage(recipient);

    if (multiSelect) this.checkBox.setVisibility(View.VISIBLE);
    else             this.checkBox.setVisibility(View.GONE);
  }

  public void setChecked(boolean selected) {
    this.checkBox.setChecked(selected);
  }

  public void unbind() {
    if (recipient != null) {
      recipient.removeListener(this);
      recipient = null;
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

  private void setContactPhotoImage(@Nullable Recipient recipient) {
    if (recipient!= null) {
      contactPhotoImage.setImageDrawable(recipient.getContactPhoto().asDrawable(getContext()));
      recipient.addListener(this);
    } else {
      contactPhotoImage.setImageDrawable(null);
    }
  }

  @Override
  public void onModified(final Recipient recipient) {
    if (this.recipient == recipient) {
      recipient.removeListener(this);
      this.contactPhotoImage.post(new Runnable() {
        @Override
        public void run() {
          contactPhotoImage.setImageDrawable(recipient.getContactPhoto().asDrawable(getContext()));
        }
      });
    }
  }

  public long getContactId() {
    return id;
  }

  public String getNumber() {
    return number;
  }
}
