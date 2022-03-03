package org.thoughtcrime.securesms.components;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.components.mention.MentionAnnotation;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Projection;
import org.thoughtcrime.securesms.util.ThemeUtil;

import java.util.List;

public class QuoteView extends FrameLayout implements RecipientForeverObserver {

  private static final String TAG = Log.tag(QuoteView.class);

  public enum MessageType {
    // These codes must match the values for the QuoteView_message_type XML attribute.
    PREVIEW(0),
    OUTGOING(1),
    INCOMING(2),
    STORY_REPLY(3);

    private final int code;

    MessageType(int code) {
      this.code = code;
    }

    private static @NonNull MessageType fromCode(int code) {
      for (MessageType value : values()) {
        if (value.code == code) {
          return value;
        }
      }

      throw new IllegalArgumentException("Unsupported code " + code);
    }
  }

  private ViewGroup mainView;
  private ViewGroup footerView;
  private TextView  authorView;
  private TextView  bodyView;
  private View      quoteBarView;
  private ImageView thumbnailView;
  private View      attachmentVideoOverlayView;
  private ViewGroup attachmentContainerView;
  private TextView  attachmentNameView;
  private ImageView dismissView;

  private long          id;
  private LiveRecipient author;
  private CharSequence  body;
  private TextView      mediaDescriptionText;
  private TextView      missingLinkText;
  private SlideDeck     attachments;
  private MessageType   messageType;
  private int           largeCornerRadius;
  private int           smallCornerRadius;
  private CornerMask    cornerMask;

  private int thumbHeight;
  private int thumbWidth;

  public QuoteView(Context context) {
    super(context);
    initialize(null);
  }

