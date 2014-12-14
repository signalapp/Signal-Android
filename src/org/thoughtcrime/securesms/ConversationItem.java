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

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.QuickContact;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.ConversationFragment.SelectionClickListener;
import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.NotificationMmsMessageRecord;
import org.thoughtcrime.securesms.jobs.MmsDownloadJob;
import org.thoughtcrime.securesms.jobs.MmsSendJob;
import org.thoughtcrime.securesms.jobs.SmsSendJob;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.Emoji;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ListenableFutureTask;

import java.util.Set;

/**
 * A view that displays an individual conversation item within a conversation
 * thread.  Used by ComposeMessageActivity's ListActivity via a ConversationAdapter.
 *
 * @author Moxie Marlinspike
 *
 */

public class ConversationItem extends LinearLayout {
  private final static String TAG = ConversationItem.class.getSimpleName();

  private final int    STYLE_ATTRIBUTES[] = new int[]{R.attr.conversation_item_sent_push_background,
                                                      R.attr.conversation_item_sent_push_triangle_background,
                                                      R.attr.conversation_item_sent_background,
                                                      R.attr.conversation_item_sent_triangle_background,
                                                      R.attr.conversation_item_sent_pending_background,
                                                      R.attr.conversation_item_sent_pending_triangle_background,
                                                      R.attr.conversation_item_sent_push_pending_background,
                                                      R.attr.conversation_item_sent_push_pending_triangle_background};

  private final static int SENT_PUSH                  = 0;
  private final static int SENT_PUSH_TRIANGLE         = 1;
  private final static int SENT_SMS                   = 2;
  private final static int SENT_SMS_TRIANGLE          = 3;
  private final static int SENT_SMS_PENDING           = 4;
  private final static int SENT_SMS_PENDING_TRIANGLE  = 5;
  private final static int SENT_PUSH_PENDING          = 6;
  private final static int SENT_PUSH_PENDING_TRIANGLE = 7;

  private Handler       failedIconHandler;
  private MessageRecord messageRecord;
  private MasterSecret  masterSecret;
  private boolean       groupThread;
  private boolean       pushDestination;

  private  View      conversationParent;
  private  TextView  bodyText;
  private  TextView  dateText;
  private  TextView  indicatorText;
  private  TextView  groupStatusText;
  private  ImageView secureImage;
  private  ImageView failedImage;
  private  ImageView contactPhoto;
  private  ImageView deliveryImage;
  private  View      triangleTick;
  private  ImageView pendingIndicator;

  private Set<MessageRecord>              batchSelected;
  private SelectionClickListener          selectionClickListener;
  private View                            mmsContainer;
  private ImageView                       mmsThumbnail;
  private Button                          mmsDownloadButton;
  private TextView                        mmsDownloadingLabel;
  private ListenableFutureTask<SlideDeck> slideDeck;
  private FutureTaskListener<SlideDeck>   slideDeckListener;
  private TypedArray                      backgroundDrawables;

  private final FailedIconClickListener     failedIconClickListener     = new FailedIconClickListener();
  private final MmsDownloadClickListener    mmsDownloadClickListener    = new MmsDownloadClickListener();
  private final MmsPreferencesClickListener mmsPreferencesClickListener = new MmsPreferencesClickListener();
  private final ClickListener               clickListener               = new ClickListener();
  private final Handler                     handler                     = new Handler();
  private final Context context;

  public ConversationItem(Context context) {
    super(context);
    this.context = context;
   }

