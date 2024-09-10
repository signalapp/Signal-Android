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
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.text.Annotation;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.common.MediaItem;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Sets;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.DimensionUnit;
import org.signal.core.util.StringUtil;
import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallLinkRootKey;
import org.thoughtcrime.securesms.BindableConversationItem;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.badges.BadgeImageView;
import org.thoughtcrime.securesms.badges.gifts.GiftMessageView;
import org.thoughtcrime.securesms.badges.gifts.OpenableGift;
import org.thoughtcrime.securesms.calls.links.CallLinks;
import org.thoughtcrime.securesms.components.AlertView;
import org.thoughtcrime.securesms.components.AudioView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.BorderlessImageView;
import org.thoughtcrime.securesms.components.ConversationItemFooter;
import org.thoughtcrime.securesms.components.ConversationItemThumbnail;
import org.thoughtcrime.securesms.components.DocumentView;
import org.thoughtcrime.securesms.components.LinkPreviewView;
import org.thoughtcrime.securesms.components.Outliner;
import org.thoughtcrime.securesms.components.PlaybackSpeedToggleTextView;
import org.thoughtcrime.securesms.components.QuoteView;
import org.thoughtcrime.securesms.components.SharedContactView;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.components.mention.MentionAnnotation;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.conversation.clicklisteners.AttachmentCancelClickListener;
import org.thoughtcrime.securesms.conversation.clicklisteners.ResendClickListener;
import org.thoughtcrime.securesms.conversation.colors.Colorizer;
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectCollection;
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart;
import org.thoughtcrime.securesms.conversation.ui.payment.PaymentMessageView;
import org.thoughtcrime.securesms.conversation.v2.items.InteractiveConversationElement;
import org.thoughtcrime.securesms.conversation.v2.items.V2ConversationItemUtils;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.MediaTable;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.Quote;
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackPolicy;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackPolicyEnforcer;
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory;
import org.thoughtcrime.securesms.mediapreview.MediaPreviewCache;
import org.thoughtcrime.securesms.mediapreview.MediaPreviewV2Fragment;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideClickListener;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.SlidesClickedListener;
import org.thoughtcrime.securesms.mms.TextSlide;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.reactions.ReactionsConversationView;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.revealable.ViewOnceMessageView;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.InterceptableLongClickCopyLinkSpan;
import org.thoughtcrime.securesms.util.LongClickMovementMethod;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.MessageRecordUtil;
import org.thoughtcrime.securesms.util.PlaceholderURLSpan;
import org.thoughtcrime.securesms.util.Projection;
import org.thoughtcrime.securesms.util.ProjectionList;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.SearchUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.UrlClickHandler;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.VibrateUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.NullableStub;
import org.thoughtcrime.securesms.util.views.Stub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * A view that displays an individual conversation item within a conversation
 * thread.  Used by ComposeMessageActivity's ListActivity via a ConversationAdapter.
 *
 * @author Moxie Marlinspike
 */

