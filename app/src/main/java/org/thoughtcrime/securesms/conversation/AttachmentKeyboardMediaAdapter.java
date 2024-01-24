package org.thoughtcrime.securesms.conversation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestManager;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.adapter.StableIdGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class AttachmentKeyboardMediaAdapter extends RecyclerView.Adapter<AttachmentKeyboardMediaAdapter.MediaViewHolder> {

  private final List<Media>              media;
  private final RequestManager           requestManager;
  private final Listener                 listener;
  private final StableIdGenerator<Media> idGenerator;

  AttachmentKeyboardMediaAdapter(@NonNull RequestManager requestManager, @NonNull Listener listener) {
    this.requestManager = requestManager;
    this.listener       = listener;
    this.media          = new ArrayList<>();
    this.idGenerator    = new StableIdGenerator<>();

    setHasStableIds(true);
  }

  @Override
  public long getItemId(int position) {
    return idGenerator.getId(media.get(position));
  }

  @Override
  public @NonNull MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new MediaViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.attachment_keyboad_media_item, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
    holder.bind(media.get(position), requestManager, listener);
  }

  @Override
  public void onViewRecycled(@NonNull MediaViewHolder holder) {
    holder.recycle();
  }

  @Override
  public int getItemCount() {
    return media.size();
  }

  public void setMedia(@NonNull List<Media> media) {
    this.media.clear();
    this.media.addAll(media);
    notifyDataSetChanged();
  }

  interface Listener {
    void onMediaClicked(@NonNull Media media);
  }

  static class MediaViewHolder extends RecyclerView.ViewHolder {

    private final ThumbnailView image;
    private final TextView      duration;
    private final View          videoIcon;

    public MediaViewHolder(@NonNull View itemView) {
      super(itemView);
      image     = itemView.findViewById(R.id.attachment_keyboard_item_image);
      duration  = itemView.findViewById(R.id.attachment_keyboard_item_video_time);
      videoIcon = itemView.findViewById(R.id.attachment_keyboard_item_video_icon);
    }

    void bind(@NonNull Media media, @NonNull RequestManager requestManager, @NonNull Listener listener) {
      image.setImageResource(requestManager, media.getUri(), 400, 400);
      image.setOnClickListener(v -> listener.onMediaClicked(media));

      duration.setVisibility(View.GONE);
      videoIcon.setVisibility(View.GONE);

      if (media.getDuration() > 0) {
        duration.setVisibility(View.VISIBLE);
        duration.setText(formatTime(media.getDuration()));
      } else if (MediaUtil.isVideoType(media.getMimeType())) {
        videoIcon.setVisibility(View.VISIBLE);
      }
    }

    void recycle() {
      image.setOnClickListener(null);
    }

    @NonNull static String formatTime(long time) {
      long hours   = TimeUnit.MILLISECONDS.toHours(time);
      time -= TimeUnit.HOURS.toMillis(hours);

      long minutes = TimeUnit.MILLISECONDS.toMinutes(time);
      time -= TimeUnit.MINUTES.toMillis(minutes);

      long seconds = TimeUnit.MILLISECONDS.toSeconds(time);

      if (hours > 0) {
        return zeroPad(hours) + ":" + zeroPad(minutes) + ":" + zeroPad(seconds);
      } else {
        return zeroPad(minutes) + ":" + zeroPad(seconds);
      }
    }

    @NonNull static String zeroPad(long value) {
      if (value < 10) {
        return "0" + value;
      } else {
        return String.valueOf(value);
      }
    }
  }
}
