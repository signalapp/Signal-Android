package org.thoughtcrime.securesms.components;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.annimon.stream.Stream;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.util.Util;

import java.util.List;

public class QuoteView extends LinearLayout implements RecipientModifiedListener {

  private static final String TAG = QuoteView.class.getSimpleName();

  private TextView  authorView;
  private TextView  bodyView;
  private ImageView quoteBarView;
  private ImageView attachmentView;
  private ImageView dismissView;

  private long      id;
  private Recipient author;
  private String    body;
  private View      mediaDescription;
  private ImageView mediaDescriptionIcon;
  private TextView  mediaDescriptionText;
  private SlideDeck attachments;

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

    this.authorView           = findViewById(R.id.quote_author);
    this.bodyView             = findViewById(R.id.quote_text);
    this.quoteBarView         = findViewById(R.id.quote_bar);
    this.attachmentView       = findViewById(R.id.quote_attachment);
    this.dismissView          = findViewById(R.id.quote_dismiss);
    this.mediaDescriptionIcon = findViewById(R.id.media_icon);
    this.mediaDescriptionText = findViewById(R.id.media_name);
    this.mediaDescription     = findViewById(R.id.media_description);

    if (attrs != null) {
      TypedArray typedArray  = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.QuoteView, 0, 0);
      boolean    dismissable = typedArray.getBoolean(R.styleable.QuoteView_quote_dismissable, true);
      typedArray.recycle();

      if (!dismissable) dismissView.setVisibility(View.GONE);
      else              dismissView.setVisibility(View.VISIBLE);
    }

    dismissView.setOnClickListener(view -> setVisibility(View.GONE));

    setBackgroundDrawable(getContext().getResources().getDrawable(R.drawable.quote_background));
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
    setQuoteAttachment(glideRequests, attachments);
  }

  public void dismiss() {
    if (this.author != null) this.author.removeListener(this);

    this.id     = 0;
    this.author = null;
    this.body   = null;

    setVisibility(View.GONE);
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
    this.authorView.setText(author.toShortString());
    this.authorView.setTextColor(author.getColor().toActionBarColor(getContext()));
    this.quoteBarView.setColorFilter(author.getColor().toActionBarColor(getContext()), PorterDuff.Mode.SRC_IN);
  }

  private void setQuoteText(@Nullable String body, @NonNull SlideDeck attachments) {
    if (TextUtils.isEmpty(body) && attachments.containsMediaSlide()) {
      mediaDescription.setVisibility(View.VISIBLE);
      bodyView.setVisibility(View.GONE);

      List<Slide> audioSlides    = Stream.of(attachments.getSlides()).filter(Slide::hasAudio).limit(1).toList();
      List<Slide> documentSlides = Stream.of(attachments.getSlides()).filter(Slide::hasDocument).limit(1).toList();
      List<Slide> imageSlides    = Stream.of(attachments.getSlides()).filter(Slide::hasImage).limit(1).toList();
      List<Slide> videoSlides    = Stream.of(attachments.getSlides()).filter(Slide::hasVideo).limit(1).toList();

      if (!audioSlides.isEmpty()) {
        mediaDescriptionIcon.setImageResource(R.drawable.ic_mic_white_24dp);
        mediaDescriptionText.setText("Audio");
      } else if (!documentSlides.isEmpty()) {
        mediaDescriptionIcon.setImageResource(R.drawable.ic_insert_drive_file_white_24dp);
        mediaDescriptionText.setText(String.format("%s (%s)", documentSlides.get(0).getFileName(), Util.getPrettyFileSize(documentSlides.get(0).getFileSize())));
      } else if (!videoSlides.isEmpty()) {
        mediaDescriptionIcon.setImageResource(R.drawable.ic_videocam_white_24dp);
        mediaDescriptionText.setText("Video");
      } else if (!imageSlides.isEmpty()) {
        mediaDescriptionIcon.setImageResource(R.drawable.ic_camera_alt_white_24dp);
        mediaDescriptionText.setText("Photo");
      }
    } else {
      mediaDescription.setVisibility(View.GONE);
      bodyView.setVisibility(View.VISIBLE);

      bodyView.setText(body == null ? "" : body);
    }
  }

  private void setQuoteAttachment(@NonNull GlideRequests glideRequests, @NonNull SlideDeck slideDeck) {
    List<Slide> imageVideoSlides = Stream.of(slideDeck.getSlides()).filter(s -> s.hasImage() || s.hasVideo()).limit(1).toList();

    if (!imageVideoSlides.isEmpty() && imageVideoSlides.get(0).getThumbnailUri() != null) {
      attachmentView.setVisibility(View.VISIBLE);
      dismissView.setBackgroundResource(R.drawable.circle_alpha);
      glideRequests.load(new DecryptableUri(imageVideoSlides.get(0).getThumbnailUri()))
                   .centerCrop()
                   .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                   .into(attachmentView);
    } else {
      attachmentView.setVisibility(View.GONE);
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
