/*
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
package org.thoughtcrime.securesms.conversation;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.net.Uri;
import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.BindableConversationItem;
import org.thoughtcrime.securesms.ConfirmIdentityDialog;
import org.thoughtcrime.securesms.MediaPreviewActivity;
import org.thoughtcrime.securesms.MessageDetailsActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.components.AlertView;
import org.thoughtcrime.securesms.components.AudioView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.ConversationItemFooter;
import org.thoughtcrime.securesms.components.ConversationItemThumbnail;
import org.thoughtcrime.securesms.components.DocumentView;
import org.thoughtcrime.securesms.components.LinkPreviewView;
import org.thoughtcrime.securesms.components.QuoteView;
import org.thoughtcrime.securesms.components.SharedContactView;
import org.thoughtcrime.securesms.components.StickerView;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.Quote;
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob;
import org.thoughtcrime.securesms.jobs.MmsDownloadJob;
import org.thoughtcrime.securesms.jobs.MmsSendJob;
import org.thoughtcrime.securesms.jobs.SmsSendJob;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideClickListener;
import org.thoughtcrime.securesms.mms.SlidesClickedListener;
import org.thoughtcrime.securesms.mms.TextSlide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.stickers.StickerUrl;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.LongClickCopySpan;
import org.thoughtcrime.securesms.util.LongClickMovementMethod;
import org.thoughtcrime.securesms.util.SearchUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.Stub;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;
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
    implements RecipientModifiedListener, BindableConversationItem
{
  private static final String TAG = ConversationItem.class.getSimpleName();

  private static final int MAX_MEASURE_CALLS       = 3;
  private static final int MAX_BODY_DISPLAY_LENGTH = 1000;

  private MessageRecord messageRecord;
  private Locale        locale;
  private boolean       groupThread;
  private Recipient     recipient;
  private GlideRequests glideRequests;

  protected ViewGroup              bodyBubble;
  private   QuoteView              quoteView;
  private   EmojiTextView          bodyText;
  private   ConversationItemFooter footer;
  private   ConversationItemFooter stickerFooter;
  private   TextView               groupSender;
  private   TextView               groupSenderProfileName;
  private   View                   groupSenderHolder;
  private   AvatarImageView        contactPhoto;
  private   ViewGroup              contactPhotoHolder;
  private   AlertView              alertView;
  private   ViewGroup              container;

  private @NonNull  Set<MessageRecord>              batchSelected = new HashSet<>();
  private           Recipient                       conversationRecipient;
  private           Stub<ConversationItemThumbnail> mediaThumbnailStub;
  private           Stub<AudioView>                 audioViewStub;
  private           Stub<DocumentView>              documentViewStub;
  private           Stub<SharedContactView>         sharedContactStub;
  private           Stub<LinkPreviewView>           linkPreviewStub;
  private           Stub<StickerView>               stickerStub;
  private @Nullable EventListener                   eventListener;

  private int defaultBubbleColor;
  private int measureCalls;

  private final PassthroughClickListener        passthroughClickListener    = new PassthroughClickListener();
  private final AttachmentDownloadClickListener downloadClickListener       = new AttachmentDownloadClickListener();
  private final SlideClickPassthroughListener   singleDownloadClickListener = new SlideClickPassthroughListener(downloadClickListener);
  private final SharedContactEventListener      sharedContactEventListener  = new SharedContactEventListener();
  private final SharedContactClickListener      sharedContactClickListener  = new SharedContactClickListener();
  private final LinkPreviewClickListener        linkPreviewClickListener    = new LinkPreviewClickListener();

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

    this.bodyText                =            findViewById(R.id.conversation_item_body);
    this.footer                  =            findViewById(R.id.conversation_item_footer);
    this.stickerFooter           =            findViewById(R.id.conversation_item_sticker_footer);
    this.groupSender             =            findViewById(R.id.group_message_sender);
    this.groupSenderProfileName  =            findViewById(R.id.group_message_sender_profile);
    this.alertView               =            findViewById(R.id.indicators_parent);
    this.contactPhoto            =            findViewById(R.id.contact_photo);
    this.contactPhotoHolder      =            findViewById(R.id.contact_photo_container);
    this.bodyBubble              =            findViewById(R.id.body_bubble);
    this.mediaThumbnailStub      = new Stub<>(findViewById(R.id.image_view_stub));
    this.audioViewStub           = new Stub<>(findViewById(R.id.audio_view_stub));
    this.documentViewStub        = new Stub<>(findViewById(R.id.document_view_stub));
    this.sharedContactStub       = new Stub<>(findViewById(R.id.shared_contact_view_stub));
    this.linkPreviewStub         = new Stub<>(findViewById(R.id.link_preview_stub));
    this.stickerStub             = new Stub<>(findViewById(R.id.sticker_view_stub));
    this.groupSenderHolder       =            findViewById(R.id.group_sender_holder);
    this.quoteView               =            findViewById(R.id.quote_view);
    this.container               =            findViewById(R.id.container);

    setOnClickListener(new ClickListener(null));

    bodyText.setOnLongClickListener(passthroughClickListener);
    bodyText.setOnClickListener(passthroughClickListener);

    bodyText.setMovementMethod(LongClickMovementMethod.getInstance(getContext()));
  }

  @Override
  public void bind(@NonNull MessageRecord           messageRecord,
                   @NonNull Optional<MessageRecord> previousMessageRecord,
                   @NonNull Optional<MessageRecord> nextMessageRecord,
                   @NonNull GlideRequests           glideRequests,
                   @NonNull Locale                  locale,
                   @NonNull Set<MessageRecord>      batchSelected,
                   @NonNull Recipient               conversationRecipient,
                   @Nullable String                 searchQuery,
                            boolean                 pulseHighlight)
  {
    this.messageRecord          = messageRecord;
    this.locale                 = locale;
    this.glideRequests          = glideRequests;
    this.batchSelected          = batchSelected;
    this.conversationRecipient  = conversationRecipient;
    this.groupThread            = conversationRecipient.isGroupRecipient();
    this.recipient              = messageRecord.getIndividualRecipient();

    this.recipient.addListener(this);
    this.conversationRecipient.addListener(this);

    setGutterSizes(messageRecord, groupThread);
    setMessageShape(messageRecord, previousMessageRecord, nextMessageRecord, groupThread);
    setMediaAttributes(messageRecord, previousMessageRecord, nextMessageRecord, conversationRecipient, groupThread);
    setInteractionState(messageRecord, pulseHighlight);
    setBodyText(messageRecord, searchQuery);
    setBubbleState(messageRecord);
    setStatusIcons(messageRecord);
    setContactPhoto(recipient);
    setGroupMessageStatus(messageRecord, recipient);
    setGroupAuthorColor(messageRecord);
    setAuthor(messageRecord, previousMessageRecord, nextMessageRecord, groupThread);
    setQuote(messageRecord, previousMessageRecord, nextMessageRecord, groupThread);
    setMessageSpacing(context, messageRecord, previousMessageRecord, nextMessageRecord, groupThread);
    setFooter(messageRecord, nextMessageRecord, locale, groupThread);
  }

  @Override
  public void setEventListener(@Nullable EventListener eventListener) {
    this.eventListener = eventListener;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    if (isInEditMode()) {
      return;
    }

    boolean needsMeasure = false;

    if (hasQuote(messageRecord)) {
      int quoteWidth     = quoteView.getMeasuredWidth();
      int availableWidth = getAvailableMessageBubbleWidth(quoteView);

      if (quoteWidth != availableWidth) {
        quoteView.getLayoutParams().width = availableWidth;
        needsMeasure = true;
      }
    }

    ConversationItemFooter activeFooter   = getActiveFooter(messageRecord);
    int                    availableWidth = getAvailableMessageBubbleWidth(footer);

    if (activeFooter.getVisibility() != GONE && activeFooter.getMeasuredWidth() != availableWidth) {
      activeFooter.getLayoutParams().width = availableWidth;
      needsMeasure = true;
    }

    if (needsMeasure) {
      if (measureCalls < MAX_MEASURE_CALLS) {
        measureCalls++;
        measure(widthMeasureSpec, heightMeasureSpec);
      } else {
        Log.w(TAG, "Hit measure() cap of " + MAX_MEASURE_CALLS);
      }
    } else {
      measureCalls = 0;
    }
  }

  private int getAvailableMessageBubbleWidth(@NonNull View forView) {
    int availableWidth;
    if (hasAudio(messageRecord)) {
      availableWidth = audioViewStub.get().getMeasuredWidth() + ViewUtil.getLeftMargin(audioViewStub.get()) + ViewUtil.getRightMargin(audioViewStub.get());
    } else if (hasThumbnail(messageRecord) || hasBigImageLinkPreview(messageRecord)) {
      availableWidth = mediaThumbnailStub.get().getMeasuredWidth();
    } else {
      availableWidth = bodyBubble.getMeasuredWidth() - bodyBubble.getPaddingLeft() - bodyBubble.getPaddingRight();
    }

    availableWidth -= ViewUtil.getLeftMargin(forView) + ViewUtil.getRightMargin(forView);

    return availableWidth;
  }

  private void initializeAttributes() {
    final int[]      attributes = new int[] {R.attr.conversation_item_bubble_background};
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

  private void setBubbleState(MessageRecord messageRecord) {
    if (messageRecord.isOutgoing()) {
      bodyBubble.getBackground().setColorFilter(defaultBubbleColor, PorterDuff.Mode.MULTIPLY);
    } else {
      bodyBubble.getBackground().setColorFilter(messageRecord.getRecipient().getColor().toConversationColor(context), PorterDuff.Mode.MULTIPLY);
    }

    if (audioViewStub.resolved()) {
      setAudioViewTint(messageRecord, this.conversationRecipient);
    }
  }

  private void setAudioViewTint(MessageRecord messageRecord, Recipient recipient) {
    if (messageRecord.isOutgoing()) {
      if (DynamicTheme.LIGHT.equals(TextSecurePreferences.getTheme(context))) {
        audioViewStub.get().setTint(getContext().getResources().getColor(R.color.core_grey_60), defaultBubbleColor);
      } else {
        audioViewStub.get().setTint(Color.WHITE, defaultBubbleColor);
      }
    } else {
      audioViewStub.get().setTint(Color.WHITE, recipient.getColor().toConversationColor(context));
    }
  }

  private void setInteractionState(MessageRecord messageRecord, boolean pulseHighlight) {
    if (batchSelected.contains(messageRecord)) {
      setBackgroundResource(R.drawable.conversation_item_background);
      setSelected(true);
    } else if (pulseHighlight) {
      setBackgroundResource(R.drawable.conversation_item_background_animated);
      setSelected(true);
      postDelayed(() -> setSelected(false), 500);
    } else {
      setSelected(false);
    }

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

    if (documentViewStub.resolved()) {
      documentViewStub.get().setFocusable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
      documentViewStub.get().setClickable(batchSelected.isEmpty());
    }
  }

  private boolean isCaptionlessMms(MessageRecord messageRecord) {
    return TextUtils.isEmpty(messageRecord.getDisplayBody(getContext())) && messageRecord.isMms() && ((MmsMessageRecord) messageRecord).getSlideDeck().getTextSlide() == null;
  }

  private boolean hasAudio(MessageRecord messageRecord) {
    return messageRecord.isMms() && ((MmsMessageRecord)messageRecord).getSlideDeck().getAudioSlide() != null;
  }

  private boolean hasThumbnail(MessageRecord messageRecord) {
    return messageRecord.isMms() && ((MmsMessageRecord)messageRecord).getSlideDeck().getThumbnailSlide() != null;
  }

  private boolean hasSticker(MessageRecord messageRecord) {
    return messageRecord.isMms() && ((MmsMessageRecord)messageRecord).getSlideDeck().getStickerSlide() != null;
  }

  private boolean hasOnlyThumbnail(MessageRecord messageRecord) {
    return hasThumbnail(messageRecord)      &&
           !hasAudio(messageRecord)         &&
           !hasDocument(messageRecord)      &&
           !hasSharedContact(messageRecord) &&
           !hasSticker(messageRecord);
  }

  private boolean hasDocument(MessageRecord messageRecord) {
    return messageRecord.isMms() && ((MmsMessageRecord)messageRecord).getSlideDeck().getDocumentSlide() != null;
  }

  private boolean hasExtraText(MessageRecord messageRecord) {
    boolean hasTextSlide    = messageRecord.isMms() && ((MmsMessageRecord)messageRecord).getSlideDeck().getTextSlide() != null;
    boolean hasOverflowText = messageRecord.getBody().length() > MAX_BODY_DISPLAY_LENGTH;

    return hasTextSlide || hasOverflowText;
  }

  private boolean hasQuote(MessageRecord messageRecord) {
    return messageRecord.isMms() && ((MmsMessageRecord)messageRecord).getQuote() != null;
  }

  private boolean hasSharedContact(MessageRecord messageRecord) {
    return messageRecord.isMms() && !((MmsMessageRecord)messageRecord).getSharedContacts().isEmpty();
  }

  private boolean hasLinkPreview(MessageRecord  messageRecord) {
    return messageRecord.isMms() && !((MmsMessageRecord)messageRecord).getLinkPreviews().isEmpty();
  }

  private boolean hasBigImageLinkPreview(MessageRecord messageRecord) {
    if (!hasLinkPreview(messageRecord)) return false;

    LinkPreview linkPreview = ((MmsMessageRecord) messageRecord).getLinkPreviews().get(0);
    int         minWidth    = getResources().getDimensionPixelSize(R.dimen.media_bubble_min_width);

    return linkPreview.getThumbnail().isPresent()                  &&
           linkPreview.getThumbnail().get().getWidth() >= minWidth &&
           !StickerUrl.isValidShareLink(linkPreview.getUrl());
  }

  private void setBodyText(MessageRecord messageRecord, @Nullable String searchQuery) {
    bodyText.setClickable(false);
    bodyText.setFocusable(false);
    bodyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, TextSecurePreferences.getMessageBodyTextSize(context));

    if (isCaptionlessMms(messageRecord)) {
      bodyText.setVisibility(View.GONE);
    } else {
      Spannable styledText = linkifyMessageBody(messageRecord.getDisplayBody(getContext()), batchSelected.isEmpty());
      styledText = SearchUtil.getHighlightedSpan(locale, () -> new BackgroundColorSpan(Color.YELLOW), styledText, searchQuery);
      styledText = SearchUtil.getHighlightedSpan(locale, () -> new ForegroundColorSpan(Color.BLACK), styledText, searchQuery);

      if (hasExtraText(messageRecord)) {
        bodyText.setOverflowText(getLongMessageSpan(messageRecord));
      } else {
        bodyText.setOverflowText(null);
      }

      bodyText.setText(styledText);
      bodyText.setVisibility(View.VISIBLE);
    }
  }

  private void setMediaAttributes(@NonNull MessageRecord           messageRecord,
                                  @NonNull Optional<MessageRecord> previousRecord,
                                  @NonNull Optional<MessageRecord> nextRecord,
                                  @NonNull Recipient               conversationRecipient,
                                           boolean                 isGroupThread)
  {
    boolean showControls = !messageRecord.isFailed();

    if (hasSharedContact(messageRecord)) {
      sharedContactStub.get().setVisibility(VISIBLE);
      if (audioViewStub.resolved())      mediaThumbnailStub.get().setVisibility(View.GONE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved())   documentViewStub.get().setVisibility(View.GONE);
      if (linkPreviewStub.resolved())    linkPreviewStub.get().setVisibility(GONE);
      if (stickerStub.resolved())        stickerStub.get().setVisibility(View.GONE);

      sharedContactStub.get().setContact(((MediaMmsMessageRecord) messageRecord).getSharedContacts().get(0), glideRequests, locale);
      sharedContactStub.get().setEventListener(sharedContactEventListener);
      sharedContactStub.get().setOnClickListener(sharedContactClickListener);
      sharedContactStub.get().setOnLongClickListener(passthroughClickListener);

      setSharedContactCorners(messageRecord, previousRecord, nextRecord, isGroupThread);

      ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParams(groupSenderHolder, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      footer.setVisibility(GONE);
    } else if (hasLinkPreview(messageRecord)) {
      linkPreviewStub.get().setVisibility(View.VISIBLE);
      if (audioViewStub.resolved())      audioViewStub.get().setVisibility(View.GONE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved())   documentViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved())  sharedContactStub.get().setVisibility(GONE);
      if (stickerStub.resolved())        stickerStub.get().setVisibility(View.GONE);

      //noinspection ConstantConditions
      LinkPreview linkPreview = ((MmsMessageRecord) messageRecord).getLinkPreviews().get(0);

      if (hasBigImageLinkPreview(messageRecord)) {
        mediaThumbnailStub.get().setVisibility(VISIBLE);
        mediaThumbnailStub.get().setImageResource(glideRequests, Collections.singletonList(new ImageSlide(context, linkPreview.getThumbnail().get())), showControls, false);
        mediaThumbnailStub.get().setThumbnailClickListener(new LinkPreviewThumbnailClickListener());
        mediaThumbnailStub.get().setDownloadClickListener(downloadClickListener);
        mediaThumbnailStub.get().setOnLongClickListener(passthroughClickListener);

        linkPreviewStub.get().setLinkPreview(glideRequests, linkPreview, false);

        setThumbnailCorners(messageRecord, previousRecord, nextRecord, isGroupThread);
        setLinkPreviewCorners(messageRecord, previousRecord, nextRecord, isGroupThread, true);

        ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ViewUtil.updateLayoutParams(groupSenderHolder, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      } else {
        linkPreviewStub.get().setLinkPreview(glideRequests, linkPreview, true);
        linkPreviewStub.get().setDownloadClickedListener(downloadClickListener);
        setLinkPreviewCorners(messageRecord, previousRecord, nextRecord, isGroupThread, false);
        ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ViewUtil.updateLayoutParams(groupSenderHolder, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      }

      linkPreviewStub.get().setOnClickListener(linkPreviewClickListener);
      linkPreviewStub.get().setOnLongClickListener(passthroughClickListener);


      footer.setVisibility(VISIBLE);
    } else if (hasAudio(messageRecord)) {
      audioViewStub.get().setVisibility(View.VISIBLE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved())   documentViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved())  sharedContactStub.get().setVisibility(GONE);
      if (linkPreviewStub.resolved())    linkPreviewStub.get().setVisibility(GONE);
      if (stickerStub.resolved())        stickerStub.get().setVisibility(View.GONE);

      //noinspection ConstantConditions
      audioViewStub.get().setAudio(((MediaMmsMessageRecord) messageRecord).getSlideDeck().getAudioSlide(), showControls);
      audioViewStub.get().setDownloadClickListener(singleDownloadClickListener);
      audioViewStub.get().setOnLongClickListener(passthroughClickListener);

      ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParams(groupSenderHolder, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

      footer.setVisibility(VISIBLE);
    } else if (hasDocument(messageRecord)) {
      documentViewStub.get().setVisibility(View.VISIBLE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (audioViewStub.resolved())      audioViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved())  sharedContactStub.get().setVisibility(GONE);
      if (linkPreviewStub.resolved())    linkPreviewStub.get().setVisibility(GONE);
      if (stickerStub.resolved())        stickerStub.get().setVisibility(View.GONE);

      //noinspection ConstantConditions
      documentViewStub.get().setDocument(((MediaMmsMessageRecord) messageRecord).getSlideDeck().getDocumentSlide(), showControls);
      documentViewStub.get().setDocumentClickListener(new ThumbnailClickListener());
      documentViewStub.get().setDownloadClickListener(singleDownloadClickListener);
      documentViewStub.get().setOnLongClickListener(passthroughClickListener);

      ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParams(groupSenderHolder, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

      footer.setVisibility(VISIBLE);
    } else if (hasSticker(messageRecord) && isCaptionlessMms(messageRecord)) {
      bodyBubble.setBackgroundColor(Color.TRANSPARENT);

      stickerStub.get().setVisibility(View.VISIBLE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (audioViewStub.resolved())      audioViewStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved())   documentViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved())  sharedContactStub.get().setVisibility(GONE);
      if (linkPreviewStub.resolved())    linkPreviewStub.get().setVisibility(GONE);

      //noinspection ConstantConditions
      stickerStub.get().setSticker(glideRequests, ((MmsMessageRecord) messageRecord).getSlideDeck().getStickerSlide());
      stickerStub.get().setThumbnailClickListener(new StickerClickListener());
      stickerStub.get().setDownloadClickListener(downloadClickListener);
      stickerStub.get().setOnLongClickListener(passthroughClickListener);
      stickerStub.get().setOnClickListener(passthroughClickListener);

      ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParams(groupSenderHolder, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

      footer.setVisibility(VISIBLE);
    } else if (hasThumbnail(messageRecord)) {
      mediaThumbnailStub.get().setVisibility(View.VISIBLE);
      if (audioViewStub.resolved())     audioViewStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved())  documentViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved()) sharedContactStub.get().setVisibility(GONE);
      if (linkPreviewStub.resolved())   linkPreviewStub.get().setVisibility(GONE);
      if (stickerStub.resolved())        stickerStub.get().setVisibility(View.GONE);

      //noinspection ConstantConditions
      List<Slide> thumbnailSlides = ((MmsMessageRecord) messageRecord).getSlideDeck().getThumbnailSlides();
      mediaThumbnailStub.get().setImageResource(glideRequests,
                                                thumbnailSlides,
                                                showControls,
                                                false);
      mediaThumbnailStub.get().setThumbnailClickListener(new ThumbnailClickListener());
      mediaThumbnailStub.get().setDownloadClickListener(downloadClickListener);
      mediaThumbnailStub.get().setOnLongClickListener(passthroughClickListener);
      mediaThumbnailStub.get().setOnClickListener(passthroughClickListener);
      mediaThumbnailStub.get().showShade(TextUtils.isEmpty(messageRecord.getDisplayBody(getContext())) && !hasExtraText(messageRecord));
      mediaThumbnailStub.get().setConversationColor(messageRecord.isOutgoing() ? defaultBubbleColor
                                                                               : messageRecord.getRecipient().getColor().toConversationColor(context));
      mediaThumbnailStub.get().setBorderless(false);

      setThumbnailCorners(messageRecord, previousRecord, nextRecord, isGroupThread);

      ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParams(groupSenderHolder, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

      footer.setVisibility(VISIBLE);
    } else {
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (audioViewStub.resolved())      audioViewStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved())   documentViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved())  sharedContactStub.get().setVisibility(GONE);
      if (linkPreviewStub.resolved())    linkPreviewStub.get().setVisibility(GONE);
      if (stickerStub.resolved())        stickerStub.get().setVisibility(View.GONE);

      ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParams(groupSenderHolder, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

      footer.setVisibility(VISIBLE);
    }
  }

  private void setThumbnailCorners(@NonNull MessageRecord           current,
                                   @NonNull Optional<MessageRecord> previous,
                                   @NonNull Optional<MessageRecord> next,
                                            boolean                 isGroupThread)
  {
    int defaultRadius  = readDimen(R.dimen.message_corner_radius);
    int collapseRadius = readDimen(R.dimen.message_corner_collapse_radius);

    int topLeft     = defaultRadius;
    int topRight    = defaultRadius;
    int bottomLeft  = defaultRadius;
    int bottomRight = defaultRadius;

    if (isSingularMessage(current, previous, next, isGroupThread)) {
      topLeft     = defaultRadius;
      topRight    = defaultRadius;
      bottomLeft  = defaultRadius;
      bottomRight = defaultRadius;
    } else if (isStartOfMessageCluster(current, previous, isGroupThread)) {
      if (current.isOutgoing()) {
        bottomRight = collapseRadius;
      } else {
        bottomLeft = collapseRadius;
      }
    } else if (isEndOfMessageCluster(current, next, isGroupThread)) {
      if (current.isOutgoing()) {
        topRight = collapseRadius;
      } else {
        topLeft = collapseRadius;
      }
    } else {
      if (current.isOutgoing()) {
        topRight    = collapseRadius;
        bottomRight = collapseRadius;
      } else {
        topLeft    = collapseRadius;
        bottomLeft = collapseRadius;
      }
    }

    if (!TextUtils.isEmpty(current.getDisplayBody(getContext()))) {
      bottomLeft  = 0;
      bottomRight = 0;
    }

    if (isStartOfMessageCluster(current, previous, isGroupThread) && !current.isOutgoing() && isGroupThread) {
      topLeft  = 0;
      topRight = 0;
    }

    if (hasQuote(messageRecord)) {
      topLeft  = 0;
      topRight = 0;
    }

    if (hasLinkPreview(messageRecord) || hasExtraText(messageRecord)) {
      bottomLeft  = 0;
      bottomRight = 0;
    }

    mediaThumbnailStub.get().setCorners(topLeft, topRight, bottomRight, bottomLeft);
  }

  private void setSharedContactCorners(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    if (isSingularMessage(current, previous, next, isGroupThread) || isEndOfMessageCluster(current, next, isGroupThread)) {
      sharedContactStub.get().setSingularStyle();
    } else if (current.isOutgoing()) {
      sharedContactStub.get().setClusteredOutgoingStyle();
    } else {
      sharedContactStub.get().setClusteredIncomingStyle();
    }
  }

  private void setLinkPreviewCorners(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, @NonNull Optional<MessageRecord> next, boolean isGroupThread, boolean bigImage) {
    int defaultRadius  = readDimen(R.dimen.message_corner_radius);
    int collapseRadius = readDimen(R.dimen.message_corner_collapse_radius);

    if (bigImage) {
      linkPreviewStub.get().setCorners(0, 0);
    } else if (isStartOfMessageCluster(current, previous, isGroupThread) && !current.isOutgoing() && isGroupThread) {
      linkPreviewStub.get().setCorners(0, 0);
    } else if (isSingularMessage(current, previous, next, isGroupThread) || isStartOfMessageCluster(current, previous, isGroupThread)) {
      linkPreviewStub.get().setCorners(defaultRadius, defaultRadius);
    } else if (current.isOutgoing()) {
      linkPreviewStub.get().setCorners(defaultRadius, collapseRadius);
    } else {
      linkPreviewStub.get().setCorners(collapseRadius, defaultRadius);
    }
  }

  private void setContactPhoto(@NonNull Recipient recipient) {
    if (contactPhoto == null) return;
    contactPhoto.setAvatar(glideRequests, recipient, true);
  }

  private SpannableString linkifyMessageBody(SpannableString messageBody, boolean shouldLinkifyAllLinks) {
    int     linkPattern = Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS;
    boolean hasLinks    = Linkify.addLinks(messageBody, shouldLinkifyAllLinks ? linkPattern : 0);

    if (hasLinks) {
      Stream.of(messageBody.getSpans(0, messageBody.length(), URLSpan.class))
            .filterNot(url -> LinkPreviewUtil.isLegalUrl(url.getURL()))
            .forEach(messageBody::removeSpan);

      URLSpan[] urlSpans = messageBody.getSpans(0, messageBody.length(), URLSpan.class);

      for (URLSpan urlSpan : urlSpans) {
        int start = messageBody.getSpanStart(urlSpan);
        int end = messageBody.getSpanEnd(urlSpan);
        messageBody.setSpan(new LongClickCopySpan(urlSpan.getURL()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    return messageBody;
  }

  private void setStatusIcons(MessageRecord messageRecord) {
    bodyText.setCompoundDrawablesWithIntrinsicBounds(0, 0, messageRecord.isKeyExchange() ? R.drawable.ic_menu_login : 0, 0);

    if (messageRecord.isFailed()) {
      alertView.setFailed();
    } else if (messageRecord.isPendingInsecureSmsFallback()) {
      alertView.setPendingApproval();
    } else {
      alertView.setNone();
    }
  }

  private void setQuote(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    if (current.isMms() && !current.isMmsNotification() && ((MediaMmsMessageRecord)current).getQuote() != null) {
      Quote quote = ((MediaMmsMessageRecord)current).getQuote();
      //noinspection ConstantConditions
      quoteView.setQuote(glideRequests, quote.getId(), Recipient.from(context, quote.getAuthor(), true), quote.getText(), quote.isOriginalMissing(), quote.getAttachment());
      quoteView.setVisibility(View.VISIBLE);
      quoteView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;

      quoteView.setOnClickListener(view -> {
        if (eventListener != null && batchSelected.isEmpty()) {
          eventListener.onQuoteClicked((MmsMessageRecord) current);
        } else {
          passthroughClickListener.onClick(view);
        }
      });

      quoteView.setOnLongClickListener(passthroughClickListener);

      if (isStartOfMessageCluster(current, previous, isGroupThread)) {
        if (current.isOutgoing()) {
          quoteView.setTopCornerSizes(true, true);
        } else if (isGroupThread) {
          quoteView.setTopCornerSizes(false, false);
        } else {
          quoteView.setTopCornerSizes(true, true);
        }
      } else if (!isSingularMessage(current, previous, next, isGroupThread)) {
        if (current.isOutgoing()) {
          quoteView.setTopCornerSizes(true, false);
        } else {
          quoteView.setTopCornerSizes(false, true);
        }
      }

      if (mediaThumbnailStub.resolved()) {
        ViewUtil.setTopMargin(mediaThumbnailStub.get(), readDimen(R.dimen.message_bubble_top_padding));
      }
    } else {
      quoteView.dismiss();

      if (mediaThumbnailStub.resolved()) {
        ViewUtil.setTopMargin(mediaThumbnailStub.get(), 0);
      }
    }
  }

  private void setGutterSizes(@NonNull MessageRecord current, boolean isGroupThread) {
    if (isGroupThread && current.isOutgoing()) {
      ViewUtil.setLeftMargin(container, readDimen(R.dimen.conversation_group_left_gutter));
    } else if (current.isOutgoing()) {
      ViewUtil.setLeftMargin(container, readDimen(R.dimen.conversation_individual_left_gutter));
    }
  }

  private void setFooter(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> next, @NonNull Locale locale, boolean isGroupThread) {
    ViewUtil.updateLayoutParams(footer, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

    footer.setVisibility(GONE);
    stickerFooter.setVisibility(GONE);
    if (sharedContactStub.resolved())  sharedContactStub.get().getFooter().setVisibility(GONE);
    if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().getFooter().setVisibility(GONE);

    boolean differentTimestamps = next.isPresent() && !DateUtils.isSameExtendedRelativeTimestamp(context, locale, next.get().getTimestamp(), current.getTimestamp());

    if (current.getExpiresIn() > 0 || !current.isSecure() || current.isPending() || current.isPendingInsecureSmsFallback() ||
        current.isFailed() || differentTimestamps || isEndOfMessageCluster(current, next, isGroupThread))
    {
      ConversationItemFooter activeFooter = getActiveFooter(current);
      activeFooter.setVisibility(VISIBLE);
      activeFooter.setMessageRecord(current, locale);
    }
  }

  private ConversationItemFooter getActiveFooter(@NonNull MessageRecord messageRecord) {
    if (hasSticker(messageRecord)) {
      return stickerFooter;
    } else if (hasSharedContact(messageRecord)) {
      return sharedContactStub.get().getFooter();
    } else if (hasOnlyThumbnail(messageRecord) && TextUtils.isEmpty(messageRecord.getDisplayBody(getContext()))) {
      return mediaThumbnailStub.get().getFooter();
    } else {
      return footer;
    }
  }

  private int readDimen(@DimenRes int dimenId) {
    return context.getResources().getDimensionPixelOffset(dimenId);
  }

  private boolean shouldInterceptClicks(MessageRecord messageRecord) {
    return batchSelected.isEmpty() &&
            ((messageRecord.isFailed() && !messageRecord.isMmsNotification()) ||
            messageRecord.isPendingInsecureSmsFallback() ||
            messageRecord.isBundleKeyExchange());
  }

  @SuppressLint("SetTextI18n")
  private void setGroupMessageStatus(MessageRecord messageRecord, Recipient recipient) {
    if (groupThread && !messageRecord.isOutgoing()) {
      this.groupSender.setText(recipient.toShortString());

      if (recipient.getName() == null && !TextUtils.isEmpty(recipient.getProfileName())) {
        this.groupSenderProfileName.setText("~" + recipient.getProfileName());
        this.groupSenderProfileName.setVisibility(View.VISIBLE);
      } else {
        this.groupSenderProfileName.setText(null);
        this.groupSenderProfileName.setVisibility(View.GONE);
      }
    }
  }

  private void setGroupAuthorColor(@NonNull MessageRecord messageRecord) {
    if (hasSticker(messageRecord)) {
      groupSender.setTextColor(ThemeUtil.getThemedColor(context, R.attr.conversation_sticker_author_color));
      groupSenderProfileName.setTextColor(ThemeUtil.getThemedColor(context, R.attr.conversation_sticker_author_color));
    } else {
      groupSender.setTextColor(ThemeUtil.getThemedColor(context, R.attr.conversation_item_received_text_primary_color));
      groupSenderProfileName.setTextColor(ThemeUtil.getThemedColor(context, R.attr.conversation_item_received_text_primary_color));
    }
  }

  private void setAuthor(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    if (isGroupThread && !current.isOutgoing()) {
      contactPhotoHolder.setVisibility(VISIBLE);

      if (!previous.isPresent() || previous.get().isUpdate() || !current.getRecipient().getAddress().equals(previous.get().getRecipient().getAddress()) ||
          !DateUtils.isSameDay(previous.get().getTimestamp(), current.getTimestamp()))
      {
        groupSenderHolder.setVisibility(VISIBLE);
      } else {
        groupSenderHolder.setVisibility(GONE);
      }

      if (!next.isPresent() || next.get().isUpdate() || !current.getRecipient().getAddress().equals(next.get().getRecipient().getAddress())) {
        contactPhoto.setVisibility(VISIBLE);
      } else {
        contactPhoto.setVisibility(GONE);
      }
    } else {
      groupSenderHolder.setVisibility(GONE);

      if (contactPhotoHolder != null) {
        contactPhotoHolder.setVisibility(GONE);
      }
    }
  }

  private void setMessageShape(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    int background;
    if (isSingularMessage(current, previous, next, isGroupThread)) {
      background = current.isOutgoing() ? R.drawable.message_bubble_background_sent_alone
                                        : R.drawable.message_bubble_background_received_alone;
    } else if (isStartOfMessageCluster(current, previous, isGroupThread)) {
      background = current.isOutgoing() ? R.drawable.message_bubble_background_sent_start
                                        : R.drawable.message_bubble_background_received_start;
    } else if (isEndOfMessageCluster(current, next, isGroupThread)) {
      background = current.isOutgoing() ? R.drawable.message_bubble_background_sent_end
                                        : R.drawable.message_bubble_background_received_end;
    } else {
      background = current.isOutgoing() ? R.drawable.message_bubble_background_sent_middle
                                        : R.drawable.message_bubble_background_received_middle;
    }

    bodyBubble.setBackgroundResource(background);
  }

  private boolean isStartOfMessageCluster(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, boolean isGroupThread) {
    if (isGroupThread) {
      return !previous.isPresent() || previous.get().isUpdate() || !DateUtils.isSameDay(current.getTimestamp(), previous.get().getTimestamp()) ||
             !current.getRecipient().getAddress().equals(previous.get().getRecipient().getAddress());
    } else {
      return !previous.isPresent() || previous.get().isUpdate() || !DateUtils.isSameDay(current.getTimestamp(), previous.get().getTimestamp()) ||
             current.isOutgoing() != previous.get().isOutgoing();
    }
  }

  private boolean isEndOfMessageCluster(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    if (isGroupThread) {
      return !next.isPresent() || next.get().isUpdate() || !DateUtils.isSameDay(current.getTimestamp(), next.get().getTimestamp()) ||
             !current.getRecipient().getAddress().equals(next.get().getRecipient().getAddress());
    } else {
      return !next.isPresent() || next.get().isUpdate() || !DateUtils.isSameDay(current.getTimestamp(), next.get().getTimestamp()) ||
             current.isOutgoing() != next.get().isOutgoing();
    }
  }

  private boolean isSingularMessage(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    return isStartOfMessageCluster(current, previous, isGroupThread) && isEndOfMessageCluster(current, next, isGroupThread);
  }

  private void setMessageSpacing(@NonNull Context context, @NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    int spacingTop = readDimen(context, R.dimen.conversation_vertical_message_spacing_collapse);
    int spacingBottom = spacingTop;

    if (isStartOfMessageCluster(current, previous, isGroupThread)) {
      spacingTop = readDimen(context, R.dimen.conversation_vertical_message_spacing_default);
    }

    if (isEndOfMessageCluster(current, next, isGroupThread)) {
      spacingBottom = readDimen(context, R.dimen.conversation_vertical_message_spacing_default);
    }

    ViewUtil.setPaddingTop(this, spacingTop);
    ViewUtil.setPaddingBottom(this, spacingBottom);
  }

  private int readDimen(@NonNull Context context, @DimenRes int dimenId) {
    return context.getResources().getDimensionPixelOffset(dimenId);
  }

  /// Event handlers

  private void handleApproveIdentity() {
    List<IdentityKeyMismatch> mismatches = messageRecord.getIdentityKeyMismatches();

    if (mismatches.size() != 1) {
      throw new AssertionError("Identity mismatch count: " + mismatches.size());
    }

    new ConfirmIdentityDialog(context, messageRecord, mismatches.get(0)).show();
  }

  private Spannable getLongMessageSpan(@NonNull MessageRecord messageRecord) {
    String   message;
    Runnable action;

    if (messageRecord.isMms()) {
      TextSlide slide = ((MmsMessageRecord) messageRecord).getSlideDeck().getTextSlide();

      if (slide != null && slide.asAttachment().getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_DONE) {
        message = getResources().getString(R.string.ConversationItem_read_more);
        action  = () -> eventListener.onMoreTextClicked(conversationRecipient.getAddress(), messageRecord.getId(), messageRecord.isMms());
      } else if (slide != null && slide.asAttachment().getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_STARTED) {
        message = getResources().getString(R.string.ConversationItem_pending);
        action  = () -> {};
      } else if (slide != null) {
        message = getResources().getString(R.string.ConversationItem_download_more);
        action  = () -> singleDownloadClickListener.onClick(bodyText, slide);
      } else {
        message = getResources().getString(R.string.ConversationItem_read_more);
        action  = () -> eventListener.onMoreTextClicked(conversationRecipient.getAddress(), messageRecord.getId(), messageRecord.isMms());
      }
    } else {
      message = getResources().getString(R.string.ConversationItem_read_more);
      action  = () -> eventListener.onMoreTextClicked(conversationRecipient.getAddress(), messageRecord.getId(), messageRecord.isMms());
    }

    SpannableStringBuilder span = new SpannableStringBuilder(message);
    CharacterStyle style = new ClickableSpan() {
      @Override
      public void onClick(@NonNull View widget) {
        if (eventListener != null && batchSelected.isEmpty()) {
          action.run();
        }
      }

      @Override
      public void updateDrawState(@NonNull TextPaint ds) {
        ds.setTypeface(Typeface.DEFAULT_BOLD);
      }
    };
    span.setSpan(style, 0, span.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    return span;
  }

  @Override
  public void onModified(final Recipient modified) {
    Util.runOnMain(() -> {
      setBubbleState(messageRecord);
      setContactPhoto(recipient);
      setGroupMessageStatus(messageRecord, recipient);
      setAudioViewTint(messageRecord, conversationRecipient);
    });
  }

  private class SharedContactEventListener implements SharedContactView.EventListener {
    @Override
    public void onAddToContactsClicked(@NonNull Contact contact) {
      if (eventListener != null && batchSelected.isEmpty()) {
        eventListener.onAddToContactsClicked(contact);
      } else {
        passthroughClickListener.onClick(sharedContactStub.get());
      }
    }

    @Override
    public void onInviteClicked(@NonNull List<Recipient> choices) {
      if (eventListener != null && batchSelected.isEmpty()) {
        eventListener.onInviteSharedContactClicked(choices);
      } else {
        passthroughClickListener.onClick(sharedContactStub.get());
      }
    }

    @Override
    public void onMessageClicked(@NonNull List<Recipient> choices) {
      if (eventListener != null && batchSelected.isEmpty()) {
        eventListener.onMessageSharedContactClicked(choices);
      } else {
        passthroughClickListener.onClick(sharedContactStub.get());
      }
    }
  }

  private class SharedContactClickListener implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      if (eventListener != null && batchSelected.isEmpty() && messageRecord.isMms() && !((MmsMessageRecord) messageRecord).getSharedContacts().isEmpty()) {
        eventListener.onSharedContactDetailsClicked(((MmsMessageRecord) messageRecord).getSharedContacts().get(0), sharedContactStub.get().getAvatarView());
      } else {
        passthroughClickListener.onClick(view);
      }
    }
  }

  private class LinkPreviewClickListener implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      if (eventListener != null && batchSelected.isEmpty() && messageRecord.isMms() && !((MmsMessageRecord) messageRecord).getLinkPreviews().isEmpty()) {
        eventListener.onLinkPreviewClicked(((MmsMessageRecord) messageRecord).getLinkPreviews().get(0));
      } else {
        passthroughClickListener.onClick(view);
      }
    }
  }

  private class LinkPreviewThumbnailClickListener implements SlideClickListener {
    public void onClick(final View v, final Slide slide) {
      if (eventListener != null && batchSelected.isEmpty() && messageRecord.isMms() && !((MmsMessageRecord) messageRecord).getLinkPreviews().isEmpty()) {
        eventListener.onLinkPreviewClicked(((MmsMessageRecord) messageRecord).getLinkPreviews().get(0));
      } else {
        performClick();
      }
    }
  }

  private class AttachmentDownloadClickListener implements SlidesClickedListener {
    @Override
    public void onClick(View v, final List<Slide> slides) {
      Log.i(TAG, "onClick() for attachment download");
      if (messageRecord.isMmsNotification()) {
        Log.i(TAG, "Scheduling MMS attachment download");
        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new MmsDownloadJob(messageRecord.getId(),
                                                  messageRecord.getThreadId(), false));
      } else {
        Log.i(TAG, "Scheduling push attachment downloads for " + slides.size() + " items");

        for (Slide slide : slides) {
          ApplicationContext.getInstance(context)
                            .getJobManager()
                            .add(new AttachmentDownloadJob(messageRecord.getId(),
                                                           ((DatabaseAttachment)slide.asAttachment()).getAttachmentId(), true));
        }
      }
    }
  }

  private class SlideClickPassthroughListener implements SlideClickListener {

    private final SlidesClickedListener original;

    private SlideClickPassthroughListener(@NonNull SlidesClickedListener original) {
      this.original = original;
    }

    @Override
    public void onClick(View v, Slide slide) {
      original.onClick(v, Collections.singletonList(slide));
    }
  }

  private class StickerClickListener implements SlideClickListener {
    @Override
    public void onClick(View v, Slide slide) {
      if (shouldInterceptClicks(messageRecord) || !batchSelected.isEmpty()) {
        performClick();
      } else if (eventListener != null && hasSticker(messageRecord)){
        //noinspection ConstantConditions
        eventListener.onStickerClicked(((MmsMessageRecord) messageRecord).getSlideDeck().getStickerSlide().asAttachment().getSticker());
      }
    }
  }

  private class ThumbnailClickListener implements SlideClickListener {
    public void onClick(final View v, final Slide slide) {
      if (shouldInterceptClicks(messageRecord) || !batchSelected.isEmpty()) {
        performClick();
      } else if (MediaPreviewActivity.isContentTypeSupported(slide.getContentType()) && slide.getUri() != null) {
        Intent intent = new Intent(context, MediaPreviewActivity.class);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(slide.getUri(), slide.getContentType());
        intent.putExtra(MediaPreviewActivity.ADDRESS_EXTRA, conversationRecipient.getAddress());
        intent.putExtra(MediaPreviewActivity.OUTGOING_EXTRA, messageRecord.isOutgoing());
        intent.putExtra(MediaPreviewActivity.DATE_EXTRA, messageRecord.getTimestamp());
        intent.putExtra(MediaPreviewActivity.SIZE_EXTRA, slide.asAttachment().getSize());
        intent.putExtra(MediaPreviewActivity.CAPTION_EXTRA, slide.getCaption().orNull());
        intent.putExtra(MediaPreviewActivity.LEFT_IS_RECENT_EXTRA, false);

        context.startActivity(intent);
      } else if (slide.getUri() != null) {
        Log.i(TAG, "Clicked: " + slide.getUri() + " , " + slide.getContentType());
        Uri publicUri = PartAuthority.getAttachmentPublicUri(slide.getUri());
        Log.i(TAG, "Public URI: " + publicUri);
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
    }
  }

  private class PassthroughClickListener implements View.OnLongClickListener, View.OnClickListener {

    @Override
    public boolean onLongClick(View v) {
      if (bodyText.hasSelection()) {
        return false;
      }
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

    ClickListener(@Nullable OnClickListener parent) {
      this.parent = parent;
    }

    public void onClick(View v) {
      if (!shouldInterceptClicks(messageRecord) && parent != null) {
        parent.onClick(v);
      } else if (messageRecord.isFailed()) {
        Intent intent = new Intent(context, MessageDetailsActivity.class);
        intent.putExtra(MessageDetailsActivity.MESSAGE_ID_EXTRA, messageRecord.getId());
        intent.putExtra(MessageDetailsActivity.THREAD_ID_EXTRA, messageRecord.getThreadId());
        intent.putExtra(MessageDetailsActivity.TYPE_EXTRA, messageRecord.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
        intent.putExtra(MessageDetailsActivity.IS_PUSH_GROUP_EXTRA, groupThread && messageRecord.isPush());
        intent.putExtra(MessageDetailsActivity.ADDRESS_EXTRA, conversationRecipient.getAddress());
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

    builder.setPositiveButton(R.string.yes, (dialogInterface, i) -> {
      if (messageRecord.isMms()) {
        MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
        database.markAsInsecure(messageRecord.getId());
        database.markAsOutbox(messageRecord.getId());
        database.markAsForcedSms(messageRecord.getId());

        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new MmsSendJob(messageRecord.getId()));
      } else {
        SmsDatabase database = DatabaseFactory.getSmsDatabase(context);
        database.markAsInsecure(messageRecord.getId());
        database.markAsOutbox(messageRecord.getId());
        database.markAsForcedSms(messageRecord.getId());

        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new SmsSendJob(context, messageRecord.getId(),
                                              messageRecord.getIndividualRecipient().getAddress().serialize()));
      }
    });

    builder.setNegativeButton(R.string.no, (dialogInterface, i) -> {
      if (messageRecord.isMms()) {
        DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageRecord.getId());
      } else {
        DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageRecord.getId());
      }
    });
    builder.show();
  }
}
