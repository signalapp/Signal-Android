package org.thoughtcrime.securesms.giph.ui;


import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.bumptech.glide.DrawableRequestBuilder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.giph.model.GiphyImage;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;


public class GiphyAdapter extends RecyclerView.Adapter<GiphyAdapter.GiphyViewHolder> {

  private static final String TAG = GiphyAdapter.class.getSimpleName();

  private List<GiphyImage>     images;
  private Context              context;
  private OnItemClickListener  listener;

  class GiphyViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, RequestListener<String, GlideDrawable> {

    public AspectRatioImageView thumbnail;
    public GiphyImage           image;
    public ProgressBar          gifProgress;
    public volatile boolean     modelReady;

    GiphyViewHolder(View view) {
      super(view);
      thumbnail   = ViewUtil.findById(view, R.id.thumbnail);
      gifProgress = ViewUtil.findById(view, R.id.gif_progress);
      thumbnail.setOnClickListener(this);
      gifProgress.setVisibility(View.GONE);
    }

    @Override
    public void onClick(View v) {
      if (listener != null) listener.onClick(this);
    }

    @Override
    public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
      Log.w(TAG, e);

      synchronized (this) {
        if (image.getGifUrl().equals(model)) {
          this.modelReady = true;
          notifyAll();
        }
      }

      return false;
    }

    @Override
    public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
      synchronized (this) {
        if (image.getGifUrl().equals(model)) {
          this.modelReady = true;
          notifyAll();
        }
      }

      return false;
    }

    public File getFile(boolean forMms) throws ExecutionException, InterruptedException {
      synchronized (this) {
        while (!modelReady) {
          Util.wait(this, 0);
        }
      }

      return Glide.with(context)
                  .load(forMms ? image.getGifMmsUrl() : image.getGifUrl())
                  .downloadOnly(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                  .get();
    }

    public synchronized void setModelReady() {
      this.modelReady = true;
      notifyAll();
    }
  }

  GiphyAdapter(Context context, List<GiphyImage> images) {
    this.context = context;
    this.images  = images;
  }

  public void setImages(@NonNull List<GiphyImage> images) {
    this.images = images;
    notifyDataSetChanged();
  }

  public void addImages(List<GiphyImage> images) {
    this.images.addAll(images);
    notifyDataSetChanged();
  }

  @Override
  public GiphyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View itemView = LayoutInflater.from(parent.getContext())
                                  .inflate(R.layout.giphy_thumbnail, parent, false);

    return new GiphyViewHolder(itemView);
  }

  @Override
  public void onBindViewHolder(GiphyViewHolder holder, int position) {
    GiphyImage image = images.get(position);

    holder.modelReady = false;
    holder.image      = image;
    holder.thumbnail.setAspectRatio(image.getGifAspectRatio());
    holder.gifProgress.setVisibility(View.GONE);

    DrawableRequestBuilder<String> thumbnailRequest = Glide.with(context)
                                                           .load(image.getStillUrl());

    if (Util.isLowMemory(context)) {
      Glide.with(context)
           .load(image.getStillUrl())
           .placeholder(new ColorDrawable(Util.getRandomElement(MaterialColor.values()).toConversationColor(context)))
           .diskCacheStrategy(DiskCacheStrategy.ALL)
           .into(holder.thumbnail);

      holder.setModelReady();
    } else {
      Glide.with(context)
           .load(image.getGifUrl())
           .thumbnail(thumbnailRequest)
           .placeholder(new ColorDrawable(Util.getRandomElement(MaterialColor.values()).toConversationColor(context)))
           .diskCacheStrategy(DiskCacheStrategy.ALL)
           .listener(holder)
           .into(holder.thumbnail);
    }
  }

  @Override
  public void onViewRecycled(GiphyViewHolder holder) {
    super.onViewRecycled(holder);
    Glide.clear(holder.thumbnail);
  }

  @Override
  public int getItemCount() {
    return images.size();
  }

  public void setListener(OnItemClickListener listener) {
    this.listener = listener;
  }

  public interface OnItemClickListener {
    void onClick(GiphyViewHolder viewHolder);
  }
}