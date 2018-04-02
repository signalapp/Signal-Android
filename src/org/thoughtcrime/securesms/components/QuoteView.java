package org.thoughtcrime.securesms.components;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.annimon.stream.Stream;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.DocumentSlide;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.util.Util;

import java.util.List;

public class QuoteView extends LinearLayout implements RecipientModifiedListener {

  private static final String TAG = QuoteView.class.getSimpleName();

  private static final int MESSAGE_TYPE_PREVIEW  = 0;
  private static final int MESSAGE_TYPE_OUTGOING = 1;
  private static final int MESSAGE_TYPE_INCOMING = 2;

  private View      rootView;
  private TextView  authorView;
  private TextView  bodyView;
  private ImageView quoteBarView;
  private ImageView attachmentView;
  private ImageView attachmentVideoOverlayView;
  private ViewGroup attachmentIconContainerView;
  private ImageView attachmentIconView;
  private ImageView attachmentIconBackgroundView;
  private ImageView dismissView;

  private long      id;
  private Recipient author;
  private String    body;
  private TextView  mediaDescriptionText;
  private SlideDeck attachments;
  private int       messageType;
  private int       roundedCornerRadiusPx;

  private final Path  clipPath = new Path();
  private final RectF drawRect = new RectF();

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

    this.rootView                     = findViewById(R.id.quote_root);
    this.authorView                   = findViewById(R.id.quote_author);
    this.bodyView                     = findViewById(R.id.quote_text);
    this.quoteBarView                 = findViewById(R.id.quote_bar);
    this.attachmentView               = findViewById(R.id.quote_attachment);
    this.attachmentVideoOverlayView   = findViewById(R.id.quote_video_overlay);
    this.attachmentIconContainerView  = findViewById(R.id.quote_attachment_icon_container);
    this.attachmentIconView           = findViewById(R.id.quote_attachment_icon);
    this.attachmentIconBackgroundView = findViewById(R.id.quote_attachment_icon_background);
    this.dismissView                  = findViewById(R.id.quote_dismiss);
    this.mediaDescriptionText         = findViewById(R.id.media_name);
    this.roundedCornerRadiusPx        = getResources().getDimensionPixelSize(R.dimen.quote_corner_radius);

    if (attrs != null) {
      TypedArray typedArray  = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.QuoteView, 0, 0);
                 messageType = typedArray.getInt(R.styleable.QuoteView_message_type, 0);
      typedArray.recycle();

