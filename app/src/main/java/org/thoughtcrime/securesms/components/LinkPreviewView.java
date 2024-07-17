package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.bumptech.glide.RequestManager;

import org.signal.ringrtc.CallLinkRootKey;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatar;
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatarDrawable;
import org.thoughtcrime.securesms.calls.links.CallLinks;
import org.thoughtcrime.securesms.conversation.colors.AvatarColorHash;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.SlidesClickedListener;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.Stub;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import okhttp3.HttpUrl;

/**
 * The view shown in the compose box or conversation that represents the state of the link preview.
 */
public class LinkPreviewView extends FrameLayout {

  private static final String STATE_ROOT = "linkPreviewView.state.root";
  private static final String STATE_STATE = "linkPreviewView.state.state";

  private static final int TYPE_CONVERSATION = 0;
  private static final int TYPE_COMPOSE      = 1;

  private ViewGroup                   container;
  private Stub<OutlinedThumbnailView> thumbnail;
  private TextView                    title;
  private TextView                    description;
  private TextView                    site;
  private View                        divider;
  private View                        closeButton;
  private View                        spinner;
  private TextView                    noPreview;

  private int                           type;
  private int                           defaultRadius;
  private CornerMask                    cornerMask;
  private CloseClickedListener          closeClickedListener;
  private LinkPreviewViewThumbnailState thumbnailState = new LinkPreviewViewThumbnailState();

  public LinkPreviewView(Context context) {
    super(context);
    init(null);
  }

  public LinkPreviewView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  private void init(@Nullable AttributeSet attrs) {
    inflate(getContext(), R.layout.link_preview, this);

    container     = findViewById(R.id.linkpreview_container);
    thumbnail     = new Stub<>(findViewById(R.id.linkpreview_thumbnail));
    title         = findViewById(R.id.linkpreview_title);
    description   = findViewById(R.id.linkpreview_description);
    site          = findViewById(R.id.linkpreview_site);
    divider       = findViewById(R.id.linkpreview_divider);
    spinner       = findViewById(R.id.linkpreview_progress_wheel);
    closeButton   = findViewById(R.id.linkpreview_close);
    noPreview     = findViewById(R.id.linkpreview_no_preview);
    defaultRadius = getResources().getDimensionPixelSize(R.dimen.thumbnail_default_radius);
    cornerMask    = new CornerMask(this);

    if (attrs != null) {
      TypedArray typedArray   = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.LinkPreviewView, 0, 0);
      type = typedArray.getInt(R.styleable.LinkPreviewView_linkpreview_type, 0);
      typedArray.recycle();
    }

    if (type == TYPE_COMPOSE) {
      container.setBackgroundColor(Color.TRANSPARENT);
      container.setPadding(0, 0, 0, 0);
      divider.setVisibility(VISIBLE);
      closeButton.setVisibility(VISIBLE);
      title.setMaxLines(2);
      description.setMaxLines(2);

      closeButton.setOnClickListener(v -> {
        if (closeClickedListener != null) {
          closeClickedListener.onCloseClicked();
        }
      });
    }

