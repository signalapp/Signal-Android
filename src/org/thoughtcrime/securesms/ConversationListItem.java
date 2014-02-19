/**
 * Copyright (C) 2011 Whisper Systems
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
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Contacts.Intents;
import android.provider.ContactsContract.QuickContact;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.Emoji;

import java.util.Set;

/**
 * A view that displays the element in a list of multiple conversation threads.
 * Used by SecureSMS's ListActivity via a ConversationListAdapter.
 *
 * @author Moxie Marlinspike
 */

public class ConversationListItem extends RelativeLayout
                                  implements Recipient.RecipientModifiedListener
{

  private Context           context;
  private Set<Long>         selectedThreads;
  private Recipients        recipients;
  private long              threadId;
  private TextView          subjectView;
  private TextView          fromView;
  private TextView          dateView;
  private long              count;
  private boolean           read;

  private ImageView         contactPhotoImage;

  private final Handler handler = new Handler();
  private int distributionType;

  public ConversationListItem(Context context) {
    super(context);
    this.context = context;
  }

  public ConversationListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  @Override
  protected void onFinishInflate() {
    this.subjectView       = (TextView) findViewById(R.id.subject);
    this.fromView          = (TextView) findViewById(R.id.from);
    this.dateView          = (TextView) findViewById(R.id.date);

    this.contactPhotoImage = (ImageView) findViewById(R.id.contact_photo_image);

    initializeContactWidgetVisibility();
  }

  public void set(ThreadRecord thread, Set<Long> selectedThreads, boolean batchMode) {
    this.selectedThreads  = selectedThreads;
    this.recipients       = thread.getRecipients();
    this.threadId         = thread.getThreadId();
    this.count            = thread.getCount();
    this.read             = thread.isRead();
    this.distributionType = thread.getDistributionType();

    this.recipients.addListener(this);
    this.fromView.setText(formatFrom(recipients, count, read));

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
      this.subjectView.setText(Emoji.getInstance(context).emojify(thread.getDisplayBody(),
                                                                  Emoji.EMOJI_SMALL),
                               TextView.BufferType.SPANNABLE);
    } else {
      this.subjectView.setText(thread.getDisplayBody());
    }

    if (thread.getDate() > 0)
      this.dateView.setText(DateUtils.getBetterRelativeTimeSpanString(getContext(), thread.getDate()));

    setBackground(read, batchMode);
    setContactPhoto(this.recipients.getPrimaryRecipient());
  }

  public void unbind() {
    if (this.recipients != null)
      this.recipients.removeListener(this);
  }

  private void initializeContactWidgetVisibility() {
    contactPhotoImage.setVisibility(View.VISIBLE);
  }

  private void setContactPhoto(final Recipient recipient) {
    if (recipient == null) return;

    contactPhotoImage.setImageBitmap(BitmapUtil.getCircleCroppedBitmap(recipient.getContactPhoto()));
    contactPhotoImage.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (recipient.getContactUri() != null) {
          QuickContact.showQuickContact(context, contactPhotoImage, recipient.getContactUri(), QuickContact.MODE_LARGE, null);
        } else {
          Intent intent = new Intent(Intents.SHOW_OR_CREATE_CONTACT,  Uri.fromParts("tel", recipient.getNumber(), null));
          context.startActivity(intent);
        }
      }
    });
  }

  private void setBackground(boolean read, boolean batch) {
    int[]      attributes = new int[]{R.attr.conversation_list_item_background_selected,
                                      R.attr.conversation_list_item_background_read,
                                      R.attr.conversation_list_item_background_unread};

    TypedArray drawables  = context.obtainStyledAttributes(attributes);

    if (batch && selectedThreads.contains(threadId)) {
      setBackgroundDrawable(drawables.getDrawable(0));
    } else if (read) {
      setBackgroundDrawable(drawables.getDrawable(1));
    } else {
      setBackgroundDrawable(drawables.getDrawable(2));
    }

    drawables.recycle();
  }

  private CharSequence formatFrom(Recipients from, long count, boolean read) {
    int attributes[]  = new int[] {R.attr.conversation_list_item_count_color};
    TypedArray colors = context.obtainStyledAttributes(attributes);

    final String fromString;
    final boolean isUnnamedGroup = from.isGroupRecipient() && TextUtils.isEmpty(from.getPrimaryRecipient().getName());
    if (isUnnamedGroup) {
      fromString = context.getString(R.string.ConversationActivity_unnamed_group);
    } else {
      fromString = from.toShortString();
    }
    SpannableStringBuilder builder = new SpannableStringBuilder(fromString);


    final int typeface;
    if (isUnnamedGroup) {
      if (!read) typeface = Typeface.BOLD_ITALIC;
      else       typeface = Typeface.ITALIC;
    } else if (!read) {
      typeface = Typeface.BOLD;
    } else {
      typeface = Typeface.NORMAL;
    }

    builder.setSpan(new StyleSpan(typeface), 0, builder.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);


    colors.recycle();
    return builder;
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
        ConversationListItem.this.fromView.setText(formatFrom(recipients, count, read));
        setContactPhoto(ConversationListItem.this.recipients.getPrimaryRecipient());
      }
    });
  }
}
