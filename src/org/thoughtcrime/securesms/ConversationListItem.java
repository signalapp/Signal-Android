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
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.components.ForegroundImageView;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.Emoji;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.RecipientViewUtil;

import java.util.Set;

import static org.thoughtcrime.securesms.util.SpanUtil.color;

/**
 * A view that displays the element in a list of multiple conversation threads.
 * Used by SecureSMS's ListActivity via a ConversationListAdapter.
 *
 * @author Moxie Marlinspike
 */

public class ConversationListItem extends RelativeLayout
                                  implements Recipient.RecipientModifiedListener
{
  private final static String TAG = ConversationListItem.class.getSimpleName();

  private final static Typeface BOLD_TYPEFACE  = Typeface.create("sans-serif", Typeface.BOLD);
  private final static Typeface LIGHT_TYPEFACE = Typeface.create("sans-serif-light", Typeface.NORMAL);

  private Context    context;
  private long       threadId;
  private Recipients recipients;
  private boolean    read;
  private int        distributionType;
  private Set<Long>  selectedThreads;

  private ListenableFutureTask<Slide>                  snippetSlideFuture;
  private FutureTaskListener<Slide>                    snippetSlideListener;
  private ListenableFutureTask<Pair<Drawable,Boolean>> snippetThumbnailFuture;
  private SnippetThumbnailListener                     snippetThumbnailListener;

  private TextView            subjectView;
  private TextView            fromView;
  private TextView            dateView;
  private ImageView           contactPhotoImage;
  private ForegroundImageView mediaPreviewImage;

  private final Handler handler = new Handler();

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
    this.mediaPreviewImage = (ForegroundImageView) findViewById(R.id.media_preview);

    initializeContactWidgetVisibility();
  }

  public void set(ThreadRecord thread, Set<Long> selectedThreads, boolean batchMode) {
    this.selectedThreads  = selectedThreads;
    this.recipients       = thread.getRecipients();
    this.threadId         = thread.getThreadId();
    this.read             = thread.isRead();
    this.distributionType = thread.getDistributionType();

    this.recipients.addListener(this);
    this.fromView.setText(RecipientViewUtil.formatFrom(context, recipients, read));

    this.subjectView.setText(Emoji.getInstance(context).emojify(thread.getDisplayBody(),
                                                                Emoji.EMOJI_SMALL,
                                                                new Emoji.InvalidatingPageLoadedListener(subjectView)),
                             TextView.BufferType.SPANNABLE);
    this.subjectView.setTypeface(read ? LIGHT_TYPEFACE : BOLD_TYPEFACE);

    if (thread.getDate() > 0) {
      CharSequence date = DateUtils.getBriefRelativeTimeSpanString(context, thread.getDate());
      dateView.setText(read ? date : color(getResources().getColor(R.color.textsecure_primary), date));
      dateView.setTypeface(read ? LIGHT_TYPEFACE : BOLD_TYPEFACE);
    }

    setBackground(read, batchMode);
    RecipientViewUtil.setContactPhoto(context, contactPhotoImage, recipients.getPrimaryRecipient(), true);

    mediaPreviewImage.reset();
    if (thread.getSnippetSlide() != null) {
      setSnippetSlideAttributes(thread);
    } else {
      mediaPreviewImage.hide();
    }
  }

  public void unbind() {
    if (this.recipients != null)
      this.recipients.removeListener(this);

    if (snippetSlideFuture != null && snippetSlideListener != null) {
      snippetSlideFuture.removeListener(snippetSlideListener);
    }

    if (snippetThumbnailFuture != null && snippetThumbnailListener != null) {
      snippetThumbnailFuture.removeListener(snippetThumbnailListener);
    }
  }

  private void initializeContactWidgetVisibility() {
    contactPhotoImage.setVisibility(View.VISIBLE);
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

  private void setSnippetSlideAttributes(ThreadRecord thread) {
    snippetSlideFuture   = thread.getSnippetSlide();
    snippetSlideListener = new SnippetSlideListener();

    snippetSlideFuture.addListener(snippetSlideListener);
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
        ConversationListItem.this.fromView.setText(RecipientViewUtil.formatFrom(context, recipients, read));
        RecipientViewUtil.setContactPhoto(context, contactPhotoImage, recipients.getPrimaryRecipient(), true);
      }
    });
  }

  private class SnippetSlideListener implements FutureTaskListener<Slide> {
    @Override
    public void onSuccess(final Slide slideResult) {
      if (slideResult == null) {
        this.onFailure(null);
        return;
      }

      snippetThumbnailFuture   = slideResult.getThumbnail(context);
      snippetThumbnailListener = new SnippetThumbnailListener();

      snippetThumbnailFuture.addListener(snippetThumbnailListener);
    }

    @Override
    public void onFailure(Throwable error) {
      Log.w(TAG, "snippet thumbnail error", error);
      handler.post(new Runnable() {
        @Override
        public void run() {
          mediaPreviewImage.hide();
        }
      });
    }
  }

  private class SnippetThumbnailListener implements FutureTaskListener<Pair<Drawable, Boolean>> {
    @Override
    public void onSuccess(final Pair<Drawable, Boolean> result) {
      handler.post(new Runnable() {
        @Override
        public void run() {
          mediaPreviewImage.show(result.first, result.second);
        }
      });
    }

    @Override
    public void onFailure(final Throwable error) {
      handler.post(new Runnable() {
        @Override
        public void run() {
          Log.w(TAG, "snippet thumbnail error", error);
          mediaPreviewImage.hide();
        }
      });
    }
  }
}