      dismissView.setVisibility(messageType == MESSAGE_TYPE_PREVIEW ? VISIBLE : GONE);
    }

    dismissView.setOnClickListener(view -> setVisibility(GONE));

    setWillNotDraw(false);
    if (Build.VERSION.SDK_INT < 18) {
      setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    drawRect.left = 0;
    drawRect.top = 0;
    drawRect.right = getWidth();
    drawRect.bottom = getHeight();

    clipPath.reset();
    clipPath.addRoundRect(drawRect, roundedCornerRadiusPx, roundedCornerRadiusPx, Path.Direction.CW);
    canvas.clipPath(clipPath);
  }

  public void setQuote(GlideRequests glideRequests, long id, @NonNull Recipient author, @Nullable String body, @NonNull SlideDeck attachments) {
    if (this.author != null) this.author.removeListener(this);

    this.id          = id;
    this.author      = author;
    this.body        = body;
    this.attachments = attachments;

    author.addListener(this);
    setQuoteAuthor(author);
    setQuoteText(body, attachments);
    setQuoteAttachment(glideRequests, attachments, author);
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
    authorView.setTextColor(author.getColor().toQuoteTitleColor(getContext()));
    // We use the raw color resource because Android 4.x was struggling with tints here
    quoteBarView.setImageResource(author.getColor().toQuoteBarColorResource(getContext(), outgoing));

    GradientDrawable background = (GradientDrawable) rootView.getBackground();
    background.setColor(author.getColor().toQuoteBackgroundColor(getContext(), outgoing));
    background.setStroke(getResources().getDimensionPixelSize(R.dimen.quote_outline_width),
                         author.getColor().toQuoteOutlineColor(getContext(), outgoing));
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
    mediaDescriptionText.setTypeface(null, Typeface.ITALIC);

    List<Slide> audioSlides    = Stream.of(attachments.getSlides()).filter(Slide::hasAudio).limit(1).toList();
    List<Slide> documentSlides = Stream.of(attachments.getSlides()).filter(Slide::hasDocument).limit(1).toList();
    List<Slide> imageSlides    = Stream.of(attachments.getSlides()).filter(Slide::hasImage).limit(1).toList();
    List<Slide> videoSlides    = Stream.of(attachments.getSlides()).filter(Slide::hasVideo).limit(1).toList();

    // Given that most types have images, we specifically check images last
    if (!audioSlides.isEmpty()) {
      mediaDescriptionText.setText(R.string.QuoteView_audio);
    } else if (!documentSlides.isEmpty()) {
      String filename = documentSlides.get(0).getFileName().orNull();
      if (!TextUtils.isEmpty(filename)) {
        mediaDescriptionText.setTypeface(null, Typeface.NORMAL);
        mediaDescriptionText.setText(filename);
      } else {
        mediaDescriptionText.setText(R.string.QuoteView_document);
      }
    } else if (!videoSlides.isEmpty()) {
      mediaDescriptionText.setText(R.string.QuoteView_video);
    } else if (!imageSlides.isEmpty()) {
      mediaDescriptionText.setText(R.string.QuoteView_photo);
    }
  }

  private void setQuoteAttachment(@NonNull GlideRequests glideRequests,
                                  @NonNull SlideDeck slideDeck,
                                  @NonNull Recipient author)
  {
    List<Slide> imageVideoSlides = Stream.of(slideDeck.getSlides()).filter(s -> s.hasImage() || s.hasVideo()).limit(1).toList();
    List<Slide> audioSlides = Stream.of(attachments.getSlides()).filter(Slide::hasAudio).limit(1).toList();
    List<Slide> documentSlides = Stream.of(attachments.getSlides()).filter(Slide::hasDocument).limit(1).toList();

    attachmentVideoOverlayView.setVisibility(GONE);

    if (!imageVideoSlides.isEmpty() && imageVideoSlides.get(0).getThumbnailUri() != null) {
      attachmentView.setVisibility(VISIBLE);
      attachmentIconContainerView.setVisibility(GONE);
      dismissView.setBackgroundResource(R.drawable.dismiss_background);
      if (imageVideoSlides.get(0).hasVideo()) {
        attachmentVideoOverlayView.setVisibility(VISIBLE);
      }
      glideRequests.load(new DecryptableUri(imageVideoSlides.get(0).getThumbnailUri()))
                   .centerCrop()
                   .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                   .into(attachmentView);
    } else if (!audioSlides.isEmpty() || !documentSlides.isEmpty()){
      boolean outgoing = messageType != MESSAGE_TYPE_INCOMING;

      dismissView.setBackgroundResource(R.drawable.circle_alpha);
      attachmentView.setVisibility(GONE);
      attachmentIconContainerView.setVisibility(VISIBLE);

      if (!audioSlides.isEmpty()) {
        attachmentIconView.setImageResource(R.drawable.ic_mic_white_48dp);
      } else {
        attachmentIconView.setImageResource(R.drawable.ic_insert_drive_file_white_24dp);
      }

      attachmentIconView.setColorFilter(author.getColor().toQuoteIconForegroundColor(getContext(), outgoing), PorterDuff.Mode.SRC_IN);
      attachmentIconBackgroundView.setColorFilter(author.getColor().toQuoteIconBackgroundColor(getContext(), outgoing), PorterDuff.Mode.SRC_IN);

    } else {
      attachmentView.setVisibility(GONE);
      attachmentIconContainerView.setVisibility(GONE);
      dismissView.setBackgroundDrawable(null);
    }
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
