package org.thoughtcrime.securesms.components;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.annimon.stream.Stream;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.Util;

import java.util.List;

public class QuoteView extends FrameLayout implements RecipientModifiedListener {

  private static final String TAG = QuoteView.class.getSimpleName();

  private static final int MESSAGE_TYPE_PREVIEW  = 0;
  private static final int MESSAGE_TYPE_OUTGOING = 1;
  private static final int MESSAGE_TYPE_INCOMING = 2;

  private ViewGroup mainView;
  private ViewGroup footerView;
  private TextView  authorView;
  private TextView  bodyView;
  private ImageView quoteBarView;
  private ImageView thumbnailView;
  private View      attachmentVideoOverlayView;
  private ViewGroup attachmentContainerView;
  private TextView  attachmentNameView;
  private ImageView dismissView;

  private long       id;
  private Recipient  author;
  private String     body;
  private TextView   mediaDescriptionText;
  private TextView   missingLinkText;
  private SlideDeck  attachments;
  private int        messageType;
  private int        largeCornerRadius;
  private int        smallCornerRadius;
  private CornerMask cornerMask;


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
    cornerMask.setRadii(largeCornerRadius, largeCornerRadius, smallCornerRadius, smallCornerRadius);

    if (attrs != null) {
      TypedArray typedArray     = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.QuoteView, 0, 0);
      int        primaryColor   = typedArray.getColor(R.styleable.QuoteView_quote_colorPrimary, Color.BLACK);
      int        secondaryColor = typedArray.getColor(R.styleable.QuoteView_quote_colorSecondary, Color.BLACK);
      messageType = typedArray.getInt(R.styleable.QuoteView_message_type, 0);
      typedArray.recycle();

      dismissView.setVisibility(messageType == MESSAGE_TYPE_PREVIEW ? VISIBLE : GONE);

      authorView.setTextColor(primaryColor);
      bodyView.setTextColor(primaryColor);
      attachmentNameView.setTextColor(primaryColor);
      mediaDescriptionText.setTextColor(secondaryColor);
      missingLinkText.setTextColor(primaryColor);