  public QuoteView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(attrs);
  }

  public QuoteView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize(attrs);
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public QuoteView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize(attrs);
  }

  private void initialize(@Nullable AttributeSet attrs) {
    inflate(getContext(), R.layout.quote_view, this);

    this.mainView                     = findViewById(R.id.quote_main);
    this.footerView                   = findViewById(R.id.quote_missing_footer);
    this.authorView                   = findViewById(R.id.quote_author);
    this.bodyView                     = findViewById(R.id.quote_text);
    this.quoteBarView                 = findViewById(R.id.quote_bar);
    this.thumbnailView                = findViewById(R.id.quote_thumbnail);
    this.attachmentVideoOverlayView   = findViewById(R.id.quote_video_overlay);
    this.attachmentContainerView      = findViewById(R.id.quote_attachment_container);
    this.attachmentNameView           = findViewById(R.id.quote_attachment_name);
    this.dismissView                  = findViewById(R.id.quote_dismiss);
    this.mediaDescriptionText         = findViewById(R.id.media_type);
    this.missingLinkText              = findViewById(R.id.quote_missing_text);
    this.largeCornerRadius            = getResources().getDimensionPixelSize(R.dimen.quote_corner_radius_large);
    this.smallCornerRadius            = getResources().getDimensionPixelSize(R.dimen.quote_corner_radius_bottom);

    cornerMask = new CornerMask(this);

    if (attrs != null) {
      TypedArray typedArray     = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.QuoteView, 0, 0);
      int        primaryColor   = typedArray.getColor(R.styleable.QuoteView_quote_colorPrimary, Color.BLACK);
      int        secondaryColor = typedArray.getColor(R.styleable.QuoteView_quote_colorSecondary, Color.BLACK);
      messageType = MessageType.fromCode(typedArray.getInt(R.styleable.QuoteView_message_type, 0));
      typedArray.recycle();

      dismissView.setVisibility(messageType == MessageType.PREVIEW ? VISIBLE : GONE);

      authorView.setTextColor(primaryColor);
      bodyView.setTextColor(primaryColor);
      attachmentNameView.setTextColor(primaryColor);
      mediaDescriptionText.setTextColor(secondaryColor);
      missingLinkText.setTextColor(primaryColor);
    }

    setMessageType(messageType);

    dismissView.setOnClickListener(view -> setVisibility(GONE));
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);
    cornerMask.mask(canvas);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if (author != null) author.removeForeverObserver(this);
  }

  public void setMessageType(@NonNull MessageType messageType) {
    this.messageType = messageType;

    cornerMask.setRadii(largeCornerRadius, largeCornerRadius, smallCornerRadius, smallCornerRadius);
    thumbWidth = thumbHeight = getResources().getDimensionPixelSize(R.dimen.quote_thumb_size);

    if (messageType == MessageType.PREVIEW) {
      int radius = getResources().getDimensionPixelOffset(R.dimen.quote_corner_radius_preview);
      cornerMask.setTopLeftRadius(radius);
      cornerMask.setTopRightRadius(radius);
    } else if (messageType == MessageType.STORY_REPLY) {
      thumbWidth = getResources().getDimensionPixelOffset(R.dimen.quote_story_thumb_width);
      thumbHeight = getResources().getDimensionPixelOffset(R.dimen.quote_story_thumb_height);
    }

    mainView.setMinimumHeight(thumbHeight);

    ViewGroup.LayoutParams params = thumbnailView.getLayoutParams();
    params.height = thumbHeight;
    params.width = thumbWidth;

    thumbnailView.setLayoutParams(params);
  }

  public void setQuote(GlideRequests glideRequests,
                       long id,
                       @NonNull Recipient author,
                       @Nullable CharSequence body,
                       boolean originalMissing,
                       @NonNull SlideDeck attachments,
                       @Nullable ChatColors chatColors)
  {
    if (this.author != null) this.author.removeForeverObserver(this);

    this.id          = id;
    this.author      = author.live();
    this.body        = body;
    this.attachments = attachments;

    this.author.observeForever(this);
    setQuoteAuthor(author);
    setQuoteText(body, attachments);
    setQuoteAttachment(glideRequests, attachments);
    setQuoteMissingFooter(originalMissing);

    if (Build.VERSION.SDK_INT < 21 && messageType == MessageType.INCOMING && chatColors != null) {
      this.setBackgroundColor(chatColors.asSingleColor());
    } else {
      this.setBackground(null);
    }
  }

  public void setTopCornerSizes(boolean topLeftLarge, boolean topRightLarge) {
    cornerMask.setTopLeftRadius(topLeftLarge ? largeCornerRadius : smallCornerRadius);
    cornerMask.setTopRightRadius(topRightLarge ? largeCornerRadius : smallCornerRadius);
  }

  public void dismiss() {
    if (this.author != null) this.author.removeForeverObserver(this);

    this.id     = 0;
    this.author = null;
    this.body   = null;

    setVisibility(GONE);
  }

  @Override
  public void onRecipientChanged(@NonNull Recipient recipient) {
    setQuoteAuthor(recipient);
  }

  public @NonNull Projection getProjection(@NonNull ViewGroup parent) {
    return Projection.relativeToParent(parent, this, getCorners());
  }

  public @NonNull Projection.Corners getCorners() {
    return new Projection.Corners(cornerMask.getRadii());
  }

  private void setQuoteAuthor(@NonNull Recipient author) {
    boolean outgoing = messageType != MessageType.INCOMING;
    boolean preview  = messageType == MessageType.PREVIEW || messageType == MessageType.STORY_REPLY;

    if (messageType == MessageType.STORY_REPLY) {
      authorView.setText(author.isSelf() ? getContext().getString(R.string.QuoteView_your_story)
                                         : getContext().getString(R.string.QuoteView_s_story, author.getDisplayName(getContext())));
    } else {
      authorView.setText(author.isSelf() ? getContext().getString(R.string.QuoteView_you)
                                         : author.getDisplayName(getContext()));
    }

    quoteBarView.setBackgroundColor(ContextCompat.getColor(getContext(), outgoing ? R.color.core_white : android.R.color.transparent));
    mainView.setBackgroundColor(ContextCompat.getColor(getContext(), preview ? R.color.quote_preview_background : R.color.quote_view_background));
  }

  private void setQuoteText(@Nullable CharSequence body, @NonNull SlideDeck attachments) {
    if (!TextUtils.isEmpty(body) || !attachments.containsMediaSlide()) {
      bodyView.setVisibility(VISIBLE);
      bodyView.setText(body == null ? "" : body);
      mediaDescriptionText.setVisibility(GONE);
      return;
    }

    bodyView.setVisibility(GONE);
    mediaDescriptionText.setVisibility(VISIBLE);

    Slide audioSlide    = attachments.getSlides().stream().filter(Slide::hasAudio).findFirst().orElse(null);
    Slide documentSlide = attachments.getSlides().stream().filter(Slide::hasDocument).findFirst().orElse(null);
    Slide imageSlide    = attachments.getSlides().stream().filter(Slide::hasImage).findFirst().orElse(null);
    Slide videoSlide    = attachments.getSlides().stream().filter(Slide::hasVideo).findFirst().orElse(null);
    Slide stickerSlide  = attachments.getSlides().stream().filter(Slide::hasSticker).findFirst().orElse(null);
    Slide viewOnceSlide = attachments.getSlides().stream().filter(Slide::hasViewOnce).findFirst().orElse(null);

    // Given that most types have images, we specifically check images last
    if (viewOnceSlide != null) {
      mediaDescriptionText.setText(R.string.QuoteView_view_once_media);
    } else if (audioSlide != null) {
      mediaDescriptionText.setText(R.string.QuoteView_audio);
    } else if (documentSlide != null) {
      mediaDescriptionText.setVisibility(GONE);
    } else if (videoSlide != null) {
      if (videoSlide.isVideoGif()) {
        mediaDescriptionText.setText(R.string.QuoteView_gif);
      } else {
        mediaDescriptionText.setText(R.string.QuoteView_video);
      }
    } else if (stickerSlide != null) {
      mediaDescriptionText.setText(R.string.QuoteView_sticker);
    } else if (imageSlide != null) {
      if (MediaUtil.isGif(imageSlide.getContentType())) {
        mediaDescriptionText.setText(R.string.QuoteView_gif);
      } else {
        mediaDescriptionText.setText(R.string.QuoteView_photo);
      }
    }
  }

  private void setQuoteAttachment(@NonNull GlideRequests glideRequests, @NonNull SlideDeck slideDeck) {
    Slide imageVideoSlide = slideDeck.getSlides().stream().filter(s -> s.hasImage() || s.hasVideo() || s.hasSticker()).findFirst().orElse(null);
    Slide documentSlide   = slideDeck.getSlides().stream().filter(Slide::hasDocument).findFirst().orElse(null);
    Slide viewOnceSlide   = slideDeck.getSlides().stream().filter(Slide::hasViewOnce).findFirst().orElse(null);

    attachmentVideoOverlayView.setVisibility(GONE);

    if (viewOnceSlide != null) {
      thumbnailView.setVisibility(GONE);
      attachmentContainerView.setVisibility(GONE);
    } else if (imageVideoSlide != null && imageVideoSlide.getUri() != null) {
      thumbnailView.setVisibility(VISIBLE);
      attachmentContainerView.setVisibility(GONE);
      dismissView.setBackgroundResource(R.drawable.dismiss_background);
      if (imageVideoSlide.hasVideo() && !imageVideoSlide.isVideoGif()) {
        attachmentVideoOverlayView.setVisibility(VISIBLE);
      }
      glideRequests.load(new DecryptableUri(imageVideoSlide.getUri()))
                   .centerCrop()
                   .override(thumbWidth, thumbHeight)
                   .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                   .into(thumbnailView);
    } else if (documentSlide != null){
      thumbnailView.setVisibility(GONE);
      attachmentContainerView.setVisibility(VISIBLE);
      attachmentNameView.setText(documentSlide.getFileName().or(""));
    } else {
      thumbnailView.setVisibility(GONE);
      attachmentContainerView.setVisibility(GONE);
      dismissView.setBackgroundDrawable(null);
    }

    if (ThemeUtil.isDarkTheme(getContext())) {
      dismissView.setBackgroundResource(R.drawable.circle_alpha);
    }
  }

  private void setQuoteMissingFooter(boolean missing) {
    footerView.setVisibility(missing ? VISIBLE : GONE);
    footerView.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.quote_view_background));
  }

  public void setTextSize(int unit, float size) {
    bodyView.setTextSize(unit, size);
  }

  public long getQuoteId() {
    return id;
  }

  public Recipient getAuthor() {
    return author.get();
  }

  public CharSequence getBody() {
    return body;
  }

  public List<Attachment> getAttachments() {
    return attachments.asAttachments();
  }

  public @NonNull List<Mention> getMentions() {
    return MentionAnnotation.getMentionsFromAnnotations(body);
  }
}
