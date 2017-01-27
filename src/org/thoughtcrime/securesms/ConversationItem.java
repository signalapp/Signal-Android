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
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.components.AlertView;
import org.thoughtcrime.securesms.components.AudioView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.DeliveryStatusView;
import org.thoughtcrime.securesms.components.ExpirationTimerView;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.jobs.MmsDownloadJob;
import org.thoughtcrime.securesms.jobs.MmsSendJob;
import org.thoughtcrime.securesms.jobs.SmsSendJob;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideClickListener;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.dualsim.SubscriptionInfoCompat;
import org.thoughtcrime.securesms.util.dualsim.SubscriptionManagerCompat;
import org.thoughtcrime.securesms.util.views.Stub;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.HashSet;
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
    implements Recipient.RecipientModifiedListener, Recipients.RecipientsModifiedListener, BindableConversationItem
{
  private final static String TAG = ConversationItem.class.getSimpleName();

  private MessageRecord messageRecord;
  private MasterSecret  masterSecret;
  private Locale        locale;
  private boolean       groupThread;
  private Recipient     recipient;

  protected View             bodyBubble;
  private TextView           bodyText;
  private TextView           dateText;
  private TextView           simInfoText;
  private TextView           indicatorText;
  private TextView           groupStatusText;
  private ImageView          secureImage;
  private AvatarImageView    contactPhoto;
  private DeliveryStatusView deliveryStatusIndicator;
  private AlertView          alertView;

  private @NonNull  Set<MessageRecord>  batchSelected = new HashSet<>();
  private @Nullable Recipients          conversationRecipients;
  private @NonNull  Stub<ThumbnailView> mediaThumbnailStub;
  private @NonNull  Stub<AudioView>     audioViewStub;
  private @NonNull  ExpirationTimerView expirationTimer;

  private int defaultBubbleColor;

  private final PassthroughClickListener        passthroughClickListener    = new PassthroughClickListener();
  private final AttachmentDownloadClickListener downloadClickListener       = new AttachmentDownloadClickListener();

  private final Context context;

  public ConversationItem(Context context) {
    this(context, null);
  }

  public ConversationItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  @Override
  public void setOnClickListener(OnClickListener l) {
    super.setOnClickListener(new ClickListener(l));
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    initializeAttributes();

    this.bodyText                = (TextView)           findViewById(R.id.conversation_item_body);
    this.dateText                = (TextView)           findViewById(R.id.conversation_item_date);
    this.simInfoText             = (TextView)           findViewById(R.id.sim_info);
    this.indicatorText           = (TextView)           findViewById(R.id.indicator_text);
    this.groupStatusText         = (TextView)           findViewById(R.id.group_message_status);
    this.secureImage             = (ImageView)          findViewById(R.id.secure_indicator);
    this.deliveryStatusIndicator = (DeliveryStatusView) findViewById(R.id.delivery_status);
    this.alertView               = (AlertView)          findViewById(R.id.indicators_parent);
    this.contactPhoto            = (AvatarImageView)    findViewById(R.id.contact_photo);
    this.bodyBubble              =                      findViewById(R.id.body_bubble);
    this.mediaThumbnailStub      = new Stub<>((ViewStub) findViewById(R.id.image_view_stub));
    this.audioViewStub           = new Stub<>((ViewStub) findViewById(R.id.audio_view_stub));
    this.expirationTimer         = (ExpirationTimerView) findViewById(R.id.expiration_indicator);

    setOnClickListener(new ClickListener(null));

    bodyText.setOnLongClickListener(passthroughClickListener);
    bodyText.setOnClickListener(passthroughClickListener);
  }

  @Override
  public void bind(@NonNull MasterSecret       masterSecret,
                   @NonNull MessageRecord      messageRecord,
                   @NonNull Locale             locale,
                   @NonNull Set<MessageRecord> batchSelected,
                   @NonNull Recipients         conversationRecipients)
  {
    this.masterSecret           = masterSecret;
    this.messageRecord          = messageRecord;
    this.locale                 = locale;
    this.batchSelected          = batchSelected;
    this.conversationRecipients = conversationRecipients;
    this.groupThread            = !conversationRecipients.isSingleRecipient() || conversationRecipients.isGroupRecipient();
    this.recipient              = messageRecord.getIndividualRecipient();

    this.recipient.addListener(this);
    this.conversationRecipients.addListener(this);

    setMediaAttributes(messageRecord);
    setInteractionState(messageRecord);
    setBodyText(messageRecord);
    setBubbleState(messageRecord, recipient);
    setStatusIcons(messageRecord);
    setContactPhoto(recipient);
    setGroupMessageStatus(messageRecord, recipient);
    setMinimumWidth();
    setSimInfo(messageRecord);
    setExpiration(messageRecord);
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

    this.expirationTimer.stopAnimation();
  }

  public MessageRecord getMessageRecord() {
    return messageRecord;
  }

  /// MessageRecord Attribute Parsers

  private void setBubbleState(MessageRecord messageRecord, Recipient recipient) {
    if (messageRecord.isOutgoing()) {
      bodyBubble.getBackground().setColorFilter(defaultBubbleColor, PorterDuff.Mode.MULTIPLY);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setBackgroundColorHint(defaultBubbleColor);
    } else {
      int color = recipient.getColor().toConversationColor(context);
      bodyBubble.getBackground().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setBackgroundColorHint(color);
    }

    if (audioViewStub.resolved()) {
      setAudioViewTint(messageRecord, conversationRecipients);
    }
  }

  private void setAudioViewTint(MessageRecord messageRecord, Recipients recipients) {
    if (messageRecord.isOutgoing()) {
      if (DynamicTheme.LIGHT.equals(TextSecurePreferences.getTheme(context))) {
        audioViewStub.get().setTint(recipients.getColor().toConversationColor(context), defaultBubbleColor);
      } else {
        audioViewStub.get().setTint(Color.WHITE, defaultBubbleColor);
      }
    } else {
      audioViewStub.get().setTint(Color.WHITE, recipients.getColor().toConversationColor(context));
    }
  }

  private void setInteractionState(MessageRecord messageRecord) {
    setSelected(batchSelected.contains(messageRecord));
    bodyText.setAutoLinkMask(batchSelected.isEmpty() ? Linkify.ALL : 0);

    if (mediaThumbnailStub.resolved()) {
      mediaThumbnailStub.get().setFocusable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
      mediaThumbnailStub.get().setClickable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
      mediaThumbnailStub.get().setLongClickable(batchSelected.isEmpty());
    }

    if (audioViewStub.resolved()) {
      audioViewStub.get().setFocusable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
      audioViewStub.get().setClickable(batchSelected.isEmpty());
      audioViewStub.get().setEnabled(batchSelected.isEmpty());
    }
  }

  private boolean isCaptionlessMms(MessageRecord messageRecord) {
    return TextUtils.isEmpty(messageRecord.getDisplayBody()) && messageRecord.isMms();
  }

  private boolean hasAudio(MessageRecord messageRecord) {
    return messageRecord.isMms() && ((MmsMessageRecord)messageRecord).getSlideDeck().getAudioSlide() != null;
  }

  private boolean hasThumbnail(MessageRecord messageRecord) {
    return messageRecord.isMms() && ((MmsMessageRecord)messageRecord).getSlideDeck().getThumbnailSlide() != null;
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
    boolean showControls = !messageRecord.isFailed() && (!messageRecord.isOutgoing() || messageRecord.isPending());

    if (hasAudio(messageRecord)) {
      audioViewStub.get().setVisibility(View.VISIBLE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);

      //noinspection ConstantConditions
      audioViewStub.get().setAudio(masterSecret, ((MediaMmsMessageRecord) messageRecord).getSlideDeck().getAudioSlide(), showControls);
      audioViewStub.get().setDownloadClickListener(downloadClickListener);
      audioViewStub.get().setOnLongClickListener(passthroughClickListener);

      bodyText.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    } else if (hasThumbnail(messageRecord)) {
      mediaThumbnailStub.get().setVisibility(View.VISIBLE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);

      //noinspection ConstantConditions
      mediaThumbnailStub.get().setImageResource(masterSecret,
                                                ((MmsMessageRecord)messageRecord).getSlideDeck().getThumbnailSlide(),
                                                showControls);
      mediaThumbnailStub.get().setThumbnailClickListener(new ThumbnailClickListener());
      mediaThumbnailStub.get().setDownloadClickListener(downloadClickListener);
      mediaThumbnailStub.get().setOnLongClickListener(passthroughClickListener);
      mediaThumbnailStub.get().setOnClickListener(passthroughClickListener);

      bodyText.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    } else {
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (audioViewStub.resolved())      audioViewStub.get().setVisibility(View.GONE);
      bodyText.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
  }

  private void setContactPhoto(Recipient recipient) {
    if (! messageRecord.isOutgoing()) {
      setContactPhotoForRecipient(recipient);
    }
  }

  private void setStatusIcons(MessageRecord messageRecord) {
    indicatorText.setVisibility(View.GONE);

    secureImage.setVisibility(messageRecord.isSecure() ? View.VISIBLE : View.GONE);
    bodyText.setCompoundDrawablesWithIntrinsicBounds(0, 0, messageRecord.isKeyExchange() ? R.drawable.ic_menu_login : 0, 0);
    dateText.setText(DateUtils.getExtendedRelativeTimeSpanString(getContext(), locale, messageRecord.getTimestamp()));

    if (messageRecord.isFailed()) {
      setFailedStatusIcons();
    } else if (messageRecord.isPendingInsecureSmsFallback()) {
      setFallbackStatusIcons();
    } else {
      alertView.setNone();

      if      (!messageRecord.isOutgoing()) deliveryStatusIndicator.setNone();
      else if (messageRecord.isPending())   deliveryStatusIndicator.setPending();
      else if (messageRecord.isDelivered()) deliveryStatusIndicator.setDelivered();
      else                                  deliveryStatusIndicator.setSent();
    }
  }

  private void setSimInfo(MessageRecord messageRecord) {
    SubscriptionManagerCompat subscriptionManager = new SubscriptionManagerCompat(context);

    if (messageRecord.getSubscriptionId() == -1 || subscriptionManager.getActiveSubscriptionInfoList().size() < 2) {
      simInfoText.setVisibility(View.GONE);
    } else {
      Optional<SubscriptionInfoCompat> subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(messageRecord.getSubscriptionId());

      if (subscriptionInfo.isPresent() && messageRecord.isOutgoing()) {
        simInfoText.setText(getContext().getString(R.string.ConversationItem_from_s, subscriptionInfo.get().getDisplayName()));
        simInfoText.setVisibility(View.VISIBLE);
      } else if (subscriptionInfo.isPresent()) {
        simInfoText.setText(getContext().getString(R.string.ConversationItem_to_s,  subscriptionInfo.get().getDisplayName()));
        simInfoText.setVisibility(View.VISIBLE);
      } else {
        simInfoText.setVisibility(View.GONE);
      }
    }
  }

  private void setExpiration(final MessageRecord messageRecord) {
    if (messageRecord.getExpiresIn() > 0) {
      this.expirationTimer.setVisibility(View.VISIBLE);
      this.expirationTimer.setPercentage(0);

      if (messageRecord.getExpireStarted() > 0) {
        this.expirationTimer.setExpirationTime(messageRecord.getExpireStarted(),
                                               messageRecord.getExpiresIn());
        this.expirationTimer.startAnimation();
      } else if (!messageRecord.isOutgoing() && !messageRecord.isMediaPending()) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
            long                   id                = messageRecord.getId();
            boolean                mms               = messageRecord.isMms();

            if (mms) DatabaseFactory.getMmsDatabase(context).markExpireStarted(id);
            else     DatabaseFactory.getSmsDatabase(context).markExpireStarted(id);

            expirationManager.scheduleDeletion(id, mms, messageRecord.getExpiresIn());
            return null;
          }
        }.execute();
      }
    } else {
      this.expirationTimer.setVisibility(View.GONE);
    }
  }

  private void setFailedStatusIcons() {
    alertView.setFailed();
    deliveryStatusIndicator.setNone();
    dateText.setText(R.string.ConversationItem_error_not_delivered);

    if (messageRecord.isOutgoing()) {
      indicatorText.setText(R.string.ConversationItem_click_for_details);
      indicatorText.setVisibility(View.VISIBLE);
    }
  }

  private void setFallbackStatusIcons() {
    alertView.setPendingApproval();
    deliveryStatusIndicator.setNone();
    indicatorText.setVisibility(View.VISIBLE);
    indicatorText.setText(R.string.ConversationItem_click_to_approve_unencrypted);
  }

  private void setMinimumWidth() {
    if (indicatorText.getVisibility() == View.VISIBLE && indicatorText.getText() != null) {
      final float density = getResources().getDisplayMetrics().density;
      bodyBubble.setMinimumWidth(indicatorText.getText().length() * (int) (6.5 * density) + (int) (22.0 * density));
    } else {
      bodyBubble.setMinimumWidth(0);
    }
  }

  private boolean shouldInterceptClicks(MessageRecord messageRecord) {
    return batchSelected.isEmpty() &&
            ((messageRecord.isFailed() && !messageRecord.isMmsNotification()) ||
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

  @Override
  public void onModified(final Recipients recipients) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        setAudioViewTint(messageRecord, recipients);
      }
    });
  }

  private class AttachmentDownloadClickListener implements SlideClickListener {
    @Override
    public void onClick(View v, final Slide slide) {
      if (messageRecord.isMmsNotification()) {
        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new MmsDownloadJob(context, messageRecord.getId(),
                                                  messageRecord.getThreadId(), false));
      } else {
        DatabaseFactory.getAttachmentDatabase(context).setTransferState(messageRecord.getId(),
                                                                        slide.asAttachment(),
                                                                        AttachmentDatabase.TRANSFER_PROGRESS_STARTED);
      }
    }
  }

  private class ThumbnailClickListener implements SlideClickListener {
    private void fireIntent(Slide slide) {
      Log.w(TAG, "Clicked: " + slide.getUri() + " , " + slide.getContentType());
      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      intent.setDataAndType(PartAuthority.getAttachmentPublicUri(slide.getUri()), slide.getContentType());
      try {
        context.startActivity(intent);
      } catch (ActivityNotFoundException anfe) {
        Log.w(TAG, "No activity existed to view the media.");
        Toast.makeText(context, R.string.ConversationItem_unable_to_open_media, Toast.LENGTH_LONG).show();
      }
    }

    public void onClick(final View v, final Slide slide) {
      if (shouldInterceptClicks(messageRecord) || !batchSelected.isEmpty()) {
        performClick();
      } else if (MediaPreviewActivity.isContentTypeSupported(slide.getContentType()) && slide.getUri() != null) {
        Intent intent = new Intent(context, MediaPreviewActivity.class);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(slide.getUri(), slide.getContentType());
        if (!messageRecord.isOutgoing()) intent.putExtra(MediaPreviewActivity.RECIPIENT_EXTRA, recipient.getRecipientId());
        intent.putExtra(MediaPreviewActivity.DATE_EXTRA, messageRecord.getTimestamp());
        intent.putExtra(MediaPreviewActivity.SIZE_EXTRA, slide.asAttachment().getSize());
        intent.putExtra(MediaPreviewActivity.THREAD_ID_EXTRA, messageRecord.getThreadId());

        context.startActivity(intent);
      } else if (slide.getUri() != null) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
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

  private class PassthroughClickListener implements View.OnLongClickListener, View.OnClickListener {

    @Override
    public boolean onLongClick(View v) {
      performLongClick();
      return true;
    }

    @Override
    public void onClick(View v) {
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
        intent.putExtra(MessageDetailsActivity.THREAD_ID_EXTRA, messageRecord.getThreadId());
        intent.putExtra(MessageDetailsActivity.TYPE_EXTRA, messageRecord.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
        intent.putExtra(MessageDetailsActivity.IS_PUSH_GROUP_EXTRA, groupThread && messageRecord.isPush());
        intent.putExtra(MessageDetailsActivity.RECIPIENTS_IDS_EXTRA, conversationRecipients.getIds());
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

    AlertDialog.Builder builder = new AlertDialog.Builder(context);
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
