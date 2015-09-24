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
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.PartDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
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
import org.thoughtcrime.securesms.util.Util;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A view that displays an individual conversation item within a conversation
 * thread.  Used by ComposeMessageActivity's ListActivity via a ConversationAdapter.
 *
 * @author Moxie Marlinspike
 *
 */

public class ConversationItem extends LinearLayout
    implements Recipient.RecipientModifiedListener, BindableConversationItem
{
  private final static String TAG = ConversationItem.class.getSimpleName();

  private MessageRecord messageRecord;
  private MasterSecret  masterSecret;
  private Locale        locale;
  private boolean       groupThread;
  private boolean       pushDestination;
  private Recipient     recipient;

  private View            bodyBubble;
  private TextView        bodyText;
  private TextView        dateText;
  private TextView        indicatorText;
  private TextView        groupStatusText;
  private ImageView       secureImage;
  private AvatarImageView contactPhoto;
  private ImageView       failedIndicator;
  private ImageView       deliveredIndicator;
  private ImageView       sentIndicator;
  private View            pendingIndicator;
  private ImageView       pendingApprovalIndicator;

  private StatusManager      statusManager;
  private Set<MessageRecord> batchSelected;
  private ThumbnailView      mediaThumbnail;
  private Button             mmsDownloadButton;
  private TextView           mmsDownloadingLabel;

  private int defaultBubbleColor;

  private final MmsDownloadClickListener    mmsDownloadClickListener    = new MmsDownloadClickListener();
  private final MmsPreferencesClickListener mmsPreferencesClickListener = new MmsPreferencesClickListener();
  private final Context                     context;

  public ConversationItem(Context context) {
    this(context, null);
  }

  public ConversationItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  @Override public void setOnClickListener(OnClickListener l) {
    super.setOnClickListener(new ClickListener(l));
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    initializeAttributes();
    ViewGroup pendingIndicatorStub = (ViewGroup) findViewById(R.id.pending_indicator_stub);

    if (pendingIndicatorStub != null) {
      LayoutInflater inflater = LayoutInflater.from(context);
      if (Build.VERSION.SDK_INT >= 11) inflater.inflate(R.layout.conversation_item_pending_v11, pendingIndicatorStub, true);
      else                             inflater.inflate(R.layout.conversation_item_pending, pendingIndicatorStub, true);
    }

    this.bodyText                 = (TextView)        findViewById(R.id.conversation_item_body);
    this.dateText                 = (TextView)        findViewById(R.id.conversation_item_date);
    this.indicatorText            = (TextView)        findViewById(R.id.indicator_text);
    this.groupStatusText          = (TextView)        findViewById(R.id.group_message_status);
    this.secureImage              = (ImageView)       findViewById(R.id.secure_indicator);
    this.failedIndicator          = (ImageView)       findViewById(R.id.sms_failed_indicator);
    this.mmsDownloadButton        = (Button)          findViewById(R.id.mms_download_button);
    this.mmsDownloadingLabel      = (TextView)        findViewById(R.id.mms_label_downloading);
    this.contactPhoto             = (AvatarImageView) findViewById(R.id.contact_photo);
    this.deliveredIndicator       = (ImageView)       findViewById(R.id.delivered_indicator);
    this.sentIndicator            = (ImageView)       findViewById(R.id.sent_indicator);
    this.bodyBubble               =                   findViewById(R.id.body_bubble);
    this.pendingApprovalIndicator = (ImageView)       findViewById(R.id.pending_approval_indicator);
    this.pendingIndicator         =                   findViewById(R.id.pending_indicator);
    this.mediaThumbnail           = (ThumbnailView)   findViewById(R.id.image_view);
    this.statusManager            = new StatusManager(pendingIndicator, sentIndicator, deliveredIndicator, failedIndicator, pendingApprovalIndicator);

    setOnClickListener(new ClickListener(null));
    PassthroughClickListener passthroughClickListener = new PassthroughClickListener();
    if (mmsDownloadButton != null) mmsDownloadButton.setOnClickListener(mmsDownloadClickListener);
    mediaThumbnail.setThumbnailClickListener(new ThumbnailClickListener());
    mediaThumbnail.setDownloadClickListener(new ThumbnailDownloadClickListener());
    mediaThumbnail.setOnLongClickListener(passthroughClickListener);
    bodyText.setOnLongClickListener(passthroughClickListener);
    bodyText.setOnClickListener(passthroughClickListener);
  }

  @Override
  public void bind(@NonNull MasterSecret       masterSecret,
                   @NonNull MessageRecord      messageRecord,
                   @NonNull Locale             locale,
                   @NonNull Set<MessageRecord> batchSelected,
                   boolean groupThread, boolean pushDestination)
  {
    this.masterSecret           = masterSecret;
    this.messageRecord          = messageRecord;
    this.locale                 = locale;
    this.batchSelected          = batchSelected;
    this.groupThread            = groupThread;
    this.pushDestination        = pushDestination;
    this.recipient              = messageRecord.getIndividualRecipient();

    this.recipient.addListener(this);

    setInteractionState(messageRecord);
    setBodyText(messageRecord);
    setBubbleState(messageRecord, recipient);
    setStatusIcons(messageRecord);
    setContactPhoto(recipient);
    setGroupMessageStatus(messageRecord, recipient);
    setMinimumWidth();
    setMediaAttributes(messageRecord);
  }

  private void initializeAttributes() {
    final int[]      attributes = new int[] {R.attr.conversation_item_bubble_background,
                                             R.attr.conversation_list_item_background_selected,
                                             R.attr.conversation_item_background};
    final TypedArray attrs      = context.obtainStyledAttributes(attributes);

    defaultBubbleColor = attrs.getColor(0, Color.WHITE);
    attrs.recycle();
  }

  @Override
  public void unbind() {
    if (recipient != null) {
      recipient.removeListener(this);
    }
  }

  public MessageRecord getMessageRecord() {
    return messageRecord;
  }

  /// MessageRecord Attribute Parsers

  private void setBubbleState(MessageRecord messageRecord, Recipient recipient) {
    if (messageRecord.isOutgoing()) {
      bodyBubble.getBackground().setColorFilter(defaultBubbleColor, PorterDuff.Mode.MULTIPLY);
      mediaThumbnail.setBackgroundColorHint(defaultBubbleColor);
    } else {
      int color = recipient.getColor().toConversationColor(context);
      bodyBubble.getBackground().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
      mediaThumbnail.setBackgroundColorHint(color);
    }
  }

  private void setInteractionState(MessageRecord messageRecord) {
    setSelected(batchSelected.contains(messageRecord));
    mediaThumbnail.setClickable(!shouldInterceptClicks(messageRecord));
    mediaThumbnail.setLongClickable(batchSelected.isEmpty());
    bodyText.setAutoLinkMask(batchSelected.isEmpty() ? Linkify.ALL : 0);
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
      bodyText.setText(messageRecord.getDisplayBody());
      bodyText.setVisibility(View.VISIBLE);
    }
  }

  private void setMediaAttributes(MessageRecord messageRecord) {
    if (messageRecord.isMmsNotification()) {
      mediaThumbnail.setVisibility(View.GONE);
      bodyText.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
      setNotificationMmsAttributes((NotificationMmsMessageRecord) messageRecord);
    } else if (hasMedia(messageRecord)) {
      mediaThumbnail.setVisibility(View.VISIBLE);
      mediaThumbnail.setImageResource(masterSecret, messageRecord.getId(),
                                      messageRecord.getDateReceived(),
                                      ((MediaMmsMessageRecord)messageRecord).getSlideDeckFuture());
      mediaThumbnail.hideControls(messageRecord.isFailed() || (messageRecord.isOutgoing() && !messageRecord.isPending()));
      bodyText.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    } else {
      mediaThumbnail.setVisibility(View.GONE);
      bodyText.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
  }

  private void setContactPhoto(Recipient recipient) {
    if (! messageRecord.isOutgoing()) {
      setContactPhotoForRecipient(recipient);
    }
  }

  private void setStatusIcons(MessageRecord messageRecord) {
    mmsDownloadButton.setVisibility(View.GONE);
    mmsDownloadingLabel.setVisibility(View.GONE);
    indicatorText.setVisibility(View.GONE);

    secureImage.setVisibility(messageRecord.isSecure() ? View.VISIBLE : View.GONE);
    bodyText.setCompoundDrawablesWithIntrinsicBounds(0, 0, messageRecord.isKeyExchange() ? R.drawable.ic_menu_login : 0, 0);

    final long timestamp;

    if (messageRecord.isPush()) timestamp = messageRecord.getDateSent();
    else                        timestamp = messageRecord.getDateReceived();

    dateText.setText(DateUtils.getExtendedRelativeTimeSpanString(getContext(), locale, timestamp));

    if      (messageRecord.isFailed())                     setFailedStatusIcons();
    else if (messageRecord.isPendingInsecureSmsFallback()) setFallbackStatusIcons();
    else if (messageRecord.isPending())                    statusManager.displayPending();
    else if (messageRecord.isDelivered())                  statusManager.displayDelivered();
    else                                                   statusManager.displaySent();
  }

  private void setFailedStatusIcons() {
    statusManager.displayFailed();
    dateText.setText(R.string.ConversationItem_error_not_delivered);
    if (indicatorText != null) {
      indicatorText.setText(R.string.ConversationItem_click_for_details);
      indicatorText.setVisibility(View.VISIBLE);
    }
  }

  private void setFallbackStatusIcons() {
    statusManager.displayPendingApproval();
    indicatorText.setVisibility(View.VISIBLE);
    indicatorText.setText(R.string.ConversationItem_click_to_approve_unencrypted);
  }

  private void setMinimumWidth() {
    if (indicatorText != null && indicatorText.getVisibility() == View.VISIBLE && indicatorText.getText() != null) {
      final float density = getResources().getDisplayMetrics().density;
      bodyBubble.setMinimumWidth(indicatorText.getText().length() * (int) (6.5 * density) + (int) (22.0 * density));
    } else {
      bodyBubble.setMinimumWidth(0);
    }
  }

  private boolean shouldInterceptClicks(MessageRecord messageRecord) {
    return batchSelected.isEmpty() &&
           (messageRecord.isFailed() ||
            messageRecord.isPendingInsecureSmsFallback() ||
            messageRecord.isBundleKeyExchange());
  }

  private void setGroupMessageStatus(MessageRecord messageRecord, Recipient recipient) {
    if (groupThread && !messageRecord.isOutgoing()) {
      this.groupStatusText.setText(recipient.toShortString());
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

  /// Helper Methods

  private void setContactPhotoForRecipient(final Recipient recipient) {
    if (contactPhoto == null) return;

    contactPhoto.setAvatar(recipient, true);
    contactPhoto.setVisibility(View.VISIBLE);
  }

  /// Event handlers

  private void handleApproveIdentity() {
    List<IdentityKeyMismatch> mismatches = messageRecord.getIdentityKeyMismatches();

    if (mismatches.size() != 1) {
      throw new AssertionError("Identity mismatch count: " + mismatches.size());
    }

    new ConfirmIdentityDialog(context, masterSecret, messageRecord, mismatches.get(0)).show();
  }

  @Override
  public void onModified(final Recipient recipient) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        setBubbleState(messageRecord, recipient);
        setContactPhoto(recipient);
        setGroupMessageStatus(messageRecord, recipient);
      }
    });
  }

  private class ThumbnailDownloadClickListener implements ThumbnailView.ThumbnailClickListener {
    @Override public void onClick(View v, final Slide slide) {
      DatabaseFactory.getPartDatabase(context).setTransferState(messageRecord.getId(), slide.getPart().getPartId(), PartDatabase.TRANSFER_PROGRESS_STARTED);
    }
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
      if (batchSelected.isEmpty() &&
          MediaPreviewActivity.isContentTypeSupported(slide.getContentType()) &&
          slide.getThumbnailUri() != null)
      {
        Intent intent = new Intent(context, MediaPreviewActivity.class);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(slide.getUri(), slide.getContentType());
        if (!messageRecord.isOutgoing()) intent.putExtra(MediaPreviewActivity.RECIPIENT_EXTRA, recipient.getRecipientId());
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

  private class PassthroughClickListener implements View.OnLongClickListener, View.OnClickListener {

    @Override public boolean onLongClick(View v) {
      performLongClick();
      return true;
    }

    @Override public void onClick(View v) {
      performClick();
    }
  }
  private class ClickListener implements View.OnClickListener {
    private OnClickListener parent;

    public ClickListener(@Nullable OnClickListener parent) {
      this.parent = parent;
    }

    public void onClick(View v) {
      if (!shouldInterceptClicks(messageRecord) && parent != null) {
        parent.onClick(v);
      } else if (messageRecord.isFailed()) {
        Intent intent = new Intent(context, MessageDetailsActivity.class);
        intent.putExtra(MessageDetailsActivity.MASTER_SECRET_EXTRA, masterSecret);
        intent.putExtra(MessageDetailsActivity.MESSAGE_ID_EXTRA, messageRecord.getId());
        intent.putExtra(MessageDetailsActivity.TYPE_EXTRA, messageRecord.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
        intent.putExtra(MessageDetailsActivity.IS_PUSH_GROUP_EXTRA, groupThread && pushDestination);
        context.startActivity(intent);
      } else if (!messageRecord.isOutgoing() && messageRecord.isIdentityMismatchFailure()) {
        handleApproveIdentity();
      } else if (messageRecord.isPendingInsecureSmsFallback()) {
        handleMessageApproval();
      }
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

  private static class StatusManager {

    private final View pendingIndicator;
    private final View sentIndicator;
    private final View deliveredIndicator;

    private final View failedIndicator;
    private final View approvalIndicator;


    public StatusManager(View pendingIndicator, View sentIndicator,
                         View deliveredIndicator, View failedIndicator,
                         View approvalIndicator)
    {
      this.pendingIndicator   = pendingIndicator;
      this.sentIndicator      = sentIndicator;
      this.deliveredIndicator = deliveredIndicator;
      this.failedIndicator    = failedIndicator;
      this.approvalIndicator  = approvalIndicator;
    }

    public void displayFailed() {
      pendingIndicator.setVisibility(View.GONE);
      sentIndicator.setVisibility(View.GONE);
      deliveredIndicator.setVisibility(View.GONE);
      approvalIndicator.setVisibility(View.GONE);

      failedIndicator.setVisibility(View.VISIBLE);
    }

    public void displayPendingApproval() {
      pendingIndicator.setVisibility(View.GONE);
      sentIndicator.setVisibility(View.GONE);
      deliveredIndicator.setVisibility(View.GONE);
      failedIndicator.setVisibility(View.GONE);

      approvalIndicator.setVisibility(View.VISIBLE);
    }

    public void displayPending() {
      sentIndicator.setVisibility(View.GONE);
      deliveredIndicator.setVisibility(View.GONE);
      failedIndicator.setVisibility(View.GONE);
      approvalIndicator.setVisibility(View.GONE);

      pendingIndicator.setVisibility(View.VISIBLE);
    }

    public void displaySent() {
      pendingIndicator.setVisibility(View.GONE);
      deliveredIndicator.setVisibility(View.GONE);
      failedIndicator.setVisibility(View.GONE);
      approvalIndicator.setVisibility(View.GONE);

      sentIndicator.setVisibility(View.VISIBLE);
    }

    public void displayDelivered() {
      pendingIndicator.setVisibility(View.GONE);
      failedIndicator.setVisibility(View.GONE);
      approvalIndicator.setVisibility(View.GONE);
      sentIndicator.setVisibility(View.GONE);

      deliveredIndicator.setVisibility(View.VISIBLE);
    }

  }

}
