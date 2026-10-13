package org.thoughtcrime.securesms.giph.mp4;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.AspectRatioFrameLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette;
import org.thoughtcrime.securesms.giph.model.ChunkedImageUrl;
import org.thoughtcrime.securesms.giph.model.GiphyImage;
import org.signal.core.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.video.inline.InlineVideoCell;
import org.thoughtcrime.securesms.video.inline.InlineVideoHost;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder;

/**
 * Holds a view which will either play back an MP4 gif or show its still.
 */
@OptIn(markerClass = UnstableApi.class)
final class GiphyMp4ViewHolder extends MappingViewHolder<GiphyImage> implements InlineVideoCell {

  private final AspectRatioFrameLayout   container;
  private final InlineVideoHost          surfaceHost;
  private final ImageView                stillImage;
  private final GiphyMp4Adapter.Callback listener;
  private final Drawable                 placeholder;

  private float     aspectRatio;
  private MediaItem mediaItem;

  GiphyMp4ViewHolder(@NonNull View itemView,
                     @Nullable GiphyMp4Adapter.Callback listener)
  {
    super(itemView);
    this.container          = itemView.findViewById(R.id.container);
    this.surfaceHost        = itemView.findViewById(R.id.surface_host);
    this.listener           = listener;
    this.stillImage         = itemView.findViewById(R.id.still_image);
    this.placeholder        = new ColorDrawable(Util.getRandomElement(ChatColorsPalette.Names.getAll()).getColor(itemView.getContext()));

    container.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);
    surfaceHost.setCornerRadius(ViewUtil.dpToPx(8));
  }

  @Override
  public void bind(@NonNull GiphyImage giphyImage) {
    aspectRatio = giphyImage.getGifAspectRatio();
    mediaItem   = MediaItem.fromUri(Uri.parse(giphyImage.getMp4PreviewUrl()));

    container.setAspectRatio(aspectRatio);
    stillImage.setAlpha(1f);

    loadPlaceholderImage(giphyImage);

    itemView.setOnClickListener(v -> listener.onClick(giphyImage));
  }

  @Override
  public @NonNull InlineVideoHost getSurfaceHost() {
    return surfaceHost;
  }

  @Override
  public void showStill() {
    stillImage.setAlpha(1f);
  }

  @Override
  public void hideStill() {
    stillImage.setAlpha(0f);
  }

  @Override
  public @NonNull MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  public boolean canPlayContent() {
    return true;
  }

  private void loadPlaceholderImage(@NonNull GiphyImage giphyImage) {
    Glide.with(itemView)
            .load(new ChunkedImageUrl(giphyImage.getStillUrl()))
            .placeholder(placeholder)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transition(DrawableTransitionOptions.withCrossFade())
            .centerCrop()
            .into(stillImage);
  }
}