  public ConversationItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    this.bodyText            = (TextView) findViewById(R.id.conversation_item_body);
    this.dateText            = (TextView) findViewById(R.id.conversation_item_date);
    this.indicatorText       = (TextView) findViewById(R.id.indicator_text);
    this.groupStatusText     = (TextView) findViewById(R.id.group_message_status);
    this.secureImage         = (ImageView)findViewById(R.id.sms_secure_indicator);
    this.failedImage         = (ImageView)findViewById(R.id.sms_failed_indicator);
    this.mmsContainer        =            findViewById(R.id.mms_view);
    this.mmsThumbnail        = (ImageView)findViewById(R.id.image_view);
    this.mmsDownloadButton   = (Button)   findViewById(R.id.mms_download_button);
    this.mmsDownloadingLabel = (TextView) findViewById(R.id.mms_label_downloading);
    this.contactPhoto        = (ImageView)findViewById(R.id.contact_photo);
    this.deliveryImage       = (ImageView)findViewById(R.id.delivered_indicator);
    this.conversationParent  =            findViewById(R.id.conversation_item_parent);
    this.triangleTick        =            findViewById(R.id.triangle_tick);
    this.pendingIndicator    = (ImageView)findViewById(R.id.pending_approval_indicator);
    this.backgroundDrawables = context.obtainStyledAttributes(STYLE_ATTRIBUTES);

