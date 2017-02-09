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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.components.AlertView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.DeliveryStatusView;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.ResUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Locale;
import java.util.Set;

import static org.thoughtcrime.securesms.util.SpanUtil.color;

/**
 * A view that displays the element in a list of multiple conversation threads.
 * Used by SecureSMS's ListActivity via a ConversationListAdapter.
 *
 * @author Moxie Marlinspike
 */

public class ConversationListItem extends RelativeLayout
                                  implements RecipientModifiedListener,
                                             BindableConversationListItem, Unbindable
{
  private final static String TAG = ConversationListItem.class.getSimpleName();

  private final static Typeface BOLD_TYPEFACE  = Typeface.create("sans-serif", Typeface.BOLD);
  private final static Typeface LIGHT_TYPEFACE = Typeface.create("sans-serif-light", Typeface.NORMAL);

  private Set<Long>          selectedThreads;
  private Recipient          recipient;
  private long               threadId;
  private TextView           subjectView;
  private FromTextView       fromView;
  private TextView           dateView;
  private TextView           archivedView;
  private DeliveryStatusView deliveryStatusIndicator;
  private AlertView          alertView;
  private long               lastSeen;

  private boolean         read;
  private boolean         archived;
  private AvatarImageView contactPhotoImage;
  private ThumbnailView   thumbnailView;

  private final @DrawableRes int readBackground;
  private final @DrawableRes int unreadBackround;

  private final Handler handler = new Handler();
  private int distributionType;

  public ConversationListItem(Context context) {
    this(context, null);
  }

  public ConversationListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    readBackground  = ResUtil.getDrawableRes(context, R.attr.conversation_list_item_background_read);
    unreadBackround = ResUtil.getDrawableRes(context, R.attr.conversation_list_item_background_unread);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.subjectView             = (TextView)           findViewById(R.id.subject);
    this.fromView                = (FromTextView)       findViewById(R.id.from);
    this.dateView                = (TextView)           findViewById(R.id.date);
    this.deliveryStatusIndicator = (DeliveryStatusView) findViewById(R.id.delivery_status);
    this.alertView               = (AlertView)          findViewById(R.id.indicators_parent);
    this.contactPhotoImage       = (AvatarImageView)    findViewById(R.id.contact_photo_image);
    this.thumbnailView           = (ThumbnailView)      findViewById(R.id.thumbnail);
    this.archivedView            = ViewUtil.findById(this, R.id.archived);
    thumbnailView.setClickable(false);

    ViewUtil.setTextViewGravityStart(this.fromView, getContext());
    ViewUtil.setTextViewGravityStart(this.subjectView, getContext());
  }

  public void bind(@NonNull MasterSecret masterSecret, @NonNull ThreadRecord thread,
                   @NonNull Locale locale, @NonNull Set<Long> selectedThreads, boolean batchMode)
  {
    this.selectedThreads  = selectedThreads;
    this.recipient        = thread.getRecipient();
    this.threadId         = thread.getThreadId();
    this.read             = thread.isRead();
    this.archived         = thread.isArchived();
    this.distributionType = thread.getDistributionType();
    this.lastSeen         = thread.getLastSeen();

    this.recipient.addListener(this);
    this.fromView.setText(recipient, read);

    this.subjectView.setText(thread.getDisplayBody());
    this.subjectView.setTypeface(read ? LIGHT_TYPEFACE : BOLD_TYPEFACE);

    if (thread.getDate() > 0) {
      CharSequence date = DateUtils.getBriefRelativeTimeSpanString(getContext(), locale, thread.getDate());
      dateView.setText(read ? date : color(getResources().getColor(R.color.textsecure_primary), date));
      dateView.setTypeface(read ? LIGHT_TYPEFACE : BOLD_TYPEFACE);
    }

    if (thread.isArchived()) {
      this.archivedView.setVisibility(View.VISIBLE);
    } else {
      this.archivedView.setVisibility(View.GONE);
    }

    setStatusIcons(thread);
    setThumbnailSnippet(masterSecret, thread);
    setBatchState(batchMode);
    setBackground(thread);
    setRippleColor(recipient);
    this.contactPhotoImage.setAvatar(recipient, true);
  }

  @Override
  public void unbind() {
    if (this.recipient != null) this.recipient.removeListener(this);
  }

  private void setBatchState(boolean batch) {
    setSelected(batch && selectedThreads.contains(threadId));
  }

  public Recipient getRecipient() {
    return recipient;
  }

  public long getThreadId() {
    return threadId;
  }

  public boolean getRead() {
    return read;
  }

  public boolean getArchived() {
    return archived;
  }

  public int getDistributionType() {
    return distributionType;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  private void setThumbnailSnippet(MasterSecret masterSecret, ThreadRecord thread) {
    if (thread.getSnippetUri() != null) {
      this.thumbnailView.setVisibility(View.VISIBLE);
      this.thumbnailView.setImageResource(masterSecret, thread.getSnippetUri());

      LayoutParams subjectParams = (RelativeLayout.LayoutParams)this.subjectView.getLayoutParams();
      subjectParams.addRule(RelativeLayout.LEFT_OF, R.id.thumbnail);
      if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
        subjectParams.addRule(RelativeLayout.START_OF, R.id.thumbnail);
      }
      this.subjectView.setLayoutParams(subjectParams);
      this.post(new ThumbnailPositioner(thumbnailView, archivedView, deliveryStatusIndicator, dateView));
    } else {
      this.thumbnailView.setVisibility(View.GONE);

      LayoutParams subjectParams = (RelativeLayout.LayoutParams)this.subjectView.getLayoutParams();
      subjectParams.addRule(RelativeLayout.LEFT_OF, R.id.delivery_status);
      if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
        subjectParams.addRule(RelativeLayout.START_OF, R.id.delivery_status);
      }
      this.subjectView.setLayoutParams(subjectParams);
    }
  }

  private void setStatusIcons(ThreadRecord thread) {
    if (!thread.isOutgoing() || thread.isOutgoingCall()) {
      deliveryStatusIndicator.setNone();
      alertView.setNone();
    } else if (thread.isFailed()) {
      deliveryStatusIndicator.setNone();
      alertView.setFailed();
    } else if (thread.isPendingInsecureSmsFallback()) {
      deliveryStatusIndicator.setNone();
      alertView.setPendingApproval();
    } else {
      alertView.setNone();

      if      (thread.isPending())   deliveryStatusIndicator.setPending();
      else if (thread.isDelivered()) deliveryStatusIndicator.setDelivered();
      else                           deliveryStatusIndicator.setSent();
    }
  }

  private void setBackground(ThreadRecord thread) {
    if (thread.isRead()) setBackgroundResource(readBackground);
    else                 setBackgroundResource(unreadBackround);
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  private void setRippleColor(Recipient recipient) {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      ((RippleDrawable)(getBackground()).mutate())
          .setColor(ColorStateList.valueOf(recipient.getColor().toConversationColor(getContext())));
    }
  }

  @Override
  public void onModified(final Recipient recipient) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        fromView.setText(recipient, read);
        contactPhotoImage.setAvatar(recipient, true);
        setRippleColor(recipient);
      }
    });
  }

  private static class ThumbnailPositioner implements Runnable {

    private final View thumbnailView;
    private final View archivedView;
    private final View deliveryStatusView;
    private final View dateView;

    public ThumbnailPositioner(View thumbnailView, View archivedView, View deliveryStatusView, View dateView) {
      this.thumbnailView      = thumbnailView;
      this.archivedView       = archivedView;
      this.deliveryStatusView = deliveryStatusView;
      this.dateView           = dateView;
    }

    @Override
    public void run() {
      LayoutParams thumbnailParams = (RelativeLayout.LayoutParams)thumbnailView.getLayoutParams();

      if (archivedView.getVisibility() == View.VISIBLE &&
          (archivedView.getWidth() + deliveryStatusView.getWidth()) > dateView.getWidth())
      {
        thumbnailParams.addRule(RelativeLayout.LEFT_OF, R.id.delivery_status);
        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
          thumbnailParams.addRule(RelativeLayout.START_OF, R.id.delivery_status);
        }
      } else {
        thumbnailParams.addRule(RelativeLayout.LEFT_OF, R.id.date);
        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
          thumbnailParams.addRule(RelativeLayout.START_OF, R.id.date);
        }
      }

      thumbnailView.setLayoutParams(thumbnailParams);
    }
  }

}
