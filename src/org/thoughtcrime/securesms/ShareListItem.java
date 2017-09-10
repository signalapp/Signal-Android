/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.util.Util;

/**
 * A simple view to show the recipients of an open conversation
 *
 * @author Jake McGinty
 */
public class ShareListItem extends RelativeLayout
                        implements RecipientModifiedListener
{
  private final static String TAG = ShareListItem.class.getSimpleName();

  private Context      context;
  private Recipient    recipient;
  private long         threadId;
  private FromTextView fromView;

  private AvatarImageView contactPhotoImage;

  private int distributionType;

  public ShareListItem(Context context) {
    super(context);
    this.context = context;
  }

  public ShareListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.fromView          = (FromTextView)    findViewById(R.id.from);
    this.contactPhotoImage = (AvatarImageView) findViewById(R.id.contact_photo_image);
  }

  public void set(ThreadRecord thread) {
    this.recipient        = thread.getRecipient();
    this.threadId         = thread.getThreadId();
    this.distributionType = thread.getDistributionType();

    this.recipient.addListener(this);
    this.fromView.setText(recipient);

    setBackground();
    this.contactPhotoImage.setAvatar(this.recipient, false);
  }

  public void unbind() {
    if (this.recipient != null) this.recipient.removeListener(this);
  }

  private void setBackground() {
    int[]      attributes = new int[]{R.attr.conversation_list_item_background_read};
    TypedArray drawables  = context.obtainStyledAttributes(attributes);

    setBackgroundDrawable(drawables.getDrawable(0));

    drawables.recycle();
  }

  public Recipient getRecipient() {
    return recipient;
  }

  public long getThreadId() {
    return threadId;
  }

  public int getDistributionType() {
    return distributionType;
  }

  @Override
  public void onModified(final Recipient recipient) {
    Util.runOnMain(() -> {
      fromView.setText(recipient);
      contactPhotoImage.setAvatar(recipient, false);
    });
  }
}