    setWillNotDraw(false);
  }

  @Override
  protected @NonNull Parcelable onSaveInstanceState() {
    Parcelable root   = super.onSaveInstanceState();
    Bundle     bundle = new Bundle();

    bundle.putParcelable(STATE_ROOT, root);
    bundle.putParcelable(STATE_STATE, thumbnailState);

    return bundle;
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    if (state instanceof Bundle) {
      Parcelable root = ((Bundle) state).getParcelable(STATE_ROOT);
      thumbnailState = ((Bundle) state).getParcelable(STATE_STATE);

      thumbnailState.applyState(thumbnail);
      super.onRestoreInstanceState(root);
    } else {
      super.onRestoreInstanceState(state);
    }
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);
    if (type == TYPE_COMPOSE) return;

    cornerMask.mask(canvas);
  }

  public void setLoading() {
    title.setVisibility(GONE);
    site.setVisibility(GONE);
    description.setVisibility(GONE);
    thumbnail.setVisibility(GONE);
    spinner.setVisibility(VISIBLE);
    noPreview.setVisibility(INVISIBLE);
  }

  public void setNoPreview(@Nullable LinkPreviewRepository.Error customError) {
    title.setVisibility(GONE);
    site.setVisibility(GONE);
    thumbnail.setVisibility(GONE);
    spinner.setVisibility(GONE);
    noPreview.setVisibility(VISIBLE);
    noPreview.setText(getLinkPreviewErrorString(customError));
  }

  public void setLinkPreview(@NonNull RequestManager requestManager, @NonNull LinkPreview linkPreview, boolean showThumbnail) {
    setLinkPreview(requestManager, linkPreview, showThumbnail, true, false);
  }

  public void setLinkPreview(@NonNull RequestManager requestManager, @NonNull LinkPreview linkPreview, boolean showThumbnail, boolean showDescription, boolean scheduleMessageMode) {
    spinner.setVisibility(GONE);
    noPreview.setVisibility(GONE);

    CallLinkRootKey callLinkRootKey = CallLinks.parseUrl(linkPreview.getUrl());
    if (!Util.isEmpty(linkPreview.getTitle())) {
      title.setText(linkPreview.getTitle());
      title.setVisibility(VISIBLE);
    } else if (callLinkRootKey != null) {
      title.setText(R.string.Recipient_signal_call);
      title.setVisibility(VISIBLE);
    } else {
      title.setVisibility(GONE);
    }

    if (showDescription && !Util.isEmpty(linkPreview.getDescription())) {
      description.setText(linkPreview.getDescription());
      description.setVisibility(VISIBLE);
    } else if (callLinkRootKey != null) {
      description.setText(R.string.LinkPreviewView__use_this_link_to_join_a_signal_call);
      description.setVisibility(VISIBLE);
    } else {
      description.setVisibility(GONE);
    }

    String domain = null;

    if (!Util.isEmpty(linkPreview.getUrl())) {
      HttpUrl url = HttpUrl.parse(linkPreview.getUrl());
      if (url != null) {
        domain = url.topPrivateDomain();
      }
    }

    if (domain != null && linkPreview.getDate() > 0) {
      site.setText(getContext().getString(R.string.LinkPreviewView_domain_date, domain, formatDate(linkPreview.getDate())));
      site.setVisibility(VISIBLE);
    } else if (domain != null) {
      site.setText(domain);
      site.setVisibility(VISIBLE);
    } else if (linkPreview.getDate() > 0) {
      site.setText(formatDate(linkPreview.getDate()));
      site.setVisibility(VISIBLE);
    } else {
      site.setVisibility(GONE);
    }

    if (showThumbnail && linkPreview.getThumbnail().isPresent()) {
      thumbnail.setVisibility(VISIBLE);
      thumbnailState.applyState(thumbnail);
      thumbnail.get().setImageResource(requestManager, new ImageSlide(linkPreview.getThumbnail().get()), type == TYPE_CONVERSATION && !scheduleMessageMode, false);
      thumbnail.get().showSecondaryText(false);
      thumbnail.get().setOutlineEnabled(true);
    } else if (callLinkRootKey != null) {
      thumbnail.setVisibility(VISIBLE);
      thumbnailState.applyState(thumbnail);
      thumbnail.get().setImageDrawable(
          requestManager,
          new FallbackAvatarDrawable(
              getContext(),
              new FallbackAvatar.Resource.CallLink(AvatarColorHash.forCallLink(callLinkRootKey.getKeyBytes()))
          ).circleCrop()
      );
      thumbnail.get().showSecondaryText(false);
      thumbnail.get().setOutlineEnabled(false);
    } else {
      thumbnail.setVisibility(GONE);
    }
  }

  public void setCorners(int topStart, int topEnd) {
    if (ViewUtil.isRtl(this)) {
      cornerMask.setRadii(topEnd, topStart, 0, 0);
      thumbnailState = thumbnailState.copy(
          defaultRadius,
          topEnd,
          defaultRadius,
          defaultRadius,
          thumbnailState.getDownloadListener()
      );
      thumbnailState.applyState(thumbnail);
    } else {
      cornerMask.setRadii(topStart, topEnd, 0, 0);
      thumbnailState.copy(
          topStart,
          defaultRadius,
          defaultRadius,
          defaultRadius,
          thumbnailState.getDownloadListener()
      );
      thumbnailState.applyState(thumbnail);
    }
    postInvalidate();
  }

  public void setCloseClickedListener(@Nullable CloseClickedListener closeClickedListener) {
    this.closeClickedListener = closeClickedListener;
  }

  public void setDownloadClickedListener(SlidesClickedListener listener) {
    thumbnailState = thumbnailState.withDownloadListener(listener);
    thumbnailState.applyState(thumbnail);
  }

  private  @StringRes static int getLinkPreviewErrorString(@Nullable LinkPreviewRepository.Error customError) {
    return customError == LinkPreviewRepository.Error.GROUP_LINK_INACTIVE ? R.string.LinkPreviewView_this_group_link_is_not_active
                                                                          : R.string.LinkPreviewView_no_link_preview_available;
  }

  private static String formatDate(long date) {
    DateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    return dateFormat.format(date);
  }

  public interface CloseClickedListener {
    void onCloseClicked();
  }
}
