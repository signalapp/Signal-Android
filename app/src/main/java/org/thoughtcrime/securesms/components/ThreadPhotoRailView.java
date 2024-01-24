package org.thoughtcrime.securesms.components;


import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestManager;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.MediaTable;
import org.thoughtcrime.securesms.mediapreview.MediaPreviewCache;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.util.ArrayList;
import java.util.List;

public class ThreadPhotoRailView extends FrameLayout {

  @NonNull  private final RecyclerView          recyclerView;
  @Nullable private       OnItemClickedListener listener;

  public ThreadPhotoRailView(Context context) {
    this(context, null);
  }

  public ThreadPhotoRailView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ThreadPhotoRailView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    inflate(context, R.layout.recipient_preference_photo_rail, this);

    this.recyclerView = findViewById(R.id.photo_list);
    this.recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
    this.recyclerView.setItemAnimator(new DefaultItemAnimator());
    this.recyclerView.setNestedScrollingEnabled(false);
  }

  public void setListener(@Nullable OnItemClickedListener listener) {
    this.listener = listener;

    if (this.recyclerView.getAdapter() != null) {
      ((ThreadPhotoRailAdapter)this.recyclerView.getAdapter()).setListener(listener);
    }
  }

  public void setMediaRecords(@NonNull RequestManager requestManager, @NonNull List<MediaTable.MediaRecord> mediaRecords) {
    this.recyclerView.setAdapter(new ThreadPhotoRailAdapter(getContext(), requestManager, mediaRecords, this.listener));
  }

  private static class ThreadPhotoRailAdapter extends RecyclerView.Adapter<ThreadPhotoRailAdapter.ThreadPhotoViewHolder> {

    @SuppressWarnings("unused")
    private static final String TAG = Log.tag(ThreadPhotoRailAdapter.class);

    @NonNull  private final RequestManager requestManager;

    @Nullable private OnItemClickedListener clickedListener;

    private final List<MediaTable.MediaRecord> mediaRecords = new ArrayList<>();

    private ThreadPhotoRailAdapter(@NonNull Context context,
                                   @NonNull RequestManager requestManager,
                                   @NonNull List<MediaTable.MediaRecord> mediaRecords,
                                   @Nullable OnItemClickedListener listener)
    {
      this.requestManager  = requestManager;
      this.clickedListener = listener;

      this.mediaRecords.clear();
      this.mediaRecords.addAll(mediaRecords);
    }

    @Override
    public int getItemCount() {
      return mediaRecords.size();
    }

    @Override
    public @NonNull ThreadPhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View itemView = LayoutInflater.from(parent.getContext())
                                    .inflate(R.layout.recipient_preference_photo_rail_item, parent, false);

      return new ThreadPhotoViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ThreadPhotoViewHolder viewHolder, int position) {
      MediaTable.MediaRecord mediaRecord = mediaRecords.get(position);
      Slide                  slide       = MediaUtil.getSlideForAttachment(mediaRecord.getAttachment());

      viewHolder.imageView.setImageResource(requestManager, slide, false, false);
      viewHolder.imageView.setOnClickListener(v -> {
        MediaPreviewCache.INSTANCE.setDrawable(viewHolder.imageView.getImageDrawable());
        if (clickedListener != null) clickedListener.onItemClicked(viewHolder.imageView, mediaRecord);
      });
    }

    public void setListener(@Nullable OnItemClickedListener listener) {
      this.clickedListener = listener;
    }

    static class ThreadPhotoViewHolder extends RecyclerView.ViewHolder {

      ThumbnailView imageView;

      ThreadPhotoViewHolder(View itemView) {
        super(itemView);

        this.imageView = itemView.findViewById(R.id.thumbnail);
      }
    }
  }

  public interface OnItemClickedListener {
    void onItemClicked(View itemView, MediaTable.MediaRecord mediaRecord);
  }
}
