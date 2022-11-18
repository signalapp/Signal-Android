package org.thoughtcrime.securesms.giph.mp4;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import org.signal.paging.PagingController;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.giph.model.GiphyImage;

import java.util.Objects;

/**
 * Maintains and displays a list of GiphyImage objects. This Adapter always displays gifs
 * as MP4 videos.
 */
final class GiphyMp4Adapter extends ListAdapter<GiphyImage, GiphyMp4ViewHolder> {

  private final Callback                   listener;
  private final GiphyMp4MediaSourceFactory mediaSourceFactory;

  private PagingController pagingController;

  public GiphyMp4Adapter(@NonNull GiphyMp4MediaSourceFactory mediaSourceFactory, @Nullable Callback listener) {
    super(new GiphyImageDiffUtilCallback());

    this.listener           = listener;
    this.mediaSourceFactory = mediaSourceFactory;
  }

  @Override
  public @NonNull GiphyMp4ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View itemView = LayoutInflater.from(parent.getContext())
                                  .inflate(R.layout.giphy_mp4, parent, false);

    return new GiphyMp4ViewHolder(itemView, listener, mediaSourceFactory);
  }

  @Override
  public void onBindViewHolder(@NonNull GiphyMp4ViewHolder holder, int position) {
    holder.onBind(getItem(position));
  }

  @Override
  protected GiphyImage getItem(int position) {
    if (pagingController != null) {
      pagingController.onDataNeededAroundIndex(position);
    }

    return super.getItem(position);
  }

  void setPagingController(@Nullable PagingController pagingController) {
    this.pagingController = pagingController;
  }

  interface Callback {
    void onClick(@NonNull GiphyImage giphyImage);
  }

  private static final class GiphyImageDiffUtilCallback extends DiffUtil.ItemCallback<GiphyImage> {

    @Override
    public boolean areItemsTheSame(@NonNull GiphyImage oldItem, @NonNull GiphyImage newItem) {
      return Objects.equals(oldItem.getMp4Url(), newItem.getMp4Url());
    }

    @Override
    public boolean areContentsTheSame(@NonNull GiphyImage oldItem, @NonNull GiphyImage newItem) {
      return areItemsTheSame(oldItem, newItem);
    }
  }
}