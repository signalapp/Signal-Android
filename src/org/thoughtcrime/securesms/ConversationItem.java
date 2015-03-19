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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.provider.ContactsContract;
import android.provider.ContactsContract.QuickContact;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.thoughtcrime.securesms.ConversationFragment.SelectionClickListener;
import org.thoughtcrime.securesms.components.BubbleContainer;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.NotificationMmsMessageRecord;
import org.thoughtcrime.securesms.jobs.MmsDownloadJob;
import org.thoughtcrime.securesms.jobs.MmsSendJob;
import org.thoughtcrime.securesms.jobs.SmsSendJob;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.Emoji;

import java.util.Locale;
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

  private MessageRecord messageRecord;
  private MasterSecret  masterSecret;
  private Locale        locale;
  private boolean       groupThread;
  private boolean       pushDestination;

  private View            bodyBubble;
  private TextView        bodyText;
  private TextView        dateText;
  private TextView        indicatorText;
  private TextView        groupStatusText;
  private ImageView       secureImage;
  private ImageView       failedImage;
  private ImageView       contactPhoto;
  private ImageView       deliveryImage;
  private ImageView       pendingIndicator;
  private BubbleContainer bubbleContainer;

  private Set<MessageRecord>     batchSelected;
  private SelectionClickListener selectionClickListener;
  private ThumbnailView          mediaThumbnail;
  private Button                 mmsDownloadButton;
  private TextView               mmsDownloadingLabel;

  private final MmsDownloadClickListener    mmsDownloadClickListener    = new MmsDownloadClickListener();
  private final MmsPreferencesClickListener mmsPreferencesClickListener = new MmsPreferencesClickListener();
  private final ClickListener               clickListener               = new ClickListener();
  private final Context                     context;

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
    this.mmsDownloadButton   = (Button)   findViewById(R.id.mms_download_button);
    this.mmsDownloadingLabel = (TextView) findViewById(R.id.mms_label_downloading);
    this.contactPhoto        = (ImageView)findViewById(R.id.contact_photo);
    this.deliveryImage       = (ImageView)findViewById(R.id.delivered_indicator);
    this.bodyBubble          =            findViewById(R.id.body_bubble);
    this.pendingIndicator    = (ImageView)findViewById(R.id.pending_approval_indicator);
    this.bubbleContainer     = (BubbleContainer)findViewById(R.id.bubble);
    this.mediaThumbnail      = (ThumbnailView)findViewById(R.id.image_view);

    setOnClickListener(clickListener);
    if (mmsDownloadButton != null) mmsDownloadButton.setOnClickListener(mmsDownloadClickListener);
    if (mediaThumbnail != null) {
      mediaThumbnail.setThumbnailClickListener(new ThumbnailClickListener());
      mediaThumbnail.setOnLongClickListener(new MultiSelectLongClickListener());
    }
  }

  public void set(@NonNull MasterSecret masterSecret,
                  @NonNull MessageRecord messageRecord,
                  @NonNull Locale locale,
                  @NonNull Set<MessageRecord> batchSelected,
                  @NonNull SelectionClickListener selectionClickListener,
                  boolean groupThread, boolean pushDestination)
  {
    this.masterSecret           = masterSecret;
    this.messageRecord          = messageRecord;
    this.locale                 = locale;
    this.batchSelected          = batchSelected;
    this.selectionClickListener = selectionClickListener;
    this.groupThread            = groupThread;
    this.pushDestination        = pushDestination;

    setSelectionBackgroundDrawables(messageRecord);
    setBodyText(messageRecord);

    if (hasConversationBubble(messageRecord)) {
      setBubbleState(messageRecord);
      setStatusIcons(messageRecord);
      setContactPhoto(messageRecord);
      setGroupMessageStatus(messageRecord);
      setEvents(messageRecord);
      setMinimumWidth();
      setMediaAttributes(messageRecord);
    }
  }

  public void unbind() {
  }

  public MessageRecord getMessageRecord() {
    return messageRecord;
  }

  /// MessageRecord Attribute Parsers

  private void setBubbleState(MessageRecord messageRecord) {
    final int transportationState;
    if ((messageRecord.isPending() || messageRecord.isFailed()) &&
        pushDestination                                         &&
        !messageRecord.isForcedSms())
    {
      transportationState = BubbleContainer.TRANSPORT_STATE_PUSH_PENDING;
    } else if (messageRecord.isPending() ||
               messageRecord.isFailed()  ||
               messageRecord.isPendingInsecureSmsFallback())
    {
      transportationState = BubbleContainer.TRANSPORT_STATE_SMS_PENDING;
    } else if (messageRecord.isPush()) {
      transportationState = BubbleContainer.TRANSPORT_STATE_PUSH_SENT;
    } else {
      transportationState = BubbleContainer.TRANSPORT_STATE_SMS_SENT;
    }

    final int mediaCaptionState;
    if (!hasMedia(messageRecord)) {
      mediaCaptionState = BubbleContainer.MEDIA_STATE_NO_MEDIA;
    } else if (isCaptionlessMms(messageRecord)) {
      mediaCaptionState = BubbleContainer.MEDIA_STATE_CAPTIONLESS;
    } else {
      mediaCaptionState = BubbleContainer.MEDIA_STATE_CAPTIONED;
    }

    bubbleContainer.setState(transportationState, mediaCaptionState);
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

  private boolean hasConversationBubble(MessageRecord messageRecord) {
    return !messageRecord.isGroupAction();
  }

  private boolean isCaptionlessMms(MessageRecord messageRecord) {
    return TextUtils.isEmpty(messageRecord.getDisplayBody()) && messageRecord.isMms();
  }

  private boolean hasMedia(MessageRecord messageRecord) {
    return messageRecord.isMms()              &&
           !messageRecord.isMmsNotification() &&
           ((MediaMmsMessageRecord)messageRecord).getPartCount() > 0;
  }

  private void setBodyText(MessageRecord messageRecord) {
    bodyText.setClickable(false);
    bodyText.setFocusable(false);

    if (isCaptionlessMms(messageRecord)) {
      bodyText.setVisibility(View.GONE);
    } else {
      bodyText.setText(Emoji.getInstance(context).emojify(messageRecord.getDisplayBody(),
                                                          new Emoji.InvalidatingPageLoadedListener(bodyText)),
                       TextView.BufferType.SPANNABLE);
      bodyText.setVisibility(View.VISIBLE);
    }

    if (bodyText.isClickable() && bodyText.isFocusable()) {
      bodyText.setOnLongClickListener(new MultiSelectLongClickListener());
      bodyText.setOnClickListener(new MultiSelectLongClickListener());
    }
  }

  private void setMediaAttributes(MessageRecord messageRecord) {
    if (messageRecord.isMmsNotification()) {
      setNotificationMmsAttributes((NotificationMmsMessageRecord) messageRecord);
    } else if (messageRecord.isMms()) {
      resolveMedia((MediaMmsMessageRecord) messageRecord);
    }
  }

  private void setContactPhoto(MessageRecord messageRecord) {
    if (! messageRecord.isOutgoing()) {
      setContactPhotoForRecipient(messageRecord.getIndividualRecipient());
    }
  }

  private void setStatusIcons(MessageRecord messageRecord) {
    failedImage.setVisibility(messageRecord.isFailed() ? View.VISIBLE : View.GONE);
    if (messageRecord.isOutgoing()) pendingIndicator.setVisibility(View.GONE);
    if (messageRecord.isOutgoing()) indicatorText.setVisibility(View.GONE);

    secureImage.setVisibility(messageRecord.isSecure() ? View.VISIBLE : View.GONE);
    bodyText.setCompoundDrawablesWithIntrinsicBounds(0, 0, messageRecord.isKeyExchange() ? R.drawable.ic_menu_login : 0, 0);
    deliveryImage.setVisibility(!messageRecord.isKeyExchange() && messageRecord.isDelivered() ? View.VISIBLE : View.GONE);

    mmsDownloadButton.setVisibility(View.GONE);
    mmsDownloadingLabel.setVisibility(View.GONE);

    if      (messageRecord.isFailed())                     setFailedStatusIcons();
    else if (messageRecord.isPendingInsecureSmsFallback()) setFallbackStatusIcons();
    else if (messageRecord.isPending())                    dateText.setText(" ··· ");
    else                                                   setSentStatusIcons();
  }

  private void setSentStatusIcons() {
    final long timestamp;
    if (messageRecord.isPush()) timestamp = messageRecord.getDateSent();
    else                        timestamp = messageRecord.getDateReceived();

    dateText.setText(DateUtils.getExtendedRelativeTimeSpanString(getContext(), locale, timestamp));
  }

  private void setFailedStatusIcons() {
    dateText.setText(R.string.ConversationItem_error_not_delivered);
    if (indicatorText != null) {
      indicatorText.setText(R.string.ConversationItem_click_for_details);
      indicatorText.setVisibility(View.VISIBLE);
    }
  }

  private void setFallbackStatusIcons() {
    pendingIndicator.setVisibility(View.VISIBLE);
    indicatorText.setVisibility(View.VISIBLE);
    indicatorText.setText(R.string.ConversationItem_click_to_approve_unencrypted);
  }

  private void setMinimumWidth() {
    if (indicatorText != null && indicatorText.getVisibility() == View.VISIBLE && indicatorText.getText() != null) {
      final float density = getResources().getDisplayMetrics().density;
      bodyBubble.setMinimumWidth(indicatorText.getText().length() * (int) (6.5 * density));
    } else {
      bodyBubble.setMinimumWidth(0);
    }
  }

  private void setEvents(MessageRecord messageRecord) {
    setClickable(batchSelected.isEmpty() &&
                 (messageRecord.isFailed()                     ||
                  messageRecord.isPendingInsecureSmsFallback() ||
                  messageRecord.isBundleKeyExchange()));
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

  private void resolveMedia(MediaMmsMessageRecord messageRecord) {
    if (hasMedia(messageRecord)) {
      mediaThumbnail.setImageResource(messageRecord.getSlideDeckFuture(), masterSecret);
    }
  }

  /// Helper Methods

  private void setContactPhotoForRecipient(final Recipient recipient) {
    if (contactPhoto == null) return;

    Bitmap contactPhotoBitmap;

    if ((recipient.getContactPhoto() == ContactPhotoFactory.getDefaultContactPhoto(context)) && (groupThread)) {
      contactPhotoBitmap = recipient.getGeneratedAvatar(context);
    } else {
      contactPhotoBitmap = recipient.getContactPhoto();
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
    intent.putExtra("sent", messageRecord.isOutgoing());
    context.startActivity(intent);
  }

  private class ThumbnailClickListener implements ThumbnailView.ThumbnailClickListener {
    private void fireIntent(Slide slide) {
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

    public void onClick(final View v, final Slide slide) {
      if (!batchSelected.isEmpty()) {
        selectionClickListener.onItemClick(null, ConversationItem.this, -1, -1);
      } else if (MediaPreviewActivity.isContentTypeSupported(slide.getContentType())) {
        Intent intent = new Intent(context, MediaPreviewActivity.class);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(slide.getUri(), slide.getContentType());
        if (!messageRecord.isOutgoing()) intent.putExtra(MediaPreviewActivity.RECIPIENT_EXTRA, messageRecord.getIndividualRecipient().getRecipientId());
        intent.putExtra(MediaPreviewActivity.DATE_EXTRA, messageRecord.getDateReceived());

        context.startActivity(intent);
      } else {
        AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(context);
        builder.setTitle(R.string.ConversationItem_view_secure_media_question);
        builder.setIconAttribute(R.attr.dialog_alert_icon);
        builder.setCancelable(true);
        builder.setMessage(R.string.ConversationItem_this_media_has_been_stored_in_an_encrypted_database_external_viewer_warning);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int which) {
            fireIntent(slide);
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
      Log.w(TAG, "Content location: " + new String(notificationRecord.getContentLocation()));
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

  private class ClickListener implements View.OnClickListener {
    public void onClick(View v) {
      if (messageRecord.isFailed()) {
        Intent intent = new Intent(context, MessageDetailsActivity.class);
        intent.putExtra(MessageDetailsActivity.MASTER_SECRET_EXTRA, masterSecret);
        intent.putExtra(MessageDetailsActivity.MESSAGE_ID_EXTRA, messageRecord.getId());
        intent.putExtra(MessageDetailsActivity.TYPE_EXTRA, messageRecord.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
        intent.putExtra(MessageDetailsActivity.IS_PUSH_GROUP_EXTRA, groupThread && pushDestination);
        context.startActivity(intent);
      } else if (messageRecord.isKeyExchange()           &&
                 !messageRecord.isOutgoing()             &&
                 !messageRecord.isProcessedKeyExchange() &&
                 !messageRecord.isStaleKeyExchange())
      {
        handleKeyExchangeClicked();
      } else if (messageRecord.isPendingInsecureSmsFallback()) {
        handleMessageApproval();
      }
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

    if (messageRecord.isMms()) title = R.string.ConversationItem_click_to_approve_unencrypted_mms_dialog_title;
    else                       title = R.string.ConversationItem_click_to_approve_unencrypted_sms_dialog_title;

    message = R.string.ConversationItem_click_to_approve_unencrypted_dialog_message;

    AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(context);
    builder.setTitle(title);

    if (message > -1) builder.setMessage(message);

    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialogInterface, int i) {
        if (messageRecord.isMms()) {
          MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
          database.markAsInsecure(messageRecord.getId());
          database.markAsOutbox(messageRecord.getId());
          database.markAsForcedSms(messageRecord.getId());

          ApplicationContext.getInstance(context)
                            .getJobManager()
                            .add(new MmsSendJob(context, messageRecord.getId()));
        } else {
          SmsDatabase database = DatabaseFactory.getSmsDatabase(context);
          database.markAsInsecure(messageRecord.getId());
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
