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
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.makeramen.RoundedImageView;

import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.RecipientViewUtil;

/**
 * A simple view to show the recipients of an open conversation
 *
 * @author Jake McGinty
 */
public class ShareListItem extends RelativeLayout
                        implements Recipient.RecipientModifiedListener
{
  private final static String TAG = ShareListItem.class.getSimpleName();

  private Context    context;
  private Recipients recipients;
  private long       threadId;
  private TextView   fromView;

  private RoundedImageView contactPhotoImage;

  private final Handler handler = new Handler();
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
    this.fromView          = (TextView)  findViewById(R.id.from);
    this.contactPhotoImage = (RoundedImageView) findViewById(R.id.contact_photo_image);
  }

  public void set(ThreadRecord thread) {
    this.recipients       = thread.getRecipients();
    this.threadId         = thread.getThreadId();
    this.distributionType = thread.getDistributionType();

    this.recipients.addListener(this);
    this.fromView.setText(RecipientViewUtil.formatFrom(getContext(), recipients));

    setBackground();
    RecipientViewUtil.setContactPhoto(getContext(), contactPhotoImage, this.recipients.getPrimaryRecipient(), false);
  }

  public void unbind() {
    if (this.recipients != null) this.recipients.removeListener(this);
  }

  private void setBackground() {
    int[]      attributes = new int[]{R.attr.conversation_list_item_background_read};
    TypedArray drawables  = context.obtainStyledAttributes(attributes);

    setBackgroundDrawable(drawables.getDrawable(0));

    drawables.recycle();
  }

  public Recipients getRecipients() {
    return recipients;
  }

  public long getThreadId() {
    return threadId;
  }

  public int getDistributionType() {
    return distributionType;
  }

  @Override
  public void onModified(Recipient recipient) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        fromView.setText(RecipientViewUtil.formatFrom(getContext(), recipients));
        RecipientViewUtil.setContactPhoto(getContext(), contactPhotoImage, recipients.getPrimaryRecipient(), false);
      }
    });
  }
}