      if (messageType == MESSAGE_TYPE_PREVIEW) {
        int radius = getResources().getDimensionPixelOffset(R.dimen.quote_corner_radius_preview);
        cornerMask.setTopLeftRadius(radius);
        cornerMask.setTopRightRadius(radius);
      }
    }

    dismissView.setOnClickListener(view -> setVisibility(GONE));
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);
    cornerMask.mask(canvas);
  }

  public void setQuote(GlideRequests glideRequests,
                       long id,
                       @NonNull Recipient author,
                       @Nullable String body,
                       boolean originalMissing,
                       @NonNull SlideDeck attachments)
  {
    if (this.author != null) this.author.removeListener(this);

    this.id          = id;
    this.author      = author;
    this.body        = body;
    this.attachments = attachments;

    author.addListener(this);
    setQuoteAuthor(author);
    setQuoteText(body, attachments);
    setQuoteAttachment(glideRequests, attachments);
    setQuoteMissingFooter(originalMissing);
  }

  public void setTopCornerSizes(boolean topLeftLarge, boolean topRightLarge) {
    cornerMask.setTopLeftRadius(topLeftLarge ? largeCornerRadius : smallCornerRadius);
    cornerMask.setTopRightRadius(topRightLarge ? largeCornerRadius : smallCornerRadius);
  }

  public void dismiss() {
    if (this.author != null) this.author.removeListener(this);

    this.id     = 0;
    this.author = null;
    this.body   = null;

    setVisibility(GONE);
  }

  @Override
  public void onModified(Recipient recipient) {
    Util.runOnMain(() -> {
      if (recipient == author) {
        setQuoteAuthor(recipient);
      }
    });
  }

  private void setQuoteAuthor(@NonNull Recipient author) {
    boolean outgoing    = messageType != MESSAGE_TYPE_INCOMING;
    boolean isOwnNumber = Util.isOwnNumber(getContext(), author.getAddress());

    authorView.setText(isOwnNumber ? getContext().getString(R.string.QuoteView_you)
                                   : author.toShortString());

    // We use the raw color resource because Android 4.x was struggling with tints here
    quoteBarView.setImageResource(author.getColor().toQuoteBarColorResource(getContext(), outgoing));
    mainView.setBackgroundColor(author.getColor().toQuoteBackgroundColor(getContext(), outgoing));
  }

  private void setQuoteText(@Nullable String body, @NonNull SlideDeck attachments) {
    if (!TextUtils.isEmpty(body) || !attachments.containsMediaSlide()) {
      bodyView.setVisibility(VISIBLE);
      bodyView.setText(body == null ? "" : body);
      mediaDescriptionText.setVisibility(GONE);
      return;
    }

    bodyView.setVisibility(GONE);
    mediaDescriptionText.setVisibility(VISIBLE);

    List<Slide> audioSlides    = Stream.of(attachments.getSlides()).filter(Slide::hasAudio).limit(1).toList();
    List<Slide> documentSlides = Stream.of(attachments.getSlides()).filter(Slide::hasDocument).limit(1).toList();
    List<Slide> imageSlides    = Stream.of(attachments.getSlides()).filter(Slide::hasImage).limit(1).toList();
    List<Slide> videoSlides    = Stream.of(attachments.getSlides()).filter(Slide::hasVideo).limit(1).toList();
    List<Slide> stickerSlides  = Stream.of(attachments.getSlides()).filter(Slide::hasSticker).limit(1).toList();

    // Given that most types have images, we specifically check images last
    if (!audioSlides.isEmpty()) {
      mediaDescriptionText.setText(R.string.QuoteView_audio);
    } else if (!documentSlides.isEmpty()) {
      mediaDescriptionText.setVisibility(GONE);
    } else if (!videoSlides.isEmpty()) {
      mediaDescriptionText.setText(R.string.QuoteView_video);
    } else if (!stickerSlides.isEmpty()) {
      mediaDescriptionText.setText(R.string.QuoteView_sticker);
    } else if (!imageSlides.isEmpty()) {
      mediaDescriptionText.setText(R.string.QuoteView_photo);
    }
  }

  private void setQuoteAttachment(@NonNull GlideRequests glideRequests, @NonNull SlideDeck slideDeck) {
    List<Slide> imageVideoSlides = Stream.of(slideDeck.getSlides()).filter(s -> s.hasImage() || s.hasVideo() || s.hasSticker()).limit(1).toList();
    List<Slide> documentSlides   = Stream.of(attachments.getSlides()).filter(Slide::hasDocument).limit(1).toList();

    attachmentVideoOverlayView.setVisibility(GONE);

    if (!imageVideoSlides.isEmpty() && imageVideoSlides.get(0).getThumbnailUri() != null) {
      thumbnailView.setVisibility(VISIBLE);
      attachmentContainerView.setVisibility(GONE);
      dismissView.setBackgroundResource(R.drawable.dismiss_background);
      if (imageVideoSlides.get(0).hasVideo()) {
        attachmentVideoOverlayView.setVisibility(VISIBLE);
      }
      glideRequests.load(new DecryptableUri(imageVideoSlides.get(0).getThumbnailUri()))
                   .centerCrop()
                   .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                   .into(thumbnailView);
    } else if (!documentSlides.isEmpty()){
      thumbnailView.setVisibility(GONE);
      attachmentContainerView.setVisibility(VISIBLE);
      attachmentNameView.setText(documentSlides.get(0).getFileName().or(""));
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
    footerView.setBackgroundColor(author.getColor().toQuoteFooterColor(getContext(), messageType != MESSAGE_TYPE_INCOMING));
  }

  public long getQuoteId() {
    return id;
  }

  public Recipient getAuthor() {
    return author;
  }

  public String getBody() {
    return body;
  }

  public List<Attachment> getAttachments() {
    return attachments.asAttachments();
  }
}
