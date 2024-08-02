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

class AttachmentKeyboardMediaAdapter extends RecyclerView.Adapter<AttachmentKeyboardMediaAdapter.ViewHolder> {

  private static final int VIEW_TYPE_MEDIA       = 0;
  private static final int VIEW_TYPE_PLACEHOLDER = 1;

  private final List<MediaContent>              media;
  private final RequestManager                  requestManager;
  private final Listener                        listener;
  private final StableIdGenerator<MediaContent> idGenerator;

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
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return switch (viewType) {
      case VIEW_TYPE_MEDIA -> new MediaViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.attachment_keyboad_media_item, parent, false));
      case VIEW_TYPE_PLACEHOLDER -> new PlaceholderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.attachment_keyboad_media_placeholder_item, parent, false));
      default -> throw new IllegalArgumentException("Unsupported viewType: " + viewType);
    };
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.bind(media.get(position), requestManager, listener);
  }

  @Override
  public void onViewRecycled(@NonNull ViewHolder holder) {
    holder.recycle();
  }

  @Override
  public int getItemCount() {
    return media.size();
  }

  @Override
  public int getItemViewType(int position) {
    return media.get(position).isPlaceholder ? VIEW_TYPE_PLACEHOLDER : VIEW_TYPE_MEDIA;
  }

  public void setMedia(@NonNull List<Media> media, boolean addFooter) {
    this.media.clear();
    this.media.addAll(media.stream().map(MediaContent::new).collect(java.util.stream.Collectors.toList()));
    if (addFooter) {
      this.media.add(new MediaContent(true));
    }
    notifyDataSetChanged();
  }

  interface Listener {
    void onMediaClicked(@NonNull Media media);
  }

  private class MediaContent {
    private Media   media;
    private boolean isPlaceholder;

    public MediaContent(Media media) {
      this.media = media;
    }

    public MediaContent(boolean isPlaceholder) {
      this.isPlaceholder = isPlaceholder;
    }
  }

  static abstract class ViewHolder extends RecyclerView.ViewHolder {
    public ViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    void bind(@NonNull MediaContent media, @NonNull RequestManager requestManager, @NonNull Listener listener) {}

    void recycle() {}
  }

  static class PlaceholderViewHolder extends ViewHolder {
    public PlaceholderViewHolder(@NonNull View itemView) {
      super(itemView);
    }
  }

  static class MediaViewHolder extends ViewHolder {

    private final ThumbnailView image;
    private final TextView      duration;
    private final View          videoIcon;

    public MediaViewHolder(@NonNull View itemView) {
      super(itemView);
      image     = itemView.findViewById(R.id.attachment_keyboard_item_image);
      duration  = itemView.findViewById(R.id.attachment_keyboard_item_video_time);
      videoIcon = itemView.findViewById(R.id.attachment_keyboard_item_video_icon);
    }

    @Override
    void bind(@NonNull MediaContent mediaContent, @NonNull RequestManager requestManager, @NonNull Listener listener) {
      Media media = mediaContent.media;
      image.setImageResource(requestManager, media.getUri(), 400, 400);
      image.setOnClickListener(v -> listener.onMediaClicked(media));

      duration.setVisibility(View.GONE);
      videoIcon.setVisibility(View.GONE);

      if (media.getDuration() > 0) {
        duration.setVisibility(View.VISIBLE);
        duration.setText(formatTime(media.getDuration()));
      } else if (MediaUtil.isVideoType(media.getContentType())) {
        videoIcon.setVisibility(View.VISIBLE);
      }
    }

    @Override
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
