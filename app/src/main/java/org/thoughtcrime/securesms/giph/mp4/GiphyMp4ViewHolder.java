package org.thoughtcrime.securesms.giph.mp4;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette;
import org.thoughtcrime.securesms.giph.model.ChunkedImageUrl;
import org.thoughtcrime.securesms.giph.model.GiphyImage;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.Projection;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * Holds a view which will either play back an MP4 gif or show its still.
 */
final class GiphyMp4ViewHolder extends RecyclerView.ViewHolder implements GiphyMp4Playable {

  private static final Projection.Corners CORNERS = new Projection.Corners(ViewUtil.dpToPx(8));

  private final AspectRatioFrameLayout     container;
  private final ImageView                  stillImage;
  private final GiphyMp4Adapter.Callback   listener;
  private final Drawable                   placeholder;
  private final GiphyMp4MediaSourceFactory mediaSourceFactory;

  private float            aspectRatio;
  private MediaSource      mediaSource;

  GiphyMp4ViewHolder(@NonNull View itemView,
                     @Nullable GiphyMp4Adapter.Callback listener,
                     @NonNull GiphyMp4MediaSourceFactory mediaSourceFactory)
  {
    super(itemView);
    this.container          = itemView.findViewById(R.id.container);
    this.listener           = listener;
    this.stillImage         = itemView.findViewById(R.id.still_image);
    this.placeholder        = new ColorDrawable(Util.getRandomElement(ChatColorsPalette.Names.getAll()).getColor(itemView.getContext()));
    this.mediaSourceFactory = mediaSourceFactory;

    container.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);
  }

  void onBind(@NonNull GiphyImage giphyImage) {
    aspectRatio = giphyImage.getGifAspectRatio();
    mediaSource = mediaSourceFactory.create(Uri.parse(giphyImage.getMp4PreviewUrl()));

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
  public @NonNull MediaSource getMediaSource() {
    return mediaSource;
  }

  @Override
  public @NonNull Projection getGiphyMp4PlayableProjection(@NonNull ViewGroup recyclerView) {
    return Projection.relativeToParent(recyclerView, container, CORNERS);
  }

  @Override
  public boolean canPlayContent() {
    return true;
  }

  private void loadPlaceholderImage(@NonNull GiphyImage giphyImage) {
    GlideApp.with(itemView)
            .load(new ChunkedImageUrl(giphyImage.getStillUrl()))
            .placeholder(placeholder)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(stillImage);
  }
}