public final class ConversationItem extends RelativeLayout implements BindableConversationItem,
                                                                      RecipientForeverObserver,
                                                                      OpenableGift,
                                                                      InteractiveConversationElement
{
  private static final String TAG = Log.tag(ConversationItem.class);

  private static final int MAX_MEASURE_CALLS = 3;

  private static final Rect SWIPE_RECT = new Rect();

  public static final  float LONG_PRESS_SCALE_FACTOR    = 0.95f;
  private static final int   SHRINK_BUBBLE_DELAY_MILLIS = 100;
  private static final long  MAX_CLUSTERING_TIME_DIFF   = TimeUnit.MINUTES.toMillis(3);
  private static final int   CONDENSED_MODE_MAX_LINES   = 3;

  private static final SearchUtil.StyleFactory STYLE_FACTORY = () -> new CharacterStyle[] { new BackgroundColorSpan(Color.YELLOW), new ForegroundColorSpan(Color.BLACK) };

  private ConversationMessage         conversationMessage;
  private MessageRecord               messageRecord;
  private Optional<MessageRecord>     nextMessageRecord;
  private Locale                      locale;
  private boolean                     groupThread;
  private LiveRecipient               author;
  private RequestManager              requestManager;
  private Optional<MessageRecord>     previousMessage;
  private ConversationItemDisplayMode displayMode;

  private           ConversationItemBodyBubble bodyBubble;
  private           View                       reply;
  private           View                       replyIcon;
  @Nullable private ViewGroup                  contactPhotoHolder;
  @Nullable private QuoteView                  quoteView;
  private           EmojiTextView              bodyText;
  private           ConversationItemFooter     footer;
  @Nullable private ConversationItemFooter     stickerFooter;
  @Nullable private TextView                   groupSender;
  @Nullable private View                       groupSenderHolder;
  private           AvatarImageView            contactPhoto;
  private           AlertView                  alertView;
  private           ReactionsConversationView  reactionsView;
  private           BadgeImageView             badgeImageView;
  private           View                       storyReactionLabelWrapper;
  private           TextView                   storyReactionLabel;
  private           View                       quotedIndicator;
  private           View                       scheduledIndicator;

  private @NonNull       Set<MultiselectPart>                    batchSelected = new HashSet<>();
  private final @NonNull Outliner                                outliner      = new Outliner();
  private final @NonNull Outliner                                pulseOutliner = new Outliner();
  private final @NonNull List<Outliner>                          outliners     = new ArrayList<>(2);
  private                LiveRecipient                           conversationRecipient;
  private                NullableStub<ConversationItemThumbnail> mediaThumbnailStub;
  private                Stub<AudioView>                         audioViewStub;
  private                Stub<DocumentView>                      documentViewStub;
  private                Stub<SharedContactView>                 sharedContactStub;
  private                Stub<LinkPreviewView>                   linkPreviewStub;
  private                Stub<BorderlessImageView>               stickerStub;
  private                Stub<ViewOnceMessageView>               revealableStub;
  private                Stub<MaterialButton>                    joinCallLinkStub;
  private                Stub<Button>                            callToActionStub;
  private                Stub<GiftMessageView>                   giftViewStub;
  private                Stub<PaymentMessageView>                paymentViewStub;
  private @Nullable      EventListener                           eventListener;
  private @Nullable      GestureDetector                         gestureDetector;

  private int     defaultBubbleColor;
  private int     defaultBubbleColorForWallpaper;
  private int     measureCalls;
  private boolean updatingFooter;

  private final PassthroughClickListener        passthroughClickListener        = new PassthroughClickListener();
  private final AttachmentDownloadClickListener downloadClickListener           = new AttachmentDownloadClickListener();
  private final PlayVideoClickListener          playVideoClickListener          = new PlayVideoClickListener();
  private final AttachmentCancelClickListener   attachmentCancelClickListener   = new AttachmentCancelClickListener();
  private final SlideClickPassthroughListener   singleDownloadClickListener     = new SlideClickPassthroughListener(downloadClickListener);
  private final SharedContactEventListener      sharedContactEventListener      = new SharedContactEventListener();
  private final SharedContactClickListener      sharedContactClickListener      = new SharedContactClickListener();
  private final LinkPreviewClickListener        linkPreviewClickListener        = new LinkPreviewClickListener();
  private final ViewOnceMessageClickListener    revealableClickListener         = new ViewOnceMessageClickListener();
  private final QuotedIndicatorClickListener    quotedIndicatorClickListener    = new QuotedIndicatorClickListener();
  private final ScheduledIndicatorClickListener scheduledIndicatorClickListener = new ScheduledIndicatorClickListener();
  private final UrlClickListener                urlClickListener                = new UrlClickListener();
  private final Rect                            thumbnailMaskingRect            = new Rect();
  private final TouchDelegateChangedListener    touchDelegateChangedListener    = new TouchDelegateChangedListener();
  private final DoubleTapEditTouchListener      doubleTapEditTouchListener      = new DoubleTapEditTouchListener();
  private final GiftMessageViewCallback         giftMessageViewCallback         = new GiftMessageViewCallback();
  private final PaymentTombstoneClickListener   paymentTombstoneClickListener   = new PaymentTombstoneClickListener();

  private final Context context;

  private       MediaItem          mediaItem;
  private       boolean            canPlayContent;
  private       Projection.Corners bodyBubbleCorners;
  private       Colorizer          colorizer;
  private       boolean            hasWallpaper;
  private       float              lastYDownRelativeToThis;
  private final ProjectionList     colorizerProjections = new ProjectionList(3);
  private       boolean            isBound              = false;

  private final Runnable shrinkBubble = new Runnable() {
    @Override
    public void run() {
      bodyBubble.animate()
                .scaleX(LONG_PRESS_SCALE_FACTOR)
                .scaleY(LONG_PRESS_SCALE_FACTOR)
                .setUpdateListener(animation -> {
                  View parent = (View) getParent();
                  if (parent != null) {
                    parent.invalidate();
                  }
                });

      reactionsView.animate()
                   .scaleX(LONG_PRESS_SCALE_FACTOR)
                   .scaleY(LONG_PRESS_SCALE_FACTOR);

      if (quotedIndicator != null) {
        quotedIndicator.animate()
                       .scaleX(LONG_PRESS_SCALE_FACTOR)
                       .scaleY(LONG_PRESS_SCALE_FACTOR);
      }
      if (scheduledIndicator != null) {
        scheduledIndicator.animate()
                          .scaleX(LONG_PRESS_SCALE_FACTOR)
                          .scaleY(LONG_PRESS_SCALE_FACTOR);
      }
    }
  };

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

    this.bodyText                  = findViewById(R.id.conversation_item_body);
    this.footer                    = findViewById(R.id.conversation_item_footer);
    this.stickerFooter             = findViewById(R.id.conversation_item_sticker_footer);
    this.groupSender               = findViewById(R.id.group_message_sender);
    this.alertView                 = findViewById(R.id.indicators_parent);
    this.contactPhoto              = findViewById(R.id.contact_photo);
    this.contactPhotoHolder        = findViewById(R.id.contact_photo_container);
    this.bodyBubble                = findViewById(R.id.body_bubble);
    this.mediaThumbnailStub        = new NullableStub<>(findViewById(R.id.image_view_stub));
    this.audioViewStub             = new Stub<>(findViewById(R.id.audio_view_stub));
    this.documentViewStub          = new Stub<>(findViewById(R.id.document_view_stub));
    this.sharedContactStub         = new Stub<>(findViewById(R.id.shared_contact_view_stub));
    this.linkPreviewStub           = new Stub<>(findViewById(R.id.link_preview_stub));
    this.stickerStub               = new Stub<>(findViewById(R.id.sticker_view_stub));
    this.revealableStub            = new Stub<>(findViewById(R.id.revealable_view_stub));
    this.joinCallLinkStub          = ViewUtil.findStubById(this, R.id.conversation_item_join_button);
    this.callToActionStub          = ViewUtil.findStubById(this, R.id.conversation_item_call_to_action_stub);
    this.groupSenderHolder         = findViewById(R.id.group_sender_holder);
    this.quoteView                 = findViewById(R.id.quote_view);
    this.reply                     = findViewById(R.id.reply_icon_wrapper);
    this.replyIcon                 = findViewById(R.id.reply_icon);
    this.reactionsView             = findViewById(R.id.reactions_view);
    this.badgeImageView            = findViewById(R.id.badge);
    this.storyReactionLabelWrapper = findViewById(R.id.story_reacted_label_holder);
    this.storyReactionLabel        = findViewById(R.id.story_reacted_label);
    this.giftViewStub              = new Stub<>(findViewById(R.id.gift_view_stub));
    this.quotedIndicator           = findViewById(R.id.quoted_indicator);
    this.paymentViewStub           = new Stub<>(findViewById(R.id.payment_view_stub));
    this.scheduledIndicator        = findViewById(R.id.scheduled_indicator);

    setOnClickListener(new ClickListener(null));

    bodyText.setOnTouchListener(doubleTapEditTouchListener);
    bodyText.setOnLongClickListener(passthroughClickListener);
    bodyText.setOnClickListener(passthroughClickListener);
    footer.setOnTouchDelegateChangedListener(touchDelegateChangedListener);
  }

  @Override
  public void bind(@NonNull LifecycleOwner lifecycleOwner,
                   @NonNull ConversationMessage conversationMessage,
                   @NonNull Optional<MessageRecord> previousMessageRecord,
                   @NonNull Optional<MessageRecord> nextMessageRecord,
                   @NonNull RequestManager requestManager,
                   @NonNull Locale locale,
                   @NonNull Set<MultiselectPart> batchSelected,
                   @NonNull Recipient conversationRecipient,
                   @Nullable String searchQuery,
                   boolean pulse,
                   boolean hasWallpaper,
                   boolean isMessageRequestAccepted,
                   boolean allowedToPlayInline,
                   @NonNull Colorizer colorizer,
                   @NonNull ConversationItemDisplayMode displayMode)
  {
    unbind();

    lastYDownRelativeToThis = 0;

    conversationRecipient = conversationRecipient.resolve();

    this.conversationMessage   = conversationMessage;
    this.messageRecord         = conversationMessage.getMessageRecord();
    this.nextMessageRecord     = nextMessageRecord;
    this.locale                = locale;
    this.requestManager        = requestManager;
    this.batchSelected         = batchSelected;
    this.conversationRecipient = conversationRecipient.live();
    this.groupThread           = conversationRecipient.isGroup();
    this.author                = messageRecord.getFromRecipient().live();
    this.canPlayContent        = false;
    this.mediaItem             = null;
    this.colorizer             = colorizer;
    this.displayMode           = displayMode;
    this.previousMessage       = previousMessageRecord;

    setGutterSizes(messageRecord, groupThread);
    setMessageShape(messageRecord, previousMessageRecord, nextMessageRecord, groupThread);
    setMediaAttributes(messageRecord, previousMessageRecord, nextMessageRecord, groupThread, hasWallpaper, isMessageRequestAccepted, allowedToPlayInline);
    setBodyText(messageRecord, searchQuery, isMessageRequestAccepted);
    setBubbleState(messageRecord, messageRecord.getFromRecipient(), hasWallpaper, colorizer);
    setInteractionState(conversationMessage, pulse);
    setStatusIcons(messageRecord, hasWallpaper);
    setContactPhoto(author.get());
    setGroupMessageStatus(messageRecord, author.get());
    setGroupAuthorColor(messageRecord, hasWallpaper, colorizer);
    setAuthor(messageRecord, previousMessageRecord, nextMessageRecord, groupThread, hasWallpaper);
    setQuote(messageRecord, previousMessageRecord, nextMessageRecord, groupThread);
    setMessageSpacing(context, messageRecord, previousMessageRecord, nextMessageRecord, groupThread);
    setReactions(messageRecord);
    setFooter(messageRecord, nextMessageRecord, locale, groupThread, hasWallpaper);
    setStoryReactionLabel(messageRecord);
    setHasBeenQuoted(conversationMessage);
    setHasBeenScheduled(conversationMessage);

    if (audioViewStub.resolved()) {
      audioViewStub.get().setOnLongClickListener(passthroughClickListener);
    }

    isBound = true;
    this.author.observeForever(this);
    this.conversationRecipient.observeForever(this);
  }

  @Override
  public void setParentScrolling(boolean isParentScrolling) {
    bodyBubble.setParentScrolling(isParentScrolling);
  }

  @Override
  public void updateSelectedState() {
    setHasBeenQuoted(conversationMessage);
  }

  @Override
  public void updateTimestamps() {
    getActiveFooter(messageRecord).setMessageRecord(messageRecord, locale, displayMode);
  }

  @Override
  public void updateContactNameColor() {
    setGroupAuthorColor(messageRecord, hasWallpaper, colorizer);
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    if (isCondensedMode()) return super.dispatchTouchEvent(ev);

    switch (ev.getAction()) {
      case MotionEvent.ACTION_DOWN:
        getHandler().postDelayed(shrinkBubble, SHRINK_BUBBLE_DELAY_MILLIS);
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        getHandler().removeCallbacks(shrinkBubble);
        bodyBubble.animate()
                  .scaleX(1.0f)
                  .scaleY(1.0f);
        reactionsView.animate()
                     .scaleX(1.0f)
                     .scaleY(1.0f);

        if (quotedIndicator != null) {
          quotedIndicator.animate()
                         .scaleX(1.0f)
                         .scaleY(1.0f);
        }
        break;
    }

    return super.dispatchTouchEvent(ev);
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {
    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
      lastYDownRelativeToThis = ev.getY();
    }

    if (batchSelected.isEmpty()) {
      return super.onInterceptTouchEvent(ev);
    } else {
      return true;
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    ConversationSwipeAnimationHelper.update(this, 0f, 1f);
    unbind();
    super.onDetachedFromWindow();
  }

  @Override
  public void setEventListener(@Nullable EventListener eventListener) {
    this.eventListener = eventListener;
  }

  @Override
  public void setGestureDetector(GestureDetector gestureDetector) {
    this.gestureDetector = gestureDetector;
  }

  public boolean disallowSwipe(float downX, float downY) {
    if (!hasAudio(messageRecord)) return false;

    audioViewStub.get().getSeekBarGlobalVisibleRect(SWIPE_RECT);
    return SWIPE_RECT.contains((int) downX, (int) downY);
  }

  public @Nullable ConversationItemBodyBubble getBodyBubble() {
    return bodyBubble;
  }

  public @Nullable View getQuotedIndicator() {
    return quotedIndicator;
  }

  public @Nullable ReactionsConversationView getReactionsView() {
    return reactionsView;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    if (isInEditMode()) {
      return;
    }

    boolean needsMeasure = false;

    if (hasQuote(messageRecord)) {
      if (quoteView == null) {
        throw new AssertionError();
      }
      int quoteWidth     = quoteView.getMeasuredWidth();
      int availableWidth = getAvailableMessageBubbleWidth(quoteView);

      if (quoteWidth != availableWidth) {
        quoteView.getLayoutParams().width = availableWidth;
        needsMeasure                      = true;
      }
    }

    int defaultTopMargin      = readDimen(R.dimen.message_bubble_default_footer_bottom_margin);
    int defaultBottomMargin   = readDimen(R.dimen.message_bubble_bottom_padding);
    int collapsedBottomMargin = readDimen(R.dimen.message_bubble_collapsed_bottom_padding);

    if (!updatingFooter &&
        getActiveFooter(messageRecord) == footer &&
        !hasAudio(messageRecord) &&
        !isStoryReaction(messageRecord) &&
        isFooterVisible(messageRecord, nextMessageRecord, groupThread) &&
        !bodyText.isJumbomoji() &&
        conversationMessage.getBottomButton() == null &&
        !StringUtil.hasMixedTextDirection(bodyText.getText()) &&
        !messageRecord.isRemoteDelete() &&
        bodyText.getLastLineWidth() > 0)
    {
      View dateView           = footer.getDateView();
      int  footerWidth        = footer.getMeasuredWidth();
      int  availableWidth     = getAvailableMessageBubbleWidth(bodyText);
      int  collapsedTopMargin = -1 * (dateView.getMeasuredHeight() + ViewUtil.dpToPx(4));

      if (bodyText.isSingleLine() && !messageRecord.isFailed()) {
        int maxBubbleWidth  = hasBigImageLinkPreview(messageRecord) || hasThumbnail(messageRecord) ? readDimen(R.dimen.media_bubble_max_width) : getMaxBubbleWidth();
        int bodyMargins     = ViewUtil.getLeftMargin(bodyText) + ViewUtil.getRightMargin(bodyText);
        int sizeWithMargins = bodyText.getMeasuredWidth() + ViewUtil.dpToPx(6) + footerWidth + bodyMargins;
        int minSize         = Math.min(maxBubbleWidth, Math.max(bodyText.getMeasuredWidth() + ViewUtil.dpToPx(6) + footerWidth + bodyMargins, bodyBubble.getMeasuredWidth()));

        if (hasQuote(messageRecord) && sizeWithMargins < availableWidth) {
          ViewUtil.setTopMargin(footer, collapsedTopMargin, false);
          ViewUtil.setBottomMargin(footer, collapsedBottomMargin, false);
          needsMeasure   = true;
          updatingFooter = true;
        } else if (sizeWithMargins != bodyText.getMeasuredWidth() && sizeWithMargins <= minSize) {
          bodyBubble.getLayoutParams().width = minSize;
          ViewUtil.setTopMargin(footer, collapsedTopMargin, false);
          ViewUtil.setBottomMargin(footer, collapsedBottomMargin, false);
          needsMeasure   = true;
          updatingFooter = true;
        }
      }

      if (!updatingFooter && !messageRecord.isFailed() && bodyText.getLastLineWidth() + ViewUtil.dpToPx(6) + footerWidth <= bodyText.getMeasuredWidth()) {
        ViewUtil.setTopMargin(footer, collapsedTopMargin, false);
        ViewUtil.setBottomMargin(footer, collapsedBottomMargin, false);
        updatingFooter = true;
        needsMeasure   = true;
      }
    }

    int defaultTopMarginForRecord = getDefaultTopMarginForRecord(messageRecord, defaultTopMargin, defaultBottomMargin);
    if (!updatingFooter && ViewUtil.getTopMargin(footer) != defaultTopMarginForRecord) {
      ViewUtil.setTopMargin(footer, defaultTopMarginForRecord, false);
      ViewUtil.setBottomMargin(footer, defaultBottomMargin, false);
      needsMeasure = true;
    }

    if (hasSharedContact(messageRecord)) {
      int contactWidth   = sharedContactStub.get().getMeasuredWidth();
      int availableWidth = getAvailableMessageBubbleWidth(sharedContactStub.get());

      if (contactWidth != availableWidth) {
        sharedContactStub.get().getLayoutParams().width = availableWidth;
        needsMeasure                                    = true;
      }
    }

    if (hasAudio(messageRecord)) {
      ConversationItemFooter activeFooter   = getActiveFooter(messageRecord);
      int                    availableWidth = getAvailableMessageBubbleWidth(footer);

      if (activeFooter.getVisibility() != GONE && activeFooter.getMeasuredWidth() != availableWidth) {
        activeFooter.getLayoutParams().width = availableWidth;
        needsMeasure                         = true;
      }

      int desiredWidth = audioViewStub.get().getMeasuredWidth() + ViewUtil.getLeftMargin(audioViewStub.get()) + ViewUtil.getRightMargin(audioViewStub.get());
      if (bodyBubble.getMeasuredWidth() != desiredWidth) {
        bodyBubble.getLayoutParams().width = desiredWidth;
        needsMeasure                       = true;
      }
    }

    if (needsMeasure) {
      if (measureCalls < MAX_MEASURE_CALLS) {
        measureCalls++;
        measure(widthMeasureSpec, heightMeasureSpec);
      } else {
        Log.w(TAG, "Hit measure() cap of " + MAX_MEASURE_CALLS);
      }
    } else {
      measureCalls   = 0;
      updatingFooter = false;
    }
  }

  private int getDefaultTopMarginForRecord(@NonNull MessageRecord messageRecord, int defaultTopMargin, int defaultBottomMargin) {
    if (isStoryReaction(messageRecord) && !messageRecord.isRemoteDelete()) {
      return defaultBottomMargin;
    } else {
      return defaultTopMargin;
    }
  }

  @Override
  public void onRecipientChanged(@NonNull Recipient modified) {
    if (!isBound) {
      return;
    }

    if (conversationRecipient.getId().equals(modified.getId())) {
      setBubbleState(messageRecord, modified, modified.getHasWallpaper(), colorizer);

      if (quoteView != null) {
        quoteView.setWallpaperEnabled(modified.getHasWallpaper());
      }

      if (audioViewStub.resolved()) {
        setAudioViewTint(messageRecord);
      }
    }

    if (author.getId().equals(modified.getId())) {
      setContactPhoto(modified);
      setGroupMessageStatus(messageRecord, modified);
    }
  }

  private int getAvailableMessageBubbleWidth(@NonNull View forView) {
    int availableWidth;
    if (hasAudio(messageRecord)) {
      availableWidth = audioViewStub.get().getMeasuredWidth() + ViewUtil.getLeftMargin(audioViewStub.get()) + ViewUtil.getRightMargin(audioViewStub.get());
    } else if (!isViewOnceMessage(messageRecord) && (hasThumbnail(messageRecord) || hasBigImageLinkPreview(messageRecord))) {
      availableWidth = mediaThumbnailStub.require().getMeasuredWidth();
    } else {
      availableWidth = bodyBubble.getMeasuredWidth() - bodyBubble.getPaddingLeft() - bodyBubble.getPaddingRight();
    }

    availableWidth = Math.min(availableWidth, getMaxBubbleWidth());

    availableWidth -= ViewUtil.getLeftMargin(forView) + ViewUtil.getRightMargin(forView);

    return availableWidth;
  }

  private int getMaxBubbleWidth() {
    int paddings = getPaddingLeft() + getPaddingRight() + ViewUtil.getLeftMargin(bodyBubble) + ViewUtil.getRightMargin(bodyBubble);
    if (groupThread && !messageRecord.isOutgoing() && !messageRecord.isRemoteDelete()) {
      paddings += contactPhoto.getLayoutParams().width + ViewUtil.getLeftMargin(contactPhoto) + ViewUtil.getRightMargin(contactPhoto);
    }
    return getMeasuredWidth() - paddings;
  }

  private void initializeAttributes() {
    defaultBubbleColor             = ContextCompat.getColor(context, R.color.conversation_item_recv_bubble_color_normal);
    defaultBubbleColorForWallpaper = ContextCompat.getColor(context, R.color.conversation_item_recv_bubble_color_wallpaper);
  }

  private @ColorInt int getDefaultBubbleColor(boolean hasWallpaper) {
    return hasWallpaper ? defaultBubbleColorForWallpaper : defaultBubbleColor;
  }

  @Override
  public void unbind() {
    isBound = false;

    if (author != null) {
      author.removeForeverObserver(this);
    }

    if (conversationRecipient != null) {
      conversationRecipient.removeForeverObserver(this);
    }

    bodyBubble.setVideoPlayerProjection(null);
    bodyBubble.setQuoteViewProjection(null);

    requestManager = null;
  }

  @Override
  public @NonNull MultiselectPart getMultiselectPartForLatestTouch() {
    MultiselectCollection parts = conversationMessage.getMultiselectCollection();

    if (parts.isSingle()) {
      return parts.asSingle().getSinglePart();
    }

    MultiselectPart top    = parts.asDouble().getTopPart();
    MultiselectPart bottom = parts.asDouble().getBottomPart();

    if (hasThumbnail(messageRecord)) {
      return isTouchBelowBoundary(mediaThumbnailStub.require()) ? bottom : top;
    } else if (hasDocument(messageRecord)) {
      return isTouchBelowBoundary(documentViewStub.get()) ? bottom : top;
    } else if (hasAudio(messageRecord)) {
      return isTouchBelowBoundary(audioViewStub.get()) ? bottom : top;
    } {
      throw new IllegalStateException("Found a situation where we have something other than a thumbnail or a document.");
    }
  }

  private boolean isTouchBelowBoundary(@NonNull View child) {
    Projection childProjection = Projection.relativeToParent(this, child, null);
    float      childBoundary   = childProjection.getY() + childProjection.getHeight();

    return lastYDownRelativeToThis > childBoundary;
  }

  @Override
  public int getTopBoundaryOfMultiselectPart(@NonNull MultiselectPart multiselectPart) {

    boolean isTextPart       = multiselectPart instanceof MultiselectPart.Text;
    boolean isAttachmentPart = multiselectPart instanceof MultiselectPart.Attachments;

    if (hasThumbnail(messageRecord) && isAttachmentPart) {
      return getProjectionTop(mediaThumbnailStub.require());
    } else if (hasThumbnail(messageRecord) && isTextPart) {
      return getProjectionBottom(mediaThumbnailStub.require());
    } else if (hasDocument(messageRecord) && isAttachmentPart) {
      return getProjectionTop(documentViewStub.get());
    } else if (hasDocument(messageRecord) && isTextPart) {
      return getProjectionBottom(documentViewStub.get());
    } else if (hasAudio(messageRecord) && isAttachmentPart) {
      return getProjectionTop(audioViewStub.get());
    } else if (hasAudio(messageRecord) && isTextPart) {
      return getProjectionBottom(audioViewStub.get());
    } else if (hasNoBubble(messageRecord)) {
      return getTop();
    } else {
      return getProjectionTop(bodyBubble);
    }
  }

  private static int getProjectionTop(@NonNull View child) {
    Projection projection = Projection.relativeToViewRoot(child, null);
    int        y          = (int) projection.getY();
    projection.release();
    return y;
  }

  private static int getProjectionBottom(@NonNull View child) {
    Projection projection = Projection.relativeToViewRoot(child, null);
    int        bottom     = (int) projection.getY() + projection.getHeight();
    projection.release();
    return bottom;
  }

  @Override
  public int getBottomBoundaryOfMultiselectPart(@NonNull MultiselectPart multiselectPart) {
    if (multiselectPart instanceof MultiselectPart.Attachments && hasThumbnail(messageRecord)) {
      return getProjectionBottom(mediaThumbnailStub.require());
    } else if (multiselectPart instanceof MultiselectPart.Attachments && hasDocument(messageRecord)) {
      return getProjectionBottom(documentViewStub.get());
    } else if (multiselectPart instanceof MultiselectPart.Attachments && hasAudio(messageRecord)) {
      return getProjectionBottom(audioViewStub.get());
    } else if (hasNoBubble(messageRecord)) {
      return getBottom();
    } else {
      return getProjectionBottom(bodyBubble);
    }
  }

  @Override
  public boolean hasNonSelectableMedia() {
    return hasQuote(messageRecord) || hasLinkPreview(messageRecord);
  }

  @Override
  public @NonNull ConversationMessage getConversationMessage() {
    return conversationMessage;
  }

  public boolean isOutgoing() {
    return conversationMessage.getMessageRecord().isOutgoing();
  }

  /// MessageRecord Attribute Parsers

  private void setBubbleState(MessageRecord messageRecord, @NonNull Recipient recipient, boolean hasWallpaper, @NonNull Colorizer colorizer) {
    this.hasWallpaper = hasWallpaper;

    ViewUtil.updateLayoutParams(bodyBubble, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    bodyText.setTextColor(colorizer.getIncomingBodyTextColor(context, hasWallpaper));
    bodyText.setLinkTextColor(colorizer.getIncomingBodyTextColor(context, hasWallpaper));

    if (messageRecord.isOutgoing() && !messageRecord.isRemoteDelete()) {
      bodyBubble.getBackground().setColorFilter(recipient.getChatColors().getChatBubbleColorFilter());
      bodyText.setTextColor(colorizer.getOutgoingBodyTextColor(context));
      bodyText.setLinkTextColor(colorizer.getOutgoingBodyTextColor(context));
      footer.setTextColor(colorizer.getOutgoingFooterTextColor(context));
      footer.setIconColor(colorizer.getOutgoingFooterIconColor(context));
      footer.setRevealDotColor(colorizer.getOutgoingFooterIconColor(context));
      footer.setOnlyShowSendingStatus(false, messageRecord);
    } else if (messageRecord.isRemoteDelete()) {
      if (hasWallpaper) {
        bodyBubble.getBackground().setColorFilter(ContextCompat.getColor(context, R.color.wallpaper_bubble_color), PorterDuff.Mode.SRC_IN);
      } else {
        bodyBubble.getBackground().setColorFilter(ContextCompat.getColor(context, R.color.signal_background_primary), PorterDuff.Mode.MULTIPLY);
        footer.setIconColor(ContextCompat.getColor(context, R.color.signal_icon_tint_secondary));
        footer.setRevealDotColor(ContextCompat.getColor(context, R.color.signal_icon_tint_secondary));
      }
      footer.setTextColor(ContextCompat.getColor(context, R.color.signal_text_secondary));
      footer.setOnlyShowSendingStatus(messageRecord.isRemoteDelete(), messageRecord);
    } else {
      bodyBubble.getBackground().setColorFilter(getDefaultBubbleColor(hasWallpaper), PorterDuff.Mode.SRC_IN);
      footer.setTextColor(colorizer.getIncomingFooterTextColor(context, hasWallpaper));
      footer.setIconColor(colorizer.getIncomingFooterIconColor(context, hasWallpaper));
      footer.setRevealDotColor(colorizer.getIncomingFooterIconColor(context, hasWallpaper));
      footer.setOnlyShowSendingStatus(false, messageRecord);
    }

    outliner.setColor(ContextCompat.getColor(context, R.color.signal_text_secondary));

    pulseOutliner.setColor(ContextCompat.getColor(getContext(), R.color.signal_inverse_transparent));
    pulseOutliner.setStrokeWidth(ViewUtil.dpToPx(4));

    outliners.clear();
    if (shouldDrawBodyBubbleOutline(messageRecord, hasWallpaper)) {
      outliners.add(outliner);
    }
    outliners.add(pulseOutliner);

    bodyBubble.setOutliners(outliners);

    if (audioViewStub.resolved()) {
      setAudioViewTint(messageRecord);
    }

    if (hasWallpaper) {
      replyIcon.setBackgroundResource(R.drawable.wallpaper_message_decoration_background);
    } else {
      replyIcon.setBackground(null);
    }
  }

  private void setAudioViewTint(MessageRecord messageRecord) {
    if (hasAudio(messageRecord)) {
      if (!messageRecord.isOutgoing()) {
        if (hasWallpaper) {
          audioViewStub.get().setTint(getContext().getResources().getColor(R.color.conversation_item_incoming_audio_foreground_tint_wallpaper));
          audioViewStub.get().setProgressAndPlayBackgroundTint(getContext().getResources().getColor(R.color.conversation_item_incoming_audio_play_pause_background_tint_wallpaper));
        } else {
          audioViewStub.get().setTint(getContext().getResources().getColor(R.color.conversation_item_incoming_audio_foreground_tint_normal));
          audioViewStub.get().setProgressAndPlayBackgroundTint(getContext().getResources().getColor(R.color.conversation_item_incoming_audio_play_pause_background_tint_normal));
        }
      } else {
        audioViewStub.get().setTint(getContext().getResources().getColor(R.color.conversation_item_outgoing_audio_foreground_tint));
        audioViewStub.get().setProgressAndPlayBackgroundTint(getContext().getResources().getColor(R.color.signal_colorTransparent2));
      }
    }
  }

  private void setInteractionState(ConversationMessage conversationMessage, boolean pulseMention) {
    Set<MultiselectPart> multiselectParts  = conversationMessage.getMultiselectCollection().toSet();
    boolean              isMessageSelected = Util.hasItems(Sets.intersection(multiselectParts, batchSelected));

    if (isMessageSelected) {
      setSelected(true);
    } else if (pulseMention) {
      setSelected(false);
    } else {
      setSelected(false);
    }

    if (mediaThumbnailStub.resolved()) {
      mediaThumbnailStub.require().setFocusable(!shouldInterceptClicks(conversationMessage.getMessageRecord()) && batchSelected.isEmpty());
      mediaThumbnailStub.require().setClickable(!shouldInterceptClicks(conversationMessage.getMessageRecord()) && batchSelected.isEmpty());
      mediaThumbnailStub.require().setLongClickable(batchSelected.isEmpty());
    }

    if (audioViewStub.resolved()) {
      audioViewStub.get().setFocusable(!shouldInterceptClicks(conversationMessage.getMessageRecord()) && batchSelected.isEmpty());
      audioViewStub.get().setClickable(batchSelected.isEmpty());
      audioViewStub.get().setEnabled(batchSelected.isEmpty());
    }

    if (documentViewStub.resolved()) {
      documentViewStub.get().setFocusable(!shouldInterceptClicks(conversationMessage.getMessageRecord()) && batchSelected.isEmpty());
      documentViewStub.get().setClickable(batchSelected.isEmpty());
    }
  }

  private boolean shouldDrawBodyBubbleOutline(MessageRecord messageRecord, boolean hasWallpaper) {
    if (hasWallpaper) {
      return false;
    } else {
      return messageRecord.isRemoteDelete();
    }
  }

  /**
   * Whether or not we're rendering this item in a constrained space.
   * Today this is only {@link org.thoughtcrime.securesms.conversation.quotes.MessageQuotesBottomSheet}.
   */
  private boolean isCondensedMode() {
    return displayMode instanceof ConversationItemDisplayMode.Condensed;
  }

  /**
   * Whether or not we want to condense the actual content of the bubble. e.g. shorten image height, text content, etc.
   * Today, we only want to do this for the first message when we're in condensed mode.
   */
  private boolean isContentCondensed() {
    return isCondensedMode() && !previousMessage.isPresent();
  }

  private boolean isStoryReaction(MessageRecord messageRecord) {
    return MessageRecordUtil.isStoryReaction(messageRecord);
  }

  private boolean isCaptionlessMms(MessageRecord messageRecord) {
    return MessageRecordUtil.isCaptionlessMms(messageRecord, context);
  }

  private boolean hasAudio(MessageRecord messageRecord) {
    return MessageRecordUtil.hasAudio(messageRecord);
  }

  private boolean hasThumbnail(MessageRecord messageRecord) {
    return MessageRecordUtil.hasThumbnail(messageRecord);
  }

  private boolean hasSticker(MessageRecord messageRecord) {
    return MessageRecordUtil.hasSticker(messageRecord);
  }

  private boolean isBorderless(MessageRecord messageRecord) {
    return MessageRecordUtil.isBorderless(messageRecord, context);
  }

  private boolean hasNoBubble(MessageRecord messageRecord) {
    return MessageRecordUtil.hasNoBubble(messageRecord, context);
  }

  private boolean hasOnlyThumbnail(MessageRecord messageRecord) {
    return MessageRecordUtil.hasOnlyThumbnail(messageRecord, context);
  }

  private boolean hasDocument(MessageRecord messageRecord) {
    return MessageRecordUtil.hasDocument(messageRecord);
  }

  private boolean hasExtraText(MessageRecord messageRecord) {
    return MessageRecordUtil.hasExtraText(messageRecord) || (!messageRecord.isDisplayBodyEmpty(context) && isContentCondensed());
  }

  private boolean hasQuote(MessageRecord messageRecord) {
    return MessageRecordUtil.hasQuote(messageRecord);
  }

  private boolean hasSharedContact(MessageRecord messageRecord) {
    return MessageRecordUtil.hasSharedContact(messageRecord);
  }

  private boolean hasLinkPreview(MessageRecord messageRecord) {
    return MessageRecordUtil.hasLinkPreview(messageRecord);
  }

  private boolean hasBigImageLinkPreview(MessageRecord messageRecord) {
    return MessageRecordUtil.hasBigImageLinkPreview(messageRecord, context) && !isContentCondensed();
  }

  private boolean isViewOnceMessage(MessageRecord messageRecord) {
    return MessageRecordUtil.isViewOnceMessage(messageRecord);
  }

  private boolean isGiftMessage(MessageRecord messageRecord) {
    return MessageRecordUtil.hasGiftBadge(messageRecord);
  }

  private void setBodyText(@NonNull MessageRecord messageRecord,
                           @Nullable String searchQuery,
                           boolean messageRequestAccepted)
  {
    bodyText.setClickable(false);
    bodyText.setFocusable(false);
    bodyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, SignalStore.settings().getMessageFontSize());
    bodyText.setMovementMethod(LongClickMovementMethod.getInstance(getContext()));

    if (messageRecord.isRemoteDelete()) {
      String          deletedMessage = context.getString(messageRecord.isOutgoing() ? R.string.ConversationItem_you_deleted_this_message : R.string.ConversationItem_this_message_was_deleted);
      SpannableString italics        = new SpannableString(deletedMessage);
      italics.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, deletedMessage.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      italics.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.signal_text_primary)),
                      0,
                      deletedMessage.length(),
                      Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

      bodyText.setText(italics);
      bodyText.setVisibility(View.VISIBLE);
      bodyText.setOverflowText(null);
    } else if (isCaptionlessMms(messageRecord) || isStoryReaction(messageRecord) || isGiftMessage(messageRecord) || messageRecord.isPaymentNotification() || messageRecord.isPaymentTombstone()) {
      bodyText.setText(null);
      bodyText.setOverflowText(null);
      bodyText.setVisibility(View.GONE);
    } else {
      Spannable styledText = conversationMessage.getDisplayBody(getContext());
      if (messageRequestAccepted) {
        linkifyMessageBody(styledText, batchSelected.isEmpty());
      }
      styledText = SearchUtil.getHighlightedSpan(locale, STYLE_FACTORY, styledText, searchQuery, SearchUtil.STRICT);

      if (hasExtraText(messageRecord)) {
        bodyText.setOverflowText(getLongMessageSpan(messageRecord));
      } else {
        bodyText.setOverflowText(null);
      }

      if (messageRecord.isOutgoing()) {
        bodyText.setMentionBackgroundTint(ContextCompat.getColor(context, R.color.transparent_black_25));
      } else {
        bodyText.setMentionBackgroundTint(ContextCompat.getColor(context, ThemeUtil.isDarkTheme(context) ? R.color.core_grey_60 : R.color.core_grey_20));
      }

      if (isContentCondensed()) {
        bodyText.setMaxLines(CONDENSED_MODE_MAX_LINES);
      } else {
        bodyText.setMaxLines(Integer.MAX_VALUE);
      }

      bodyText.setText(StringUtil.trim(styledText));
      bodyText.setVisibility(View.VISIBLE);

      if (conversationMessage.getBottomButton() != null) {
        callToActionStub.get().setVisibility(View.VISIBLE);
        callToActionStub.get().setText(conversationMessage.getBottomButton().label);
        callToActionStub.get().setOnClickListener(v -> {
          if (eventListener != null) {
            eventListener.onCallToAction(conversationMessage.getBottomButton().action);
          }
        });
      } else if (callToActionStub.resolved()) {
        callToActionStub.get().setVisibility(View.GONE);
      }
    }
  }

  private void setMediaAttributes(@NonNull MessageRecord messageRecord,
                                  @NonNull Optional<MessageRecord> previousRecord,
                                  @NonNull Optional<MessageRecord> nextRecord,
                                  boolean isGroupThread,
                                  boolean hasWallpaper,
                                  boolean messageRequestAccepted,
                                  boolean allowedToPlayInline)
  {
    boolean showControls = !MessageRecordUtil.isScheduled(messageRecord) && (messageRecord.isMediaPending() || !messageRecord.isFailed());

    ViewUtil.setTopMargin(bodyText, readDimen(R.dimen.message_bubble_top_padding));

    bodyBubble.setQuoteViewProjection(null);
    bodyBubble.setVideoPlayerProjection(null);

    if (eventListener != null && audioViewStub.resolved()) {
      Log.d(TAG, "setMediaAttributes: unregistering voice note callbacks for audio slide " + audioViewStub.get().getAudioSlideUri());
      eventListener.onUnregisterVoiceNoteCallbacks(audioViewStub.get().getPlaybackStateObserver());
    }

    footer.setPlaybackSpeedListener(null);

    if (isViewOnceMessage(messageRecord) && !messageRecord.isRemoteDelete()) {
      revealableStub.get().setVisibility(VISIBLE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.require().setVisibility(View.GONE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved()) sharedContactStub.get().setVisibility(GONE);
      if (linkPreviewStub.resolved()) linkPreviewStub.get().setVisibility(GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(View.GONE);
      if (giftViewStub.resolved()) giftViewStub.get().setVisibility(View.GONE);
      if (callToActionStub.resolved()) callToActionStub.get().setVisibility(View.GONE);
      if (joinCallLinkStub.resolved()) joinCallLinkStub.get().setVisibility(View.GONE);
      paymentViewStub.setVisibility(View.GONE);

      revealableStub.get().setMessage((MmsMessageRecord) messageRecord, hasWallpaper);
      revealableStub.get().setOnClickListener(revealableClickListener);
      revealableStub.get().setOnLongClickListener(passthroughClickListener);

      updateRevealableMargins(messageRecord, previousRecord, nextRecord, isGroupThread);

      footer.setVisibility(VISIBLE);
    } else if (hasSharedContact(messageRecord)) {
      sharedContactStub.get().setVisibility(VISIBLE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.require().setVisibility(View.GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(View.GONE);
      if (linkPreviewStub.resolved()) linkPreviewStub.get().setVisibility(GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(View.GONE);
      if (revealableStub.resolved()) revealableStub.get().setVisibility(View.GONE);
      if (giftViewStub.resolved()) giftViewStub.get().setVisibility(View.GONE);
      if (joinCallLinkStub.resolved()) joinCallLinkStub.get().setVisibility(View.GONE);
      paymentViewStub.setVisibility(View.GONE);

      sharedContactStub.get().setContact(((MmsMessageRecord) messageRecord).getSharedContacts().get(0), requestManager, locale);
      sharedContactStub.get().setEventListener(sharedContactEventListener);
      sharedContactStub.get().setOnClickListener(sharedContactClickListener);
      sharedContactStub.get().setOnLongClickListener(passthroughClickListener);

      setSharedContactCorners(messageRecord, previousRecord, nextRecord, isGroupThread);

      ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParamsIfNonNull(groupSenderHolder, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      footer.setVisibility(GONE);
    } else if (hasLinkPreview(messageRecord) && messageRequestAccepted) {
      linkPreviewStub.get().setVisibility(View.VISIBLE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.require().setVisibility(View.GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved()) sharedContactStub.get().setVisibility(GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(View.GONE);
      if (revealableStub.resolved()) revealableStub.get().setVisibility(View.GONE);
      if (giftViewStub.resolved()) giftViewStub.get().setVisibility(View.GONE);
      if (joinCallLinkStub.resolved()) joinCallLinkStub.get().setVisibility(View.GONE);
      paymentViewStub.setVisibility(View.GONE);

      //noinspection ConstantConditions
      LinkPreview linkPreview = ((MmsMessageRecord) messageRecord).getLinkPreviews().get(0);

      CallLinkRootKey callLinkRootKey = CallLinks.parseUrl(linkPreview.getUrl());
      if (callLinkRootKey != null) {
        joinCallLinkStub.setVisibility(View.VISIBLE);
        joinCallLinkStub.get().setTextColor(ContextCompat.getColor(context, messageRecord.isOutgoing() ? R.color.signal_light_colorOnPrimary : R.color.signal_colorOnPrimaryContainer));
        joinCallLinkStub.get().setBackgroundColor(ContextCompat.getColor(context, messageRecord.isOutgoing() ? R.color.signal_light_colorTransparent2 : R.color.signal_colorOnPrimary));
        joinCallLinkStub.get().setOnClickListener(v -> {
          if (eventListener != null) {
            eventListener.onJoinCallLink(callLinkRootKey);
          }
        });
      }

      if (hasBigImageLinkPreview(messageRecord)) {
        mediaThumbnailStub.require().setVisibility(VISIBLE);
        mediaThumbnailStub.require().setMinimumThumbnailWidth(readDimen(R.dimen.media_bubble_min_width_with_content));
        mediaThumbnailStub.require().setMaximumThumbnailHeight(readDimen(R.dimen.media_bubble_max_height));
        mediaThumbnailStub.require().setImageResource(requestManager, Collections.singletonList(new ImageSlide(linkPreview.getThumbnail().get())), showControls, false);
        mediaThumbnailStub.require().setThumbnailClickListener(new LinkPreviewThumbnailClickListener());
        mediaThumbnailStub.require().setStartTransferClickListener(downloadClickListener);
        mediaThumbnailStub.require().setCancelTransferClickListener(attachmentCancelClickListener);
        mediaThumbnailStub.require().setPlayVideoClickListener(playVideoClickListener);
        mediaThumbnailStub.require().setOnLongClickListener(passthroughClickListener);

        linkPreviewStub.get().setLinkPreview(requestManager, linkPreview, false);

        setThumbnailCorners(messageRecord, previousRecord, nextRecord, isGroupThread);
        setLinkPreviewCorners(messageRecord, previousRecord, nextRecord, isGroupThread, true);

        ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ViewUtil.updateLayoutParamsIfNonNull(groupSenderHolder, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ViewUtil.setTopMargin(linkPreviewStub.get(), 0);
      } else {
        linkPreviewStub.get().setLinkPreview(requestManager, linkPreview, true, !isContentCondensed(), displayMode.getScheduleMessageMode());
        linkPreviewStub.get().setDownloadClickedListener(downloadClickListener);
        setLinkPreviewCorners(messageRecord, previousRecord, nextRecord, isGroupThread, false);
        ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ViewUtil.updateLayoutParamsIfNonNull(groupSenderHolder, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        //noinspection ConstantConditions
        int topMargin = isGroupThread && isStartOfMessageCluster(messageRecord, previousRecord, isGroupThread) && !messageRecord.isOutgoing() ? readDimen(R.dimen.message_bubble_top_padding) : 0;
        ViewUtil.setTopMargin(linkPreviewStub.get(), topMargin);
      }

      linkPreviewStub.get().setOnClickListener(linkPreviewClickListener);
      linkPreviewStub.get().setOnLongClickListener(passthroughClickListener);
      linkPreviewStub.get().setBackgroundColor(getDefaultBubbleColor(hasWallpaper));

      footer.setVisibility(VISIBLE);
    } else if (hasAudio(messageRecord)) {
      audioViewStub.get().setVisibility(View.VISIBLE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.require().setVisibility(View.GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved()) sharedContactStub.get().setVisibility(GONE);
      if (linkPreviewStub.resolved()) linkPreviewStub.get().setVisibility(GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(View.GONE);
      if (revealableStub.resolved()) revealableStub.get().setVisibility(View.GONE);
      if (giftViewStub.resolved()) giftViewStub.get().setVisibility(View.GONE);
      if (joinCallLinkStub.resolved()) joinCallLinkStub.get().setVisibility(View.GONE);
      paymentViewStub.setVisibility(View.GONE);

      audioViewStub.get().setAudio(Objects.requireNonNull(((MmsMessageRecord) messageRecord).getSlideDeck().getAudioSlide()), new AudioViewCallbacks(), showControls, true);
      audioViewStub.get().setDownloadClickListener(singleDownloadClickListener);
      audioViewStub.get().setOnLongClickListener(passthroughClickListener);

      if (eventListener != null) {
        Log.d(TAG, "setMediaAttributes: registered listener for audio slide " + audioViewStub.get().getAudioSlideUri());
        eventListener.onRegisterVoiceNoteCallbacks(audioViewStub.get().getPlaybackStateObserver());
      } else {
        Log.w(TAG, "setMediaAttributes: could not register listener for audio slide " + audioViewStub.get().getAudioSlideUri());
      }

      ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParamsIfNonNull(groupSenderHolder, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

      footer.setPlaybackSpeedListener(new AudioPlaybackSpeedToggleListener());
      footer.setVisibility(VISIBLE);
    } else if (hasDocument(messageRecord)) {
      documentViewStub.get().setVisibility(View.VISIBLE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.require().setVisibility(View.GONE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved()) sharedContactStub.get().setVisibility(GONE);
      if (linkPreviewStub.resolved()) linkPreviewStub.get().setVisibility(GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(View.GONE);
      if (revealableStub.resolved()) revealableStub.get().setVisibility(View.GONE);
      if (giftViewStub.resolved()) giftViewStub.get().setVisibility(View.GONE);
      if (joinCallLinkStub.resolved()) joinCallLinkStub.get().setVisibility(View.GONE);
      paymentViewStub.setVisibility(View.GONE);

      //noinspection ConstantConditions
      documentViewStub.get().setDocument(
          ((MmsMessageRecord) messageRecord).getSlideDeck().getDocumentSlide(),
          showControls,
          displayMode != ConversationItemDisplayMode.Detailed.INSTANCE
      );
      documentViewStub.get().setDocumentClickListener(new ThumbnailClickListener());
      documentViewStub.get().setDownloadClickListener(singleDownloadClickListener);
      documentViewStub.get().setOnLongClickListener(passthroughClickListener);

      ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParamsIfNonNull(groupSenderHolder, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.setTopMargin(bodyText, 0);

      footer.setVisibility(VISIBLE);
    } else if ((hasSticker(messageRecord) && isCaptionlessMms(messageRecord)) || isBorderless(messageRecord)) {
      bodyBubble.setBackgroundColor(Color.TRANSPARENT);

      stickerStub.get().setVisibility(View.VISIBLE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.require().setVisibility(View.GONE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved()) sharedContactStub.get().setVisibility(GONE);
      if (linkPreviewStub.resolved()) linkPreviewStub.get().setVisibility(GONE);
      if (revealableStub.resolved()) revealableStub.get().setVisibility(View.GONE);
      if (giftViewStub.resolved()) giftViewStub.get().setVisibility(View.GONE);
      if (joinCallLinkStub.resolved()) joinCallLinkStub.get().setVisibility(View.GONE);
      paymentViewStub.setVisibility(View.GONE);

      if (hasSticker(messageRecord)) {
        //noinspection ConstantConditions
        stickerStub.get().setSlide(requestManager, ((MmsMessageRecord) messageRecord).getSlideDeck().getStickerSlide());
        stickerStub.get().setThumbnailClickListener(new StickerClickListener());
      } else {
        //noinspection ConstantConditions
        stickerStub.get().setSlide(requestManager, ((MmsMessageRecord) messageRecord).getSlideDeck().getThumbnailSlide());
        stickerStub.get().setThumbnailClickListener((v, slide) -> performClick());
      }

      stickerStub.get().setDownloadClickListener(downloadClickListener);
      stickerStub.get().setOnLongClickListener(passthroughClickListener);
      stickerStub.get().setOnClickListener(passthroughClickListener);

      ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParamsIfNonNull(groupSenderHolder, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

      footer.setVisibility(VISIBLE);
    } else if (hasNoBubble(messageRecord)) {
      bodyBubble.setBackgroundColor(Color.TRANSPARENT);
    } else if (hasThumbnail(messageRecord)) {
      mediaThumbnailStub.require().setVisibility(View.VISIBLE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved()) sharedContactStub.get().setVisibility(GONE);
      if (linkPreviewStub.resolved()) linkPreviewStub.get().setVisibility(GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(View.GONE);
      if (revealableStub.resolved()) revealableStub.get().setVisibility(View.GONE);
      if (giftViewStub.resolved()) giftViewStub.get().setVisibility(View.GONE);
      if (joinCallLinkStub.resolved()) joinCallLinkStub.get().setVisibility(View.GONE);
      paymentViewStub.setVisibility(View.GONE);

      final SlideDeck slideDeck       = ((MmsMessageRecord) messageRecord).getSlideDeck();
      List<Slide>     thumbnailSlides = slideDeck.getThumbnailSlides();
      mediaThumbnailStub.require().setMinimumThumbnailWidth(readDimen(isCaptionlessMms(messageRecord) ? R.dimen.media_bubble_min_width_solo
                                                                                                      : R.dimen.media_bubble_min_width_with_content));
      mediaThumbnailStub.require().setMaximumThumbnailHeight(readDimen(isContentCondensed() ? R.dimen.media_bubble_max_height_condensed
                                                                                            : R.dimen.media_bubble_max_height));

      mediaThumbnailStub.require().setThumbnailClickListener(new ThumbnailClickListener());
      mediaThumbnailStub.require().setCancelTransferClickListener(attachmentCancelClickListener);
      mediaThumbnailStub.require().setPlayVideoClickListener(playVideoClickListener);
      mediaThumbnailStub.require().setOnLongClickListener(passthroughClickListener);
      mediaThumbnailStub.require().setOnClickListener(passthroughClickListener);
      mediaThumbnailStub.require().showShade(messageRecord.isDisplayBodyEmpty(getContext()) && !hasExtraText(messageRecord));
      mediaThumbnailStub.require().setImageResource(requestManager,
                                                    thumbnailSlides,
                                                    showControls,
                                                    false);
      if (!messageRecord.isOutgoing()) {
        mediaThumbnailStub.require().setConversationColor(getDefaultBubbleColor(hasWallpaper));
        mediaThumbnailStub.require().setStartTransferClickListener(downloadClickListener);
      } else {
        mediaThumbnailStub.require().setConversationColor(Color.TRANSPARENT);
        if (doAnySlidesLackData(slideDeck)) {
          mediaThumbnailStub.require().setStartTransferClickListener(downloadClickListener);
        } else {
          mediaThumbnailStub.require().setStartTransferClickListener(new ResendClickListener(messageRecord));
        }
      }

      mediaThumbnailStub.require().setBorderless(false);

      setThumbnailCorners(messageRecord, previousRecord, nextRecord, isGroupThread);

      ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParamsIfNonNull(groupSenderHolder, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

      footer.setVisibility(VISIBLE);

      if (thumbnailSlides.size() == 1 &&
          thumbnailSlides.get(0).isVideoGif() &&
          thumbnailSlides.get(0) instanceof VideoSlide)
      {
        Uri uri = thumbnailSlides.get(0).getUri();
        if (uri != null) {
          mediaItem = MediaItem.fromUri(uri);
        } else {
          mediaItem = null;
        }

        canPlayContent = (GiphyMp4PlaybackPolicy.autoplay() || allowedToPlayInline) && mediaItem != null;
      }

    } else if (isGiftMessage(messageRecord)) {
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.require().setVisibility(GONE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(GONE);
      if (sharedContactStub.resolved()) sharedContactStub.get().setVisibility(GONE);
      if (linkPreviewStub.resolved()) linkPreviewStub.get().setVisibility(GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(GONE);
      if (revealableStub.resolved()) revealableStub.get().setVisibility(GONE);
      if (joinCallLinkStub.resolved()) joinCallLinkStub.get().setVisibility(View.GONE);
      paymentViewStub.setVisibility(View.GONE);

      MmsMessageRecord mmsMessageRecord = (MmsMessageRecord) messageRecord;
      giftViewStub.get().setGiftBadge(requestManager, Objects.requireNonNull(mmsMessageRecord.getGiftBadge()), messageRecord.isOutgoing(), giftMessageViewCallback, messageRecord.getFromRecipient(), messageRecord.getToRecipient());
      giftViewStub.get().setVisibility(VISIBLE);

      footer.setVisibility(VISIBLE);
    } else if (messageRecord.isPaymentNotification()) {
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.require().setVisibility(GONE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(GONE);
      if (sharedContactStub.resolved()) sharedContactStub.get().setVisibility(GONE);
      if (linkPreviewStub.resolved()) linkPreviewStub.get().setVisibility(GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(GONE);
      if (revealableStub.resolved()) revealableStub.get().setVisibility(GONE);
      if (giftViewStub.resolved()) giftViewStub.get().setVisibility(View.GONE);
      if (joinCallLinkStub.resolved()) joinCallLinkStub.get().setVisibility(View.GONE);

      MmsMessageRecord mediaMmsMessageRecord = (MmsMessageRecord) messageRecord;

      paymentViewStub.setVisibility(View.VISIBLE);
      paymentViewStub.get().setOnTombstoneClickListener(paymentTombstoneClickListener);
      paymentViewStub.get().bindPayment(conversationRecipient.get(), Objects.requireNonNull(mediaMmsMessageRecord.getPayment()), colorizer);

      footer.setVisibility(VISIBLE);
    } else if (messageRecord.isPaymentTombstone()) {
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.require().setVisibility(GONE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(GONE);
      if (sharedContactStub.resolved()) sharedContactStub.get().setVisibility(GONE);
      if (linkPreviewStub.resolved()) linkPreviewStub.get().setVisibility(GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(GONE);
      if (revealableStub.resolved()) revealableStub.get().setVisibility(GONE);
      if (giftViewStub.resolved()) giftViewStub.get().setVisibility(View.GONE);
      if (joinCallLinkStub.resolved()) joinCallLinkStub.get().setVisibility(View.GONE);

      MmsMessageRecord mediaMmsMessageRecord = (MmsMessageRecord) messageRecord;

      paymentViewStub.setVisibility(View.VISIBLE);
      paymentViewStub.get().setOnTombstoneClickListener(paymentTombstoneClickListener);
      MessageExtras messageExtras = mediaMmsMessageRecord.getMessageExtras();

      paymentViewStub.get().bindPaymentTombstone(mediaMmsMessageRecord.isOutgoing(), conversationRecipient.get(), messageExtras == null ? null : messageExtras.paymentTombstone, colorizer);

      footer.setVisibility(VISIBLE);
    } else {
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.require().setVisibility(View.GONE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved()) sharedContactStub.get().setVisibility(GONE);
      if (linkPreviewStub.resolved()) linkPreviewStub.get().setVisibility(GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(View.GONE);
      if (revealableStub.resolved()) revealableStub.get().setVisibility(View.GONE);
      if (giftViewStub.resolved()) giftViewStub.get().setVisibility(View.GONE);
      if (joinCallLinkStub.resolved()) joinCallLinkStub.get().setVisibility(View.GONE);
      paymentViewStub.setVisibility(View.GONE);

      ViewUtil.updateLayoutParams(bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParamsIfNonNull(groupSenderHolder, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

      footer.setVisibility(VISIBLE);

      //noinspection ConstantConditions
      int topMargin = !messageRecord.isOutgoing() && isGroupThread && isStartOfMessageCluster(messageRecord, previousRecord, isGroupThread)
                      ? readDimen(R.dimen.message_bubble_text_only_top_margin)
                      : readDimen(R.dimen.message_bubble_top_padding);
      ViewUtil.setTopMargin(bodyText, topMargin);
    }
  }

  private void updateRevealableMargins(MessageRecord messageRecord, Optional<MessageRecord> previous, Optional<MessageRecord> next, boolean isGroupThread) {
    int bigMargin   = readDimen(R.dimen.message_bubble_revealable_padding);
    int smallMargin = readDimen(R.dimen.message_bubble_top_padding);

    //noinspection ConstantConditions
    if (messageRecord.isOutgoing() || !isStartOfMessageCluster(messageRecord, previous, isGroupThread)) {
      ViewUtil.setTopMargin(revealableStub.get(), bigMargin);
    } else {
      ViewUtil.setTopMargin(revealableStub.get(), smallMargin);
    }

    if (isFooterVisible(messageRecord, next, isGroupThread)) {
      ViewUtil.setBottomMargin(revealableStub.get(), smallMargin);
    } else {
      ViewUtil.setBottomMargin(revealableStub.get(), bigMargin);
    }
  }

  private void setThumbnailCorners(@NonNull MessageRecord current,
                                   @NonNull Optional<MessageRecord> previous,
                                   @NonNull Optional<MessageRecord> next,
                                   boolean isGroupThread)
  {
    int defaultRadius  = readDimen(R.dimen.message_corner_radius);
    int collapseRadius = readDimen(R.dimen.message_corner_collapse_radius);

    int topStart    = defaultRadius;
    int topEnd      = defaultRadius;
    int bottomStart = defaultRadius;
    int bottomEnd   = defaultRadius;

    if (isSingularMessage(current, previous, next, isGroupThread)) {
      topStart    = defaultRadius;
      topEnd      = defaultRadius;
      bottomStart = defaultRadius;
      bottomEnd   = defaultRadius;
    } else if (isStartOfMessageCluster(current, previous, isGroupThread)) {
      if (current.isOutgoing()) {
        bottomEnd = collapseRadius;
      } else {
        bottomStart = collapseRadius;
      }
    } else if (isEndOfMessageCluster(current, next, isGroupThread)) {
      if (current.isOutgoing()) {
        topEnd = collapseRadius;
      } else {
        topStart = collapseRadius;
      }
    } else {
      if (current.isOutgoing()) {
        topEnd    = collapseRadius;
        bottomEnd = collapseRadius;
      } else {
        topStart    = collapseRadius;
        bottomStart = collapseRadius;
      }
    }

    if (!current.isDisplayBodyEmpty(getContext())) {
      bottomStart = 0;
      bottomEnd   = 0;
    }

    if (isStartOfMessageCluster(current, previous, isGroupThread) && !current.isOutgoing() && isGroupThread) {
      topStart = 0;
      topEnd   = 0;
    }

    if (hasQuote(messageRecord)) {
      topStart = 0;
      topEnd   = 0;
    }

    if (hasLinkPreview(messageRecord) || hasExtraText(messageRecord)) {
      bottomStart = 0;
      bottomEnd   = 0;
    }

    if (ViewUtil.isRtl(this)) {
      mediaThumbnailStub.require().setCorners(topEnd, topStart, bottomStart, bottomEnd);
    } else {
      mediaThumbnailStub.require().setCorners(topStart, topEnd, bottomEnd, bottomStart);
    }
  }

  private void setSharedContactCorners(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    if (messageRecord.isDisplayBodyEmpty(getContext())) {
      if (isSingularMessage(current, previous, next, isGroupThread) || isEndOfMessageCluster(current, next, isGroupThread)) {
        sharedContactStub.get().setSingularStyle();
      } else if (current.isOutgoing()) {
        sharedContactStub.get().setClusteredOutgoingStyle();
      } else {
        sharedContactStub.get().setClusteredIncomingStyle();
      }
    }
  }

  private void setLinkPreviewCorners(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, @NonNull Optional<MessageRecord> next, boolean isGroupThread, boolean bigImage) {
    int defaultRadius  = readDimen(R.dimen.message_corner_radius);
    int collapseRadius = readDimen(R.dimen.message_corner_collapse_radius);

    if (bigImage || hasQuote(current)) {
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

    final RecipientId recipientId = recipient.getId();

    contactPhoto.setOnClickListener(v -> {
      if (eventListener != null) {
        eventListener.onGroupMemberClicked(recipientId, conversationRecipient.get().requireGroupId());
      }
    });

    contactPhoto.setAvatar(requestManager, recipient, false);
    badgeImageView.setBadgeFromRecipient(recipient, requestManager);
    badgeImageView.setClickable(false);
  }

  private void linkifyMessageBody(@NonNull Spannable messageBody,
                                  boolean shouldLinkifyAllLinks)
  {
    V2ConversationItemUtils.linkifyUrlLinks(messageBody, shouldLinkifyAllLinks, urlClickListener);

    if (conversationMessage.hasStyleLinks()) {
      for (PlaceholderURLSpan placeholder : messageBody.getSpans(0, messageBody.length(), PlaceholderURLSpan.class)) {
        int     start = messageBody.getSpanStart(placeholder);
        int     end   = messageBody.getSpanEnd(placeholder);
        URLSpan span  = new InterceptableLongClickCopyLinkSpan(placeholder.getValue(),
                                                               urlClickListener,
                                                               ContextCompat.getColor(getContext(), R.color.signal_accent_primary),
                                                               false);

        messageBody.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }

    List<Annotation> mentionAnnotations = MentionAnnotation.getMentionAnnotations(messageBody);
    for (Annotation annotation : mentionAnnotations) {
      messageBody.setSpan(new MentionClickableSpan(RecipientId.from(annotation.getValue())), messageBody.getSpanStart(annotation), messageBody.getSpanEnd(annotation), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
  }

  private void setStatusIcons(MessageRecord messageRecord, boolean hasWallpaper) {
    bodyText.setCompoundDrawablesWithIntrinsicBounds(0, 0, messageRecord.isKeyExchange() ? R.drawable.symbol_key_24 : 0, 0);

    if (!messageRecord.isMediaPending() && messageRecord.isFailed()) {
      alertView.setFailed();
    } else if (messageRecord.isRateLimited()) {
      alertView.setRateLimited();
    } else {
      alertView.setNone();
    }

    if (hasWallpaper) {
      alertView.setBackgroundResource(R.drawable.wallpaper_message_decoration_background);
    } else {
      alertView.setBackground(null);
    }
  }

  private void setQuote(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    boolean startOfCluster = isStartOfMessageCluster(current, previous, isGroupThread);
    if (hasQuote(messageRecord)) {
      if (quoteView == null) {
        throw new AssertionError();
      }
      Quote quote = ((MmsMessageRecord) current).getQuote();

      if (((MmsMessageRecord) current).getParentStoryId() != null) {
        quoteView.setMessageType(current.isOutgoing() ? QuoteView.MessageType.STORY_REPLY_OUTGOING : QuoteView.MessageType.STORY_REPLY_INCOMING);
      } else {
        quoteView.setMessageType(current.isOutgoing() ? QuoteView.MessageType.OUTGOING : QuoteView.MessageType.INCOMING);
      }

      //noinspection ConstantConditions
      quoteView.setQuote(requestManager,
                         quote.getId(),
                         Recipient.live(quote.getAuthor()).get(),
                         quote.getDisplayText(),
                         quote.isOriginalMissing(),
                         quote.getAttachment(),
                         isStoryReaction(current) ? current.getBody() : null,
                         quote.getQuoteType());

      quoteView.setWallpaperEnabled(hasWallpaper);
      quoteView.setVisibility(View.VISIBLE);
      quoteView.setTextSize(TypedValue.COMPLEX_UNIT_SP, SignalStore.settings().getMessageQuoteFontSize(context));
      quoteView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;

      quoteView.setOnClickListener(view -> {
        if (eventListener != null && batchSelected.isEmpty()) {
          eventListener.onQuoteClicked((MmsMessageRecord) current);
        } else {
          passthroughClickListener.onClick(view);
        }
      });

      quoteView.setOnLongClickListener(passthroughClickListener);

      if (startOfCluster) {
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

      if (!isFooterVisible(current, next, isGroupThread) && isStoryReaction(current)) {
        ViewUtil.setBottomMargin(quoteView, (int) DimensionUnit.DP.toPixels(8), false);
      } else {
        ViewUtil.setBottomMargin(quoteView, 0, false);
      }

      if (mediaThumbnailStub.resolved()) {
        ViewUtil.setTopMargin(mediaThumbnailStub.require(), readDimen(R.dimen.message_bubble_top_padding), false);
      }

      if (linkPreviewStub.resolved() && !hasBigImageLinkPreview(current)) {
        ViewUtil.setTopMargin(linkPreviewStub.get(), readDimen(R.dimen.message_bubble_top_padding), false);
      }
    } else {
      if (quoteView != null) {
        quoteView.dismiss();
      }

      int topMargin = (current.isOutgoing() || !startOfCluster || !groupThread) ? 0 : readDimen(R.dimen.message_bubble_top_image_margin);
      if (mediaThumbnailStub.resolved()) {
        ViewUtil.setTopMargin(mediaThumbnailStub.require(), topMargin, false);
      }
    }
  }

  private void setGutterSizes(@NonNull MessageRecord current, boolean isGroupThread) {
    if (isGroupThread && current.isOutgoing()) {
      ViewUtil.setPaddingStart(this, readDimen(R.dimen.conversation_group_left_gutter));
      ViewUtil.setPaddingEnd(this, readDimen(R.dimen.conversation_individual_right_gutter));
    } else if (current.isOutgoing()) {
      ViewUtil.setPaddingStart(this, readDimen(R.dimen.conversation_individual_left_gutter));
      ViewUtil.setPaddingEnd(this, readDimen(R.dimen.conversation_individual_right_gutter));
    } else {
      ViewUtil.setPaddingStart(this, readDimen(R.dimen.conversation_individual_received_left_gutter));
      ViewUtil.setPaddingEnd(this, readDimen(R.dimen.conversation_individual_right_gutter));
    }
  }

  private void setReactions(@NonNull MessageRecord current) {
    bodyBubble.setOnSizeChangedListener(null);

    if (current.getReactions().isEmpty()) {
      reactionsView.clear();
      return;
    }

    setReactionsWithWidth(current, bodyBubble.getWidth());
    bodyBubble.setOnSizeChangedListener((width, height) -> setReactionsWithWidth(current, width));
  }

  private void setReactionsWithWidth(@NonNull MessageRecord current, int width) {
    reactionsView.setReactions(current.getReactions());
    reactionsView.setBubbleWidth(width);
    reactionsView.setOnClickListener(v -> {
      if (eventListener == null) return;

      eventListener.onReactionClicked(new MultiselectPart.Message(conversationMessage), current.getId(), current.isMms());
    });
  }

  private void setFooter(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> next, @NonNull Locale locale, boolean isGroupThread, boolean hasWallpaper) {
    ViewUtil.updateLayoutParams(footer, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    ViewUtil.setTopMargin(footer, readDimen(R.dimen.message_bubble_default_footer_bottom_margin));

    footer.setVisibility(GONE);
    ViewUtil.setVisibilityIfNonNull(stickerFooter, GONE);
    if (sharedContactStub.resolved()) sharedContactStub.get().getFooter().setVisibility(GONE);
    if (mediaThumbnailStub.resolved() && mediaThumbnailStub.require().getFooter().resolved()) {
      mediaThumbnailStub.require().getFooter().setVisibility(GONE);
    }

    if (isFooterVisible(current, next, isGroupThread)) {
      ConversationItemFooter activeFooter = getActiveFooter(current);
      activeFooter.setVisibility(VISIBLE);
      activeFooter.setMessageRecord(current, locale, displayMode);

      if (MessageRecordUtil.isEditMessage(current)) {
        activeFooter.getDateView().setOnClickListener(v -> {
          if (eventListener != null) {
            eventListener.onEditedIndicatorClicked(conversationMessage);
          }
        });
      } else {
        activeFooter.getDateView().setOnClickListener(null);
        activeFooter.getDateView().setClickable(false);
      }

      if (hasWallpaper && hasNoBubble((messageRecord))) {
        if (messageRecord.isOutgoing()) {
          activeFooter.disableBubbleBackground();
          activeFooter.setTextColor(ContextCompat.getColor(context, R.color.conversation_item_sent_text_secondary_color));
          activeFooter.setIconColor(ContextCompat.getColor(context, R.color.conversation_item_sent_text_secondary_color));
          activeFooter.setRevealDotColor(ContextCompat.getColor(context, R.color.conversation_item_sent_text_secondary_color));
        } else {
          activeFooter.enableBubbleBackground(R.drawable.wallpaper_bubble_background_tintable_11, getDefaultBubbleColor(hasWallpaper));
        }
      } else if (hasNoBubble(messageRecord)) {
        activeFooter.disableBubbleBackground();
        activeFooter.setTextColor(ContextCompat.getColor(context, R.color.signal_text_secondary));
        activeFooter.setIconColor(ContextCompat.getColor(context, R.color.signal_icon_tint_secondary));
        activeFooter.setRevealDotColor(ContextCompat.getColor(context, R.color.signal_icon_tint_secondary));
      } else {
        activeFooter.disableBubbleBackground();
      }
    }
  }

  private void setStoryReactionLabel(@NonNull MessageRecord record) {
    if (isStoryReaction(record) && !record.isRemoteDelete()) {
      storyReactionLabelWrapper.setVisibility(View.VISIBLE);
      storyReactionLabel.setTextColor(record.isOutgoing() ? colorizer.getOutgoingBodyTextColor(context) : ContextCompat.getColor(context, R.color.signal_text_primary));
      storyReactionLabel.setText(getStoryReactionLabelText(messageRecord));
    } else if (storyReactionLabelWrapper != null) {
      storyReactionLabelWrapper.setVisibility(View.GONE);
    }
  }

  private @NonNull String getStoryReactionLabelText(@NonNull MessageRecord messageRecord) {
    if (hasQuote(messageRecord)) {
      MmsMessageRecord mmsMessageRecord = (MmsMessageRecord) messageRecord;
      RecipientId      author           = mmsMessageRecord.getQuote().getAuthor();

      if (author.equals(Recipient.self().getId())) {
        return context.getString(R.string.ConversationItem__reacted_to_your_story);
      } else {
        return context.getString(R.string.ConversationItem__you_reacted_to_s_story, Recipient.resolved(author).getShortDisplayName(context));
      }
    } else {
      return context.getString(R.string.ConversationItem__reacted_to_a_story);
    }
  }

  private void setHasBeenQuoted(@NonNull ConversationMessage message) {
    if (message.hasBeenQuoted() && !isCondensedMode() && quotedIndicator != null && batchSelected.isEmpty() && displayMode != ConversationItemDisplayMode.EditHistory.INSTANCE) {
      quotedIndicator.setVisibility(VISIBLE);
      quotedIndicator.setOnClickListener(quotedIndicatorClickListener);
    } else if (quotedIndicator != null) {
      quotedIndicator.setVisibility(GONE);
      quotedIndicator.setOnClickListener(null);
    }
  }

  private void setHasBeenScheduled(@NonNull ConversationMessage message) {
    if (scheduledIndicator == null) {
      return;
    }
    if (message.hasBeenScheduled()) {
      scheduledIndicator.setVisibility(View.VISIBLE);
      scheduledIndicator.setOnClickListener(scheduledIndicatorClickListener);
    } else {
      scheduledIndicator.setVisibility(View.GONE);
      scheduledIndicator.setOnClickListener(null);
    }
  }

  private boolean forceFooter(@NonNull MessageRecord messageRecord) {
    return hasAudio(messageRecord) || MessageRecordUtil.isEditMessage(messageRecord) || displayMode == ConversationItemDisplayMode.EditHistory.INSTANCE;
  }

  private boolean forceGroupHeader(@NonNull MessageRecord messageRecord) {
    return displayMode == ConversationItemDisplayMode.EditHistory.INSTANCE;
  }

  private ConversationItemFooter getActiveFooter(@NonNull MessageRecord messageRecord) {
    if (hasNoBubble(messageRecord) && stickerFooter != null) {
      return stickerFooter;
    } else if (hasSharedContact(messageRecord) && messageRecord.isDisplayBodyEmpty(getContext())) {
      return sharedContactStub.get().getFooter();
    } else if (hasOnlyThumbnail(messageRecord) && messageRecord.isDisplayBodyEmpty(getContext())) {
      return mediaThumbnailStub.require().getFooter().get();
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
            (messageRecord.isRateLimited() && SignalStore.rateLimit().needsRecaptcha()) ||
            messageRecord.isBundleKeyExchange());
  }

  @SuppressLint("SetTextI18n")
  private void setGroupMessageStatus(MessageRecord messageRecord, Recipient recipient) {
    if (groupThread && !messageRecord.isOutgoing() && groupSender != null) {
      groupSender.setText(recipient.getDisplayName(getContext()));
    }
  }

  private void setGroupAuthorColor(@NonNull MessageRecord messageRecord, boolean hasWallpaper, @NonNull Colorizer colorizer) {
    if (groupSender != null) {
      groupSender.setTextColor(colorizer.getIncomingGroupSenderColor(getContext(), messageRecord.getFromRecipient()));
    }
  }

  @SuppressWarnings("ConstantConditions")
  private void setAuthor(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, @NonNull Optional<MessageRecord> next, boolean isGroupThread, boolean hasWallpaper) {
    if (isGroupThread && !current.isOutgoing()) {
      contactPhotoHolder.setVisibility(VISIBLE);

      if (!previous.isPresent() || previous.get().isUpdate() || !current.getFromRecipient().equals(previous.get().getFromRecipient()) ||
          !DateUtils.isSameDay(previous.get().getTimestamp(), current.getTimestamp()) || !isWithinClusteringTime(current, previous.get()) || forceGroupHeader(current))
      {
        groupSenderHolder.setVisibility(VISIBLE);

        if (hasWallpaper && hasNoBubble(current)) {
          groupSenderHolder.setBackgroundResource(R.drawable.wallpaper_bubble_background_tintable_11);
          groupSenderHolder.getBackground().setColorFilter(getDefaultBubbleColor(hasWallpaper), PorterDuff.Mode.MULTIPLY);
        } else {
          groupSenderHolder.setBackground(null);
        }
      } else {
        groupSenderHolder.setVisibility(GONE);
      }

      if (!next.isPresent() || next.get().isUpdate() || !current.getFromRecipient().equals(next.get().getFromRecipient()) || !isWithinClusteringTime(current, next.get()) || forceGroupHeader(current)) {
        contactPhoto.setVisibility(VISIBLE);
        badgeImageView.setVisibility(VISIBLE);
      } else {
        contactPhoto.setVisibility(GONE);
        badgeImageView.setVisibility(GONE);
      }
    } else {
      if (groupSenderHolder != null) {
        groupSenderHolder.setVisibility(GONE);
      }

      if (contactPhotoHolder != null) {
        contactPhotoHolder.setVisibility(GONE);
      }

      if (badgeImageView != null) {
        badgeImageView.setVisibility(GONE);
      }
    }
  }

  private void setOutlinerRadii(Outliner outliner, int topStart, int topEnd, int bottomEnd, int bottomStart) {
    if (ViewUtil.isRtl(this)) {
      outliner.setRadii(topEnd, topStart, bottomStart, bottomEnd);
    } else {
      outliner.setRadii(topStart, topEnd, bottomEnd, bottomStart);
    }
  }

  private @NonNull Projection.Corners getBodyBubbleCorners(int topStart, int topEnd, int bottomEnd, int bottomStart) {
    if (ViewUtil.isRtl(this)) {
      return new Projection.Corners(topEnd, topStart, bottomStart, bottomEnd);
    } else {
      return new Projection.Corners(topStart, topEnd, bottomEnd, bottomStart);
    }
  }

  private void setMessageShape(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    int bigRadius   = readDimen(R.dimen.message_corner_radius);
    int smallRadius = readDimen(R.dimen.message_corner_collapse_radius);

    int background;

    if (isSingularMessage(current, previous, next, isGroupThread) || displayMode == ConversationItemDisplayMode.EditHistory.INSTANCE) {
      if (current.isOutgoing()) {
        background = R.drawable.message_bubble_background_sent_alone;
        outliner.setRadius(bigRadius);
        pulseOutliner.setRadius(bigRadius);
        bodyBubbleCorners = new Projection.Corners(bigRadius);
      } else {
        background = R.drawable.message_bubble_background_received_alone;
        outliner.setRadius(bigRadius);
        pulseOutliner.setRadius(bigRadius);
        bodyBubbleCorners = new Projection.Corners(bigRadius);
      }
    } else if (isStartOfMessageCluster(current, previous, isGroupThread)) {
      if (current.isOutgoing()) {
        background = R.drawable.message_bubble_background_sent_start;
        setOutlinerRadii(outliner, bigRadius, bigRadius, smallRadius, bigRadius);
        setOutlinerRadii(pulseOutliner, bigRadius, bigRadius, smallRadius, bigRadius);
        bodyBubbleCorners = getBodyBubbleCorners(bigRadius, bigRadius, smallRadius, bigRadius);
      } else {
        background = R.drawable.message_bubble_background_received_start;
        setOutlinerRadii(outliner, bigRadius, bigRadius, bigRadius, smallRadius);
        setOutlinerRadii(pulseOutliner, bigRadius, bigRadius, bigRadius, smallRadius);
        bodyBubbleCorners = getBodyBubbleCorners(bigRadius, bigRadius, bigRadius, smallRadius);
      }
    } else if (isEndOfMessageCluster(current, next, isGroupThread)) {
      if (current.isOutgoing()) {
        background = R.drawable.message_bubble_background_sent_end;
        setOutlinerRadii(outliner, bigRadius, smallRadius, bigRadius, bigRadius);
        setOutlinerRadii(pulseOutliner, bigRadius, smallRadius, bigRadius, bigRadius);
        bodyBubbleCorners = getBodyBubbleCorners(bigRadius, smallRadius, bigRadius, bigRadius);
      } else {
        background = R.drawable.message_bubble_background_received_end;
        setOutlinerRadii(outliner, smallRadius, bigRadius, bigRadius, bigRadius);
        setOutlinerRadii(pulseOutliner, smallRadius, bigRadius, bigRadius, bigRadius);
        bodyBubbleCorners = getBodyBubbleCorners(smallRadius, bigRadius, bigRadius, bigRadius);
      }
    } else {
      if (current.isOutgoing()) {
        background = R.drawable.message_bubble_background_sent_middle;
        setOutlinerRadii(outliner, bigRadius, smallRadius, smallRadius, bigRadius);
        setOutlinerRadii(pulseOutliner, bigRadius, smallRadius, smallRadius, bigRadius);
        bodyBubbleCorners = getBodyBubbleCorners(bigRadius, smallRadius, smallRadius, bigRadius);
      } else {
        background = R.drawable.message_bubble_background_received_middle;
        setOutlinerRadii(outliner, smallRadius, bigRadius, bigRadius, smallRadius);
        setOutlinerRadii(pulseOutliner, smallRadius, bigRadius, bigRadius, smallRadius);
        bodyBubbleCorners = getBodyBubbleCorners(smallRadius, bigRadius, bigRadius, smallRadius);
      }
    }

    bodyBubble.setBackgroundResource(background);
  }

  private boolean isStartOfMessageCluster(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, boolean isGroupThread) {
    if (isGroupThread) {
      return !previous.isPresent() || previous.get().isUpdate() || !DateUtils.isSameDay(current.getTimestamp(), previous.get().getTimestamp()) ||
             !current.getFromRecipient().equals(previous.get().getFromRecipient()) || !isWithinClusteringTime(current, previous.get()) || MessageRecordUtil.isScheduled(current);
    } else {
      return !previous.isPresent() || previous.get().isUpdate() || !DateUtils.isSameDay(current.getTimestamp(), previous.get().getTimestamp()) ||
             current.isOutgoing() != previous.get().isOutgoing() || previous.get().isSecure() != current.isSecure() || !isWithinClusteringTime(current, previous.get()) ||
             MessageRecordUtil.isScheduled(current);
    }
  }

  private boolean isEndOfMessageCluster(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    if (isGroupThread) {
      return !next.isPresent() || next.get().isUpdate() || !DateUtils.isSameDay(current.getTimestamp(), next.get().getTimestamp()) ||
             !current.getFromRecipient().equals(next.get().getFromRecipient()) || !current.getReactions().isEmpty() || !isWithinClusteringTime(current, next.get()) ||
             MessageRecordUtil.isScheduled(current);
    } else {
      return !next.isPresent() || next.get().isUpdate() || !DateUtils.isSameDay(current.getTimestamp(), next.get().getTimestamp()) ||
             current.isOutgoing() != next.get().isOutgoing() || !current.getReactions().isEmpty() || next.get().isSecure() != current.isSecure() ||
             !isWithinClusteringTime(current, next.get()) || MessageRecordUtil.isScheduled(current);
    }
  }

  private boolean isSingularMessage(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    return isStartOfMessageCluster(current, previous, isGroupThread) && isEndOfMessageCluster(current, next, isGroupThread);
  }

  private boolean isFooterVisible(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    boolean differentTimestamps = next.isPresent() && !DateUtils.isSameExtendedRelativeTimestamp(next.get().getTimestamp(), current.getTimestamp());

    return forceFooter(messageRecord) || current.getExpiresIn() > 0 || !current.isSecure() || current.isPending() ||
           current.isFailed() || current.isRateLimited() || differentTimestamps || isEndOfMessageCluster(current, next, isGroupThread);
  }

  private static boolean isWithinClusteringTime(@NonNull MessageRecord lhs, @NonNull MessageRecord rhs) {
    long timeDiff = Math.abs(lhs.getDateSent() - rhs.getDateSent());
    return timeDiff <= MAX_CLUSTERING_TIME_DIFF;
  }

  private void setMessageSpacing(@NonNull Context context, @NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    int spacingTop    = readDimen(context, R.dimen.conversation_vertical_message_spacing_collapse);
    int spacingBottom = spacingTop;

    if (isStartOfMessageCluster(current, previous, isGroupThread) && (displayMode != ConversationItemDisplayMode.EditHistory.INSTANCE || next.isEmpty())) {
      spacingTop = readDimen(context, R.dimen.conversation_vertical_message_spacing_default);
    }

    if (isEndOfMessageCluster(current, next, isGroupThread) || displayMode == ConversationItemDisplayMode.EditHistory.INSTANCE) {
      spacingBottom = readDimen(context, R.dimen.conversation_vertical_message_spacing_default);
    }

    ViewUtil.setPaddingTop(this, spacingTop);
    ViewUtil.setPaddingBottom(this, spacingBottom);
  }

  private int readDimen(@NonNull Context context, @DimenRes int dimenId) {
    return context.getResources().getDimensionPixelOffset(dimenId);
  }

  private boolean doAnySlidesLackData(SlideDeck deck) {
    for (Attachment attachment : deck.asAttachments()) {
      if (attachment instanceof DatabaseAttachment && !((DatabaseAttachment) attachment).hasData) {
        return true;
      }
    }
    return false;
  }

  /// Event handlers

  private Spannable getLongMessageSpan(@NonNull MessageRecord messageRecord) {
    String   message;
    Runnable action;

    if (messageRecord.isMms()) {
      TextSlide slide = ((MmsMessageRecord) messageRecord).getSlideDeck().getTextSlide();

      if (slide != null && (slide.asAttachment().transferState == AttachmentTable.TRANSFER_PROGRESS_DONE || MessageRecordUtil.isScheduled(messageRecord))) {
        message = getResources().getString(R.string.ConversationItem_read_more);
        action  = () -> eventListener.onMoreTextClicked(conversationRecipient.getId(), messageRecord.getId(), messageRecord.isMms());
      } else if (slide != null && slide.asAttachment().transferState == AttachmentTable.TRANSFER_PROGRESS_STARTED) {
        message = getResources().getString(R.string.ConversationItem_pending);
        action  = () -> {};
      } else if (slide != null) {
        message = getResources().getString(R.string.ConversationItem_download_more);
        action  = () -> singleDownloadClickListener.onClick(bodyText, slide);
      } else {
        message = getResources().getString(R.string.ConversationItem_read_more);
        action  = () -> eventListener.onMoreTextClicked(conversationRecipient.getId(), messageRecord.getId(), messageRecord.isMms());
      }
    } else {
      message = getResources().getString(R.string.ConversationItem_read_more);
      action  = () -> eventListener.onMoreTextClicked(conversationRecipient.getId(), messageRecord.getId(), messageRecord.isMms());
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
  public void showProjectionArea() {
    if (mediaThumbnailStub != null && mediaThumbnailStub.resolved()) {
      mediaThumbnailStub.require().showThumbnailView();
      bodyBubble.setVideoPlayerProjection(null);
    }
  }

  @Override
  public void hideProjectionArea() {
    if (mediaThumbnailStub != null && mediaThumbnailStub.resolved()) {
      mediaThumbnailStub.require().hideThumbnailView();
      mediaThumbnailStub.require().getDrawingRect(thumbnailMaskingRect);
      bodyBubble.setVideoPlayerProjection(Projection.relativeToViewWithCommonRoot(mediaThumbnailStub.require(), bodyBubble, null));
    }
  }

  @Override
  public @Nullable MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  public @Nullable GiphyMp4PlaybackPolicyEnforcer getPlaybackPolicyEnforcer() {
    if (GiphyMp4PlaybackPolicy.autoplay()) {
      return null;
    } else {
      return new GiphyMp4PlaybackPolicyEnforcer(() -> {
        if (eventListener != null) {
          eventListener.onPlayInlineContent(null);
        }
      });
    }
  }

  @Override
  public int getAdapterPosition() {
    throw new UnsupportedOperationException("Do not delegate to this method");
  }

  @Override
  public @NonNull Projection getGiphyMp4PlayableProjection(@NonNull ViewGroup recyclerView) {
    if (mediaThumbnailStub != null && mediaThumbnailStub.isResolvable()) {
      ConversationItemThumbnail thumbnail = mediaThumbnailStub.require();
      return Projection.relativeToParent(recyclerView, thumbnail, thumbnail.getCorners())
                       .scale(bodyBubble.getScaleX())
                       .translateX(Util.halfOffsetFromScale(thumbnail.getWidth(), bodyBubble.getScaleX()))
                       .translateY(Util.halfOffsetFromScale(thumbnail.getHeight(), bodyBubble.getScaleY()))
                       .translateY(getTranslationY())
                       .translateX(bodyBubble.getTranslationX())
                       .translateX(getTranslationX());
    } else {
      return Projection.relativeToParent(recyclerView, bodyBubble, bodyBubbleCorners)
                       .translateY(getTranslationY())
                       .translateX(bodyBubble.getTranslationX())
                       .translateX(getTranslationX());
    }
  }

  @Override
  public boolean canPlayContent() {
    return mediaThumbnailStub != null && mediaThumbnailStub.isResolvable() && canPlayContent;
  }

  @Override
  public boolean shouldProjectContent() {
    return canPlayContent() && bodyBubble.getVisibility() == VISIBLE;
  }

  @Override
  public @NonNull ProjectionList getColorizerProjections(@NonNull ViewGroup coordinateRoot) {
    return getSnapshotProjections(coordinateRoot, true, true);
  }

  @Override
  public @NonNull ProjectionList getSnapshotProjections(@NonNull ViewGroup coordinateRoot, boolean clipOutMedia) {
    return getSnapshotProjections(coordinateRoot, clipOutMedia, true);
  }

  @Override
  public @NonNull ProjectionList getSnapshotProjections(@NonNull ViewGroup coordinateRoot, boolean clipOutMedia, boolean outgoingOnly) {
    colorizerProjections.clear();

    if ((messageRecord.isOutgoing() || !outgoingOnly) &&
        !hasNoBubble(messageRecord) &&
        !messageRecord.isRemoteDelete() &&
        bodyBubbleCorners != null &&
        bodyBubble.getVisibility() == VISIBLE)
    {
      Projection bodyBubbleToRoot = Projection.relativeToParent(coordinateRoot, bodyBubble, bodyBubbleCorners).translateX(bodyBubble.getTranslationX());
      Projection videoToBubble    = bodyBubble.getVideoPlayerProjection();
      Projection mediaThumb       = clipOutMedia && mediaThumbnailStub.resolved() ? Projection.relativeToParent(coordinateRoot, mediaThumbnailStub.require(), null) : null;

      float translationX = Util.halfOffsetFromScale(bodyBubble.getWidth(), bodyBubble.getScaleX());
      float translationY = Util.halfOffsetFromScale(bodyBubble.getHeight(), bodyBubble.getScaleY());

      if (videoToBubble != null) {
        Projection videoToRoot = Projection.translateFromDescendantToParentCoords(videoToBubble, bodyBubble, coordinateRoot);

        List<Projection> projections = Projection.getCapAndTail(bodyBubbleToRoot, videoToRoot);
        if (!projections.isEmpty()) {
          projections.get(0)
                     .scale(bodyBubble.getScaleX())
                     .translateX(translationX)
                     .translateY(translationY);
          projections.get(1)
                     .scale(bodyBubble.getScaleX())
                     .translateX(translationX)
                     .translateY(-translationY);
        }

        colorizerProjections.addAll(projections);
      } else if (hasThumbnail(messageRecord) && mediaThumb != null) {
        if (hasQuote(messageRecord) && quoteView != null) {
          Projection quote        = Projection.relativeToParent(coordinateRoot, bodyBubble, bodyBubbleCorners).translateX(bodyBubble.getTranslationX());
          int        quoteViewTop = (int) quote.getY();
          int        mediaTop     = (int) mediaThumb.getY();

          colorizerProjections.add(
              quote.insetBottom(quote.getHeight() - (mediaTop - quoteViewTop))
                   .scale(bodyBubble.getScaleX())
                   .translateX(translationX)
                   .translateY(translationY)
          );
        }

        colorizerProjections.add(
            bodyBubbleToRoot.scale(bodyBubble.getScaleX())
                            .insetTop((int) (mediaThumb.getHeight() * bodyBubble.getScaleX()))
                            .translateX(translationX)
                            .translateY(translationY)
        );
      } else {
        colorizerProjections.add(
            bodyBubbleToRoot.scale(bodyBubble.getScaleX())
                            .translateX(translationX)
                            .translateY(translationY)
        );
      }

      if (mediaThumb != null) {
        mediaThumb.release();
      }
    }

    if ((messageRecord.isOutgoing() || !outgoingOnly) &&
        hasNoBubble(messageRecord) &&
        hasWallpaper &&
        bodyBubble.getVisibility() == VISIBLE)
    {
      ConversationItemFooter footer           = getActiveFooter(messageRecord);
      Projection             footerProjection = footer.getProjection(coordinateRoot);
      if (footerProjection != null) {
        colorizerProjections.add(
            footerProjection.translateX(bodyBubble.getTranslationX())
                            .scale(bodyBubble.getScaleX())
                            .translateX(Util.halfOffsetFromScale(footer.getWidth(), bodyBubble.getScaleX()))
                            .translateY(-Util.halfOffsetFromScale(footer.getHeight(), bodyBubble.getScaleY()))
        );
      }
    }

    for (int i = 0; i < colorizerProjections.size(); i++) {
      colorizerProjections.get(i).translateY(getTranslationY());
    }

    return colorizerProjections;
  }

  @Override
  public @Nullable View getHorizontalTranslationTarget() {
    if (messageRecord.isOutgoing()) {
      return null;
    } else if (groupThread) {
      return contactPhotoHolder;
    } else {
      return bodyBubble;
    }
  }

  @Override
  public @Nullable Projection getOpenableGiftProjection(boolean isAnimating) {
    if (!isGiftMessage(messageRecord) || messageRecord.isRemoteDelete() || (messageRecord.isViewed() && !isAnimating)) {
      return null;
    }

    return Projection.relativeToViewRoot(bodyBubble, bodyBubbleCorners)
                     .translateX(bodyBubble.getTranslationX())
                     .translateX(getTranslationX())
                     .scale(bodyBubble.getScaleX());
  }

  @Override
  public long getGiftId() {
    return messageRecord.getId();
  }

  @Override
  public void setOpenGiftCallback(@NonNull Function1<? super OpenableGift, Unit> openGift) {
    if (giftViewStub.resolved()) {
      bodyBubble.setOnClickListener(unused -> {
        openGift.invoke(this);
        eventListener.onGiftBadgeRevealed(messageRecord);
        bodyBubble.performHapticFeedback(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM
                                                                     : HapticFeedbackConstants.KEYBOARD_TAP);
      });
      giftViewStub.get().onGiftNotOpened();
    }
  }

  @Override
  public void clearOpenGiftCallback() {
    if (giftViewStub.resolved()) {
      bodyBubble.setOnClickListener(null);
      bodyBubble.setClickable(false);
      giftViewStub.get().onGiftOpened();
    }
  }

  @Override
  public @NonNull AnimationSign getAnimationSign() {
    return AnimationSign.get(ViewUtil.isLtr(this), messageRecord.isOutgoing());
  }

  @Override
  public @Nullable View getQuotedIndicatorView() {
    return quotedIndicator;
  }

  @Override
  public @NonNull View getReplyView() {
    return reply;
  }

  @Override
  public @Nullable View getContactPhotoHolderView() {
    return contactPhotoHolder;
  }

  @Override
  public @Nullable View getBadgeImageView() {
    return badgeImageView;
  }

  @NonNull @Override public List<View> getBubbleViews() {
    return Collections.singletonList(bodyBubble);
  }

  @Override
  public int getAdapterPosition(@NonNull RecyclerView recyclerView) {
    return recyclerView.getChildViewHolder(this).getBindingAdapterPosition();
  }

  @Override
  public @NonNull ViewGroup getRoot() {
    return this;
  }

  @Override
  public @NonNull View getBubbleView() {
    return bodyBubble;
  }

  @Override
  public void invalidateChatColorsDrawable(@NonNull ViewGroup coordinateRoot) {
    // Intentionally left blank.
  }

  @Override public @Nullable SnapshotStrategy getSnapshotStrategy() {
    return null;
  }

  private class PaymentTombstoneClickListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      if (eventListener != null) {
        eventListener.onPaymentTombstoneClicked();
      } else {
        passthroughClickListener.onClick(v);
      }
    }
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
        eventListener.onSharedContactDetailsClicked(((MmsMessageRecord) messageRecord).getSharedContacts().get(0), (View) sharedContactStub.get().getAvatarView().getParent());
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

  private class ViewOnceMessageClickListener implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      ViewOnceMessageView revealView = (ViewOnceMessageView) view;

      if (batchSelected.isEmpty() && messageRecord.isMms() && revealView.requiresTapToDownload((MmsMessageRecord) messageRecord)) {
        singleDownloadClickListener.onClick(view, ((MmsMessageRecord) messageRecord).getSlideDeck().getThumbnailSlide());
      } else if (eventListener != null && batchSelected.isEmpty() && messageRecord.isMms()) {
        eventListener.onViewOnceMessageClicked((MmsMessageRecord) messageRecord);
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

  private class QuotedIndicatorClickListener implements View.OnClickListener {
    public void onClick(final View view) {
      if (eventListener != null && batchSelected.isEmpty() && conversationMessage.hasBeenQuoted()) {
        eventListener.onQuotedIndicatorClicked((messageRecord));
      } else {
        passthroughClickListener.onClick(view);
      }
    }
  }

  private class ScheduledIndicatorClickListener implements View.OnClickListener {
    public void onClick(final View view) {
      if (eventListener != null && batchSelected.isEmpty()) {
        eventListener.onScheduledIndicatorClicked(view, (conversationMessage));
      } else {
        passthroughClickListener.onClick(view);
      }
    }
  }

  private class DoubleTapEditTouchListener implements View.OnTouchListener {
    @Override
    public boolean onTouch(View v, MotionEvent event) {
      if (gestureDetector != null && batchSelected.isEmpty()) {
        return gestureDetector.onTouchEvent(event);
      }
      return false;
    }
  }

  private class AttachmentDownloadClickListener implements SlidesClickedListener {
    @Override
    public void onClick(View v, final List<Slide> slides) {
      Log.i(TAG, "onClick() for attachment download");
      if (messageRecord.isMmsNotification()) {
        Log.w(TAG, "Ignoring MMS download.");
      } else {
        Log.i(TAG, "Scheduling push attachment downloads for " + slides.size() + " items");

        for (Slide slide : slides) {
          AttachmentDownloadJob.downloadAttachmentIfNeeded((DatabaseAttachment) slide.asAttachment());
        }
      }
    }
  }

  private class PlayVideoClickListener implements SlideClickListener {
    private static final float MINIMUM_DOWNLOADED_THRESHOLD = 0.05f;
    private              View  parentView;
    private              Slide activeSlide;

    @Override
    public void onClick(View v, Slide slide) {
      if (MediaUtil.isInstantVideoSupported(slide)) {
        final DatabaseAttachment databaseAttachment = (DatabaseAttachment) slide.asAttachment();
        String jobId = AttachmentDownloadJob.downloadAttachmentIfNeeded(databaseAttachment);
        if (jobId != null) {
          setup(v, slide);
          AppDependencies.getJobManager().addListener(jobId, (job, jobState) -> {
            if (jobState.isComplete()) {
              cleanup();
            }
          });
        } else {
          launchMediaPreview(v, slide);
          cleanup();
        }
      } else {
        Log.d(TAG, "Non-eligible slide clicked.");
      }
    }

    private void setup(View v, Slide slide) {
      parentView  = v;
      activeSlide = slide;
      if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    private void cleanup() {
      parentView  = null;
      activeSlide = null;
      if (EventBus.getDefault().isRegistered(this)) {
        EventBus.getDefault().unregister(this);
      }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventAsync(PartProgressEvent event) {
      final Slide currentActiveSlide = activeSlide;
      if (currentActiveSlide == null || !event.attachment.equals(currentActiveSlide.asAttachment())) {
        return;
      }

      final View  currentParentView  = parentView;
      float       progressPercent    = ((float) event.progress) / event.total;
      if (progressPercent >= MINIMUM_DOWNLOADED_THRESHOLD && currentParentView != null) {
        cleanup();
        launchMediaPreview(currentParentView, currentActiveSlide);
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
      } else if (eventListener != null && hasSticker(messageRecord)) {
        //noinspection ConstantConditions
        eventListener.onStickerClicked(((MmsMessageRecord) messageRecord).getSlideDeck().getStickerSlide().asAttachment().stickerLocator);
      }
    }
  }

  private class ThumbnailClickListener implements SlideClickListener {
    public void onClick(final View v, final Slide slide) {
      if (shouldInterceptClicks(messageRecord) || !batchSelected.isEmpty() || isCondensedMode()) {
        performClick();
      } else if (!canPlayContent && mediaItem != null && eventListener != null) {
        eventListener.onPlayInlineContent(conversationMessage);
      } else if (MediaPreviewV2Fragment.isContentTypeSupported(slide.getContentType()) && slide.getDisplayUri() != null) {
        AttachmentDownloadJob.downloadAttachmentIfNeeded((DatabaseAttachment) slide.asAttachment());
        launchMediaPreview(v, slide);
      } else if (slide.getUri() != null) {
        Log.i(TAG, "Clicked: " + slide.getUri() + " , " + slide.getContentType());
        Uri publicUri = PartAuthority.getAttachmentPublicUri(slide.getUri());
        Log.i(TAG, "Public URI: " + publicUri);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(PartAuthority.getAttachmentPublicUri(slide.getUri()), Intent.normalizeMimeType(slide.getContentType()));
        try {
          context.startActivity(intent);
        } catch (ActivityNotFoundException anfe) {
          Log.w(TAG, "No activity existed to view the media.");
          Toast.makeText(context, R.string.ConversationItem_unable_to_open_media, Toast.LENGTH_LONG).show();
        }
      } else if (slide.asAttachment().isPermanentlyFailed()) {
        String failedMessage;

        if (slide instanceof ImageSlide) {
          failedMessage = messageRecord.isOutgoing() ? context.getString(R.string.ConversationItem_cant_download_image_you_will_need_to_send_it_again)
                                                     : context.getString(R.string.ConversationItem_cant_download_image_s_will_need_to_send_it_again, messageRecord.getFromRecipient().getShortDisplayName(context));
        } else if (slide instanceof VideoSlide) {
          failedMessage = messageRecord.isOutgoing() ? context.getString(R.string.ConversationItem_cant_download_video_you_will_need_to_send_it_again)
                                                     : context.getString(R.string.ConversationItem_cant_download_video_s_will_need_to_send_it_again, messageRecord.getFromRecipient().getShortDisplayName(context));
        } else {
          failedMessage = messageRecord.isOutgoing() ? context.getString(R.string.ConversationItem_cant_download_message_you_will_need_to_send_it_again)
                                                     : context.getString(R.string.ConversationItem_cant_download_message_s_will_need_to_send_it_again, messageRecord.getFromRecipient().getShortDisplayName(context));
        }

        new MaterialAlertDialogBuilder(getContext())
            .setMessage(failedMessage)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show();
      }
    }
  }

  private void launchMediaPreview(View v, Slide slide) {
    if (eventListener == null) {
      Log.w(TAG, "Could not launch media preview for item: eventListener was null");
      return;
    }

    Uri mediaUri = slide.getDisplayUri();
    if (mediaUri == null) {
      Log.w(TAG, "Could not launch media preview for item: uri was null");
      return;
    }

    MediaIntentFactory.MediaPreviewArgs args = new MediaIntentFactory.MediaPreviewArgs(
        messageRecord.getThreadId(),
        messageRecord.getTimestamp(),
        mediaUri,
        slide.getContentType(),
        slide.asAttachment().size,
        slide.getCaption().orElse(null),
        false,
        false,
        false,
        false,
        MediaTable.Sorting.Newest,
        slide.isVideoGif(),
        new MediaIntentFactory.SharedElementArgs(
            slide.asAttachment().width,
            slide.asAttachment().height,
            mediaThumbnailStub.require().getCorners().getTopLeft(),
            mediaThumbnailStub.require().getCorners().getTopRight(),
            mediaThumbnailStub.require().getCorners().getBottomRight(),
            mediaThumbnailStub.require().getCorners().getBottomLeft()
        ),
        false);
    if (v instanceof ThumbnailView) {
      MediaPreviewCache.INSTANCE.setDrawable(((ThumbnailView) v).getImageDrawable());
    }
    eventListener.goToMediaPreview(ConversationItem.this, v, args);
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

  private class GiftMessageViewCallback implements GiftMessageView.Callback {

    @Override
    public void onViewGiftBadgeClicked() {
      eventListener.onViewGiftBadgeClicked(messageRecord);
    }
  }

  private class ClickListener implements View.OnClickListener {
    private final OnClickListener parent;

    ClickListener(@Nullable OnClickListener parent) {
      this.parent = parent;
    }

    public void onClick(View v) {
      if (!shouldInterceptClicks(messageRecord) && parent != null) {
        parent.onClick(v);
      } else if (messageRecord.isFailed()) {
        if (eventListener != null) {
          eventListener.onMessageWithErrorClicked(messageRecord);
        }
      } else if (messageRecord.isRateLimited() && SignalStore.rateLimit().needsRecaptcha()) {
        if (eventListener != null) {
          eventListener.onMessageWithRecaptchaNeededClicked(messageRecord);
        }
      } else if (!messageRecord.isOutgoing() && messageRecord.isIdentityMismatchFailure()) {
        if (eventListener != null) {
          eventListener.onIncomingIdentityMismatchClicked(messageRecord.getFromRecipient().getId());
        }
      }
    }
  }

  private final class TouchDelegateChangedListener implements ConversationItemFooter.OnTouchDelegateChangedListener {
    @Override
    public void onTouchDelegateChanged(@NonNull Rect delegateRect, @NonNull View delegateView) {
      offsetDescendantRectToMyCoords(footer, delegateRect);
      setTouchDelegate(new TouchDelegate(delegateRect, delegateView));
    }
  }

  private final class UrlClickListener implements UrlClickHandler {

    @Override
    public boolean handleOnClick(@NonNull String url) {
      return eventListener != null && eventListener.onUrlClicked(url);
    }
  }

  private class MentionClickableSpan extends ClickableSpan {
    private final RecipientId mentionedRecipientId;

    MentionClickableSpan(RecipientId mentionedRecipientId) {
      this.mentionedRecipientId = mentionedRecipientId;
    }

    @Override
    public void onClick(@NonNull View widget) {
      if (eventListener != null && batchSelected.isEmpty()) {
        VibrateUtil.vibrateTick(context);
        eventListener.onGroupMemberClicked(mentionedRecipientId, conversationRecipient.get().requireGroupId());
      }
    }

    @Override
    public void updateDrawState(@NonNull TextPaint ds) {}
  }

  private final class AudioPlaybackSpeedToggleListener implements PlaybackSpeedToggleTextView.PlaybackSpeedListener {
    @Override
    public void onPlaybackSpeedChanged(float speed) {
      if (eventListener == null || !audioViewStub.resolved()) {
        return;
      }

      Uri uri = audioViewStub.get().getAudioSlideUri();
      if (uri == null) {
        return;
      }

      eventListener.onVoiceNotePlaybackSpeedChanged(uri, speed);
    }
  }

  private final class AudioViewCallbacks implements AudioView.Callbacks {

    @Override
    public void onPlay(@NonNull Uri audioUri, double progress) {
      if (eventListener == null) return;

      eventListener.onVoiceNotePlay(audioUri, messageRecord.getId(), progress);
    }

    @Override
    public void onPause(@NonNull Uri audioUri) {
      if (eventListener == null) return;

      eventListener.onVoiceNotePause(audioUri);
    }

    @Override
    public void onSeekTo(@NonNull Uri audioUri, double progress) {
      if (eventListener == null) return;

      eventListener.onVoiceNoteSeekTo(audioUri, progress);
    }

    @Override
    public void onStopAndReset(@NonNull Uri audioUri) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void onSpeedChanged(float speed, boolean isPlaying) {
      footer.setAudioPlaybackSpeed(speed, isPlaying);
    }

    @Override
    public void onProgressUpdated(long durationMillis, long playheadMillis) {
      footer.setAudioDuration(durationMillis, playheadMillis);
    }
  }
}