    setOnClickListener(clickListener);
    if (failedImage != null)       failedImage.setOnClickListener(failedIconClickListener);
    if (mmsDownloadButton != null) mmsDownloadButton.setOnClickListener(mmsDownloadClickListener);
    if (mmsThumbnail != null)      mmsThumbnail.setOnLongClickListener(new MultiSelectLongClickListener());
  }

  public void set(MasterSecret masterSecret, MessageRecord messageRecord,
                  Set<MessageRecord> batchSelected, SelectionClickListener selectionClickListener,
                  Handler failedIconHandler, boolean groupThread, boolean pushDestination)
  {
    this.masterSecret           = masterSecret;
    this.messageRecord          = messageRecord;
    this.batchSelected          = batchSelected;
    this.selectionClickListener = selectionClickListener;
    this.failedIconHandler      = failedIconHandler;
    this.groupThread            = groupThread;
    this.pushDestination        = pushDestination;

    setConversationBackgroundDrawables(messageRecord);
    setSelectionBackgroundDrawables(messageRecord);
    setBodyText(messageRecord);

    if (!messageRecord.isGroupAction()) {
      setStatusIcons(messageRecord);
      setContactPhoto(messageRecord);
      setGroupMessageStatus(messageRecord);
      setEvents(messageRecord);
      setMinimumWidth();

      if (messageRecord.isMmsNotification()) {
        setNotificationMmsAttributes((NotificationMmsMessageRecord)messageRecord);
      } else if (messageRecord.isMms()) {
        setMediaMmsAttributes((MediaMmsMessageRecord)messageRecord);
      }
    }
  }

  public void unbind() {
    if (slideDeck != null && slideDeckListener != null)
      slideDeck.removeListener(slideDeckListener);
  }

  public MessageRecord getMessageRecord() {
    return messageRecord;
  }

  public void setHandler(Handler failedIconHandler) {
    this.failedIconHandler = failedIconHandler;
  }

  public static void setViewBackgroundWithoutResettingPadding(final View v, final int backgroundResId) {
    final int paddingBottom = v.getPaddingBottom();
    final int paddingLeft   = v.getPaddingLeft();
    final int paddingRight  = v.getPaddingRight();
    final int paddingTop    = v.getPaddingTop();
    v.setBackgroundResource(backgroundResId);
    v.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
  }

  /// MessageRecord Attribute Parsers

  private void setConversationBackgroundDrawables(MessageRecord messageRecord) {
    if (conversationParent != null && backgroundDrawables != null) {
      if (messageRecord.isOutgoing()) {
        final int background;
        final int triangleBackground;
        if (messageRecord.isPending() && pushDestination && !messageRecord.isForcedSms()) {
          background         = SENT_PUSH_PENDING;
          triangleBackground = SENT_PUSH_PENDING_TRIANGLE;
        } else if (messageRecord.isPending() || messageRecord.isPendingSmsFallback()) {
          background         = SENT_SMS_PENDING;
          triangleBackground = SENT_SMS_PENDING_TRIANGLE;
        } else if (messageRecord.isPush()) {
          background         = SENT_PUSH;
          triangleBackground = SENT_PUSH_TRIANGLE;
        } else {
          background         = SENT_SMS;
          triangleBackground = SENT_SMS_TRIANGLE;
        }

        setViewBackgroundWithoutResettingPadding(conversationParent, backgroundDrawables.getResourceId(background, -1));
        setViewBackgroundWithoutResettingPadding(triangleTick, backgroundDrawables.getResourceId(triangleBackground, -1));
      }
    }
  }

  private void setSelectionBackgroundDrawables(MessageRecord messageRecord) {
    int[]      attributes = new int[]{R.attr.conversation_list_item_background_selected,
                                      R.attr.conversation_item_background};

    TypedArray drawables  = context.obtainStyledAttributes(attributes);

    if (batchSelected.contains(messageRecord)) {
      setBackgroundDrawable(drawables.getDrawable(0));
    } else {
      setBackgroundDrawable(drawables.getDrawable(1));
    }

    drawables.recycle();
  }

  private void setBodyText(MessageRecord messageRecord) {
    bodyText.setClickable(false);
    bodyText.setFocusable(false);
    bodyText.setText(Emoji.getInstance(context).emojify(messageRecord.getDisplayBody(),
                                                        new Emoji.InvalidatingPageLoadedListener(bodyText)),
                     TextView.BufferType.SPANNABLE);

    if (bodyText.isClickable() && bodyText.isFocusable()) {
      bodyText.setOnLongClickListener(new MultiSelectLongClickListener());
      bodyText.setOnClickListener(new MultiSelectLongClickListener());
    }
  }

  private void setContactPhoto(MessageRecord messageRecord) {
    if (! messageRecord.isOutgoing()) {
      setContactPhotoForRecipient(messageRecord.getIndividualRecipient());
    }
  }

  private void setStatusIcons(MessageRecord messageRecord) {
    failedImage.setVisibility(messageRecord.isFailed() ? View.VISIBLE : View.GONE);
    if (messageRecord.isOutgoing()) {
      pendingIndicator.setVisibility(messageRecord.isPendingSmsFallback() ? View.VISIBLE : View.GONE);
      indicatorText.setVisibility(messageRecord.isPendingSmsFallback() ? View.VISIBLE : View.GONE);
    }
    secureImage.setVisibility(messageRecord.isSecure() ? View.VISIBLE : View.GONE);
    bodyText.setCompoundDrawablesWithIntrinsicBounds(0, 0, messageRecord.isKeyExchange() ? R.drawable.ic_menu_login : 0, 0);
    deliveryImage.setVisibility(!messageRecord.isKeyExchange() && messageRecord.isDelivered() ? View.VISIBLE : View.GONE);

    mmsThumbnail.setVisibility(View.GONE);
    mmsDownloadButton.setVisibility(View.GONE);
    mmsDownloadingLabel.setVisibility(View.GONE);

    if (messageRecord.isFailed()) {
      dateText.setText(R.string.ConversationItem_error_sending_message);
    } else if (messageRecord.isPendingSmsFallback() && indicatorText != null) {
      dateText.setText("");
      if (messageRecord.isPendingSecureSmsFallback()) {
        if (messageRecord.isMms()) indicatorText.setText(R.string.ConversationItem_click_to_approve_mms);
        else                       indicatorText.setText(R.string.ConversationItem_click_to_approve_sms);
      } else {
        indicatorText.setText(R.string.ConversationItem_click_to_approve_unencrypted);
      }
    } else if (messageRecord.isPending()) {
      dateText.setText(" ··· ");
    } else {
      final long timestamp;

      if (messageRecord.isPush()) timestamp = messageRecord.getDateSent();
      else                        timestamp = messageRecord.getDateReceived();

      dateText.setText(DateUtils.getBetterRelativeTimeSpanString(getContext(), timestamp));
    }
  }

  private void setMinimumWidth() {
    if (indicatorText != null && indicatorText.getVisibility() == View.VISIBLE && indicatorText.getText() != null) {
      final float density = getResources().getDisplayMetrics().density;
      conversationParent.setMinimumWidth(indicatorText.getText().length() * (int)(6.5 * density));
    } else {
      conversationParent.setMinimumWidth(0);
    }
  }

  private void setEvents(MessageRecord messageRecord) {
    setClickable(messageRecord.isPendingSmsFallback() ||
                 (messageRecord.isKeyExchange()            &&
                  !messageRecord.isCorruptedKeyExchange()  &&
                  !messageRecord.isOutgoing()));

    if (!messageRecord.isOutgoing()                       &&
        messageRecord.getRecipients().isSingleRecipient() &&
        !messageRecord.isSecure())
    {
      checkForAutoInitiate(messageRecord.getIndividualRecipient(),
                           messageRecord.getBody().getBody(),
                           messageRecord.getThreadId());
    }
  }

  private void setGroupMessageStatus(MessageRecord messageRecord) {
    if (groupThread && !messageRecord.isOutgoing()) {
      this.groupStatusText.setText(messageRecord.getIndividualRecipient().toShortString());
      this.groupStatusText.setVisibility(View.VISIBLE);
    } else {
      this.groupStatusText.setVisibility(View.GONE);
    }
  }

  private void setNotificationMmsAttributes(NotificationMmsMessageRecord messageRecord) {
    String messageSize = String.format(context.getString(R.string.ConversationItem_message_size_d_kb),
                                       messageRecord.getMessageSize());
    String expires     = String.format(context.getString(R.string.ConversationItem_expires_s),
                                       DateUtils.getRelativeTimeSpanString(getContext(),
                                                                           messageRecord.getExpiration(),
                                                                           false));

    dateText.setText(messageSize + "\n" + expires);

    if (MmsDatabase.Status.isDisplayDownloadButton(messageRecord.getStatus())) {
      mmsDownloadButton.setVisibility(View.VISIBLE);
      mmsDownloadingLabel.setVisibility(View.GONE);
    } else {
      mmsDownloadingLabel.setText(MmsDatabase.Status.getLabelForStatus(context, messageRecord.getStatus()));
      mmsDownloadButton.setVisibility(View.GONE);
      mmsDownloadingLabel.setVisibility(View.VISIBLE);

      if (MmsDatabase.Status.isHardError(messageRecord.getStatus()) && !messageRecord.isOutgoing())
        setOnClickListener(mmsDownloadClickListener);
      else if (MmsDatabase.Status.DOWNLOAD_APN_UNAVAILABLE == messageRecord.getStatus() && !messageRecord.isOutgoing())
        setOnClickListener(mmsPreferencesClickListener);
    }
  }

  private void setMediaMmsAttributes(MediaMmsMessageRecord messageRecord) {
    if (messageRecord.getPartCount() > 0) {
      mmsThumbnail.setVisibility(View.VISIBLE);
      mmsContainer.setVisibility(View.VISIBLE);
      mmsThumbnail.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
    } else {
      mmsThumbnail.setVisibility(View.GONE);
      mmsContainer.setVisibility(View.GONE);
    }

    slideDeck = messageRecord.getSlideDeckFuture();
    slideDeckListener = new FutureTaskListener<SlideDeck>() {
      @Override
      public void onSuccess(final SlideDeck result) {
        if (result == null)
          return;

        handler.post(new Runnable() {
          @Override
          public void run() {
            for (Slide slide : result.getSlides()) {
              if (slide.hasImage()) {
                slide.setThumbnailOn(mmsThumbnail);
                mmsThumbnail.setOnClickListener(new ThumbnailClickListener(slide));
                mmsThumbnail.setVisibility(View.VISIBLE);
                return;
              }
            }

            mmsThumbnail.setVisibility(View.GONE);
          }
        });
      }

      @Override
      public void onFailure(Throwable error) {}
    };
    slideDeck.addListener(slideDeckListener);
  }

  /// Helper Methods

  private void checkForAutoInitiate(Recipient recipient, String body, long threadId) {
    if (!groupThread &&
        AutoInitiateActivity.isValidAutoInitiateSituation(context, masterSecret, recipient,
                                                          body, threadId))
    {
      AutoInitiateActivity.exemptThread(context, threadId);

      Intent intent = new Intent();
      intent.setClass(context, AutoInitiateActivity.class);
      intent.putExtra("threadId", threadId);
      intent.putExtra("masterSecret", masterSecret);
      intent.putExtra("recipient", recipient.getRecipientId());

      context.startActivity(intent);
    }
  }

  private void setContactPhotoForRecipient(final Recipient recipient) {
    if (contactPhoto == null) return;

    Bitmap contactPhotoBitmap;

    if ((recipient.getContactPhoto() == ContactPhotoFactory.getDefaultContactPhoto(context)) && (groupThread)) {
      contactPhotoBitmap = recipient.getGeneratedAvatar(context);
    } else {
      contactPhotoBitmap = recipient.getCircleCroppedContactPhoto();
    }

    contactPhoto.setImageBitmap(contactPhotoBitmap);

    contactPhoto.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (recipient.getContactUri() != null) {
          QuickContact.showQuickContact(context, contactPhoto, recipient.getContactUri(), QuickContact.MODE_LARGE, null);
        } else {
          final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
          intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipient.getNumber());
          intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
          context.startActivity(intent);
        }
      }
    });

    contactPhoto.setVisibility(View.VISIBLE);
  }

  /// Event handlers

  private void handleKeyExchangeClicked() {
    Intent intent = new Intent(context, ReceiveKeyActivity.class);
    intent.putExtra("recipient", messageRecord.getIndividualRecipient().getRecipientId());
    intent.putExtra("recipient_device_id", messageRecord.getRecipientDeviceId());
    intent.putExtra("body", messageRecord.getBody().getBody());
    intent.putExtra("thread_id", messageRecord.getThreadId());
    intent.putExtra("message_id", messageRecord.getId());
    intent.putExtra("is_bundle", messageRecord.isBundleKeyExchange());
    intent.putExtra("is_identity_update", messageRecord.isIdentityUpdate());
    intent.putExtra("is_push", messageRecord.isPush());
    intent.putExtra("master_secret", masterSecret);
    intent.putExtra("sent", messageRecord.isOutgoing());
    context.startActivity(intent);
  }

  private class ThumbnailClickListener implements View.OnClickListener {
    private final Slide slide;

    public ThumbnailClickListener(Slide slide) {
      this.slide = slide;
    }

    private void fireIntent() {
      Log.w(TAG, "Clicked: " + slide.getUri() + " , " + slide.getContentType());
      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      intent.setDataAndType(PartAuthority.getPublicPartUri(slide.getUri()), slide.getContentType());
      try {
        context.startActivity(intent);
      } catch (ActivityNotFoundException anfe) {
        Log.w(TAG, "No activity existed to view the media.");
        Toast.makeText(context, R.string.ConversationItem_unable_to_open_media, Toast.LENGTH_LONG).show();
      }
    }

    public void onClick(View v) {
      if (!batchSelected.isEmpty()) {
        selectionClickListener.onItemClick(null, ConversationItem.this, -1, -1);
      } else if (MediaPreviewActivity.isContentTypeSupported(slide.getContentType())) {
        Intent intent = new Intent(context, MediaPreviewActivity.class);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(slide.getUri(), slide.getContentType());
        intent.putExtra(MediaPreviewActivity.MASTER_SECRET_EXTRA, masterSecret);
        if (!messageRecord.isOutgoing()) intent.putExtra(MediaPreviewActivity.RECIPIENT_EXTRA, messageRecord.getIndividualRecipient().getRecipientId());
        intent.putExtra(MediaPreviewActivity.DATE_EXTRA, messageRecord.getDateReceived());
        context.startActivity(intent);
      } else {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.ConversationItem_view_secure_media_question);
        builder.setIcon(Dialogs.resolveIcon(context, R.attr.dialog_alert_icon));
        builder.setCancelable(true);
        builder.setMessage(R.string.ConversationItem_this_media_has_been_stored_in_an_encrypted_database_external_viewer_warning);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int which) {
            fireIntent();
          }
        });
        builder.setNegativeButton(R.string.no, null);
        builder.show();
      }
    }
  }

  private class MmsDownloadClickListener implements View.OnClickListener {
    public void onClick(View v) {
      NotificationMmsMessageRecord notificationRecord = (NotificationMmsMessageRecord)messageRecord;
      Log.w("MmsDownloadClickListener", "Content location: " + new String(notificationRecord.getContentLocation()));
      mmsDownloadButton.setVisibility(View.GONE);
      mmsDownloadingLabel.setVisibility(View.VISIBLE);

      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MmsDownloadJob(context, messageRecord.getId(),
                                                messageRecord.getThreadId(), false));
    }
  }

  private class MmsPreferencesClickListener implements View.OnClickListener {
    public void onClick(View v) {
      Intent intent = new Intent(context, PromptMmsActivity.class);
      intent.putExtra("message_id", messageRecord.getId());
      intent.putExtra("thread_id", messageRecord.getThreadId());
      intent.putExtra("automatic", true);
      context.startActivity(intent);
    }
  }

  private class FailedIconClickListener implements View.OnClickListener {
    public void onClick(View v) {
      if (failedIconHandler != null && !messageRecord.isKeyExchange()) {
        Message message = Message.obtain();
        message.obj     = messageRecord.getBody().getBody();
        failedIconHandler.dispatchMessage(message);
      }
    }
  }

  private class ClickListener implements View.OnClickListener {
    public void onClick(View v) {
      if (messageRecord.isKeyExchange()           &&
          !messageRecord.isOutgoing()             &&
          !messageRecord.isProcessedKeyExchange() &&
          !messageRecord.isStaleKeyExchange())
        handleKeyExchangeClicked();
      else if (messageRecord.isPendingSmsFallback())
        handleMessageApproval();
    }
  }

  private class MultiSelectLongClickListener implements OnLongClickListener, OnClickListener {
    @Override
    public boolean onLongClick(View view) {
      selectionClickListener.onItemLongClick(null, ConversationItem.this, -1, -1);
      return true;
    }

    @Override
    public void onClick(View view) {
      selectionClickListener.onItemClick(null, ConversationItem.this, -1, -1);
    }
  }

  private void handleMessageApproval() {
    final int title;
    final int message;

    if (messageRecord.isPendingSecureSmsFallback()) {
      if (messageRecord.isMms()) title = R.string.ConversationItem_click_to_approve_mms_dialog_title;
      else                       title = R.string.ConversationItem_click_to_approve_sms_dialog_title;

      message = -1;
    } else {
      if (messageRecord.isMms()) title = R.string.ConversationItem_click_to_approve_unencrypted_mms_dialog_title;
      else                       title = R.string.ConversationItem_click_to_approve_unencrypted_sms_dialog_title;

      message = R.string.ConversationItem_click_to_approve_unencrypted_dialog_message;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(title);

    if (message > -1) builder.setMessage(message);

    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialogInterface, int i) {
        if (messageRecord.isMms()) {
          MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
          if (messageRecord.isPendingInsecureSmsFallback()) {
            database.markAsInsecure(messageRecord.getId());
          }
          database.markAsOutbox(messageRecord.getId());
          database.markAsForcedSms(messageRecord.getId());

          ApplicationContext.getInstance(context)
                            .getJobManager()
                            .add(new MmsSendJob(context, messageRecord.getId()));
        } else {
          SmsDatabase database = DatabaseFactory.getSmsDatabase(context);
          if (messageRecord.isPendingInsecureSmsFallback()) {
            database.markAsInsecure(messageRecord.getId());
          }
          database.markAsOutbox(messageRecord.getId());
          database.markAsForcedSms(messageRecord.getId());

          ApplicationContext.getInstance(context)
                            .getJobManager()
                            .add(new SmsSendJob(context, messageRecord.getId(),
                                                messageRecord.getIndividualRecipient().getNumber()));
        }
      }
    });

    builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialogInterface, int i) {
        if (messageRecord.isMms()) {
          DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageRecord.getId());
        } else {
          DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageRecord.getId());
        }
      }
    });
    builder.show();
  }
}
