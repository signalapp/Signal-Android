package org.thoughtcrime.securesms.components;


import android.content.Context;
import android.database.Cursor;
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

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter;
import org.thoughtcrime.securesms.database.MediaDatabase;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.util.MediaUtil;

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

  public void setCursor(@NonNull GlideRequests glideRequests, @Nullable Cursor cursor) {
    this.recyclerView.setAdapter(new ThreadPhotoRailAdapter(getContext(), glideRequests, cursor, this.listener));
  }

  private static class ThreadPhotoRailAdapter extends CursorRecyclerViewAdapter<ThreadPhotoRailAdapter.ThreadPhotoViewHolder> {

    @SuppressWarnings("unused")
    private static final String TAG = ThreadPhotoRailAdapter.class.getSimpleName();

    @NonNull  private final GlideRequests glideRequests;

    @Nullable private OnItemClickedListener clickedListener;

    private ThreadPhotoRailAdapter(@NonNull Context context,
                                   @NonNull GlideRequests glideRequests,
                                   @Nullable Cursor cursor,
                                   @Nullable OnItemClickedListener listener)
    {
      super(context, cursor);
      this.glideRequests   = glideRequests;
      this.clickedListener = listener;
    }

    @Override
    public ThreadPhotoViewHolder onCreateItemViewHolder(ViewGroup parent, int viewType) {
      View itemView = LayoutInflater.from(parent.getContext())
                                    .inflate(R.layout.recipient_preference_photo_rail_item, parent, false);

      return new ThreadPhotoViewHolder(itemView);
    }

    @Override
    public void onBindItemViewHolder(ThreadPhotoViewHolder viewHolder, @NonNull Cursor cursor) {
      ThumbnailView             imageView   = viewHolder.imageView;
      MediaDatabase.MediaRecord mediaRecord = MediaDatabase.MediaRecord.from(getContext(), cursor);
      Slide                     slide       = MediaUtil.getSlideForAttachment(getContext(), mediaRecord.getAttachment());

      if (slide != null) {
        imageView.setImageResource(glideRequests, slide, false, false);
      }

      imageView.setOnClickListener(v -> {
        if (clickedListener != null) clickedListener.onItemClicked(mediaRecord);
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
    void onItemClicked(MediaDatabase.MediaRecord mediaRecord);
  }
}
