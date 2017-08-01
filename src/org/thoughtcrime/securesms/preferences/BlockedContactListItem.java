package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.util.Util;

public class BlockedContactListItem extends RelativeLayout implements RecipientModifiedListener {

  private AvatarImageView contactPhotoImage;
  private TextView        nameView;
  private Recipient       recipient;

  public BlockedContactListItem(Context context) {
    super(context);
  }

  public BlockedContactListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public BlockedContactListItem(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();
    this.contactPhotoImage = (AvatarImageView)findViewById(R.id.contact_photo_image);
    this.nameView          = (TextView)       findViewById(R.id.name);
  }

  public void set(Recipient recipients) {
    this.recipient = recipients;

    onModified(recipients);
    recipients.addListener(this);
  }

  @Override
  public void onModified(final Recipient recipients) {
    final AvatarImageView contactPhotoImage = this.contactPhotoImage;
    final TextView        nameView          = this.nameView;

    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        contactPhotoImage.setAvatar(recipients, false);
        nameView.setText(recipients.toShortString());
      }
    });
  }

  public Recipient getRecipient() {
    return recipient;
  }
}
