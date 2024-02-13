package org.thoughtcrime.securesms.giph.mp4;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
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
import org.thoughtcrime.securesms.util.Projection;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder;

/**
 * Holds a view which will either play back an MP4 gif or show its still.
 */
@OptIn(markerClass = UnstableApi.class)
final class GiphyMp4ViewHolder extends MappingViewHolder<GiphyImage> implements GiphyMp4Playable {

  private static final Projection.Corners CORNERS = new Projection.Corners(ViewUtil.dpToPx(8));

  private final AspectRatioFrameLayout   container;
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
    this.listener           = listener;
    this.stillImage         = itemView.findViewById(R.id.still_image);
    this.placeholder        = new ColorDrawable(Util.getRandomElement(ChatColorsPalette.Names.getAll()).getColor(itemView.getContext()));

    container.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);
  }

  @Override
  public void bind(@NonNull GiphyImage giphyImage) {
    aspectRatio = giphyImage.getGifAspectRatio();
    mediaItem   = MediaItem.fromUri(Uri.parse(giphyImage.getMp4PreviewUrl()));

    container.setAspectRatio(aspectRatio);

    loadPlaceholderImage(giphyImage);

    itemView.setOnClickListener(v -> listener.onClick(giphyImage));
  }

  @Override
  public void showProjectionArea() {
    container.setAlpha(1f);
  }

  @Override
  public void hideProjectionArea() {
    container.setAlpha(0f);
  }

  @Override
  public @NonNull MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  public @NonNull Projection getGiphyMp4PlayableProjection(@NonNull ViewGroup recyclerView) {
    return Projection.relativeToParent(recyclerView, container, CORNERS);
  }

  @Override
  public boolean canPlayContent() {
    return true;
  }

  @Override
  public boolean shouldProjectContent() {
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
