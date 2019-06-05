package org.thoughtcrime.securesms.components;


import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.util.views.Stub;
import org.thoughtcrime.securesms.video.VideoPlayer;

import java.io.IOException;

public class MediaView extends FrameLayout {

  private ZoomingImageView  imageView;
  private Stub<VideoPlayer> videoView;

  public MediaView(@NonNull Context context) {
    super(context);
    initialize();
  }

  public MediaView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public MediaView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public MediaView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  private void initialize() {
    inflate(getContext(), R.layout.media_view, this);

    this.imageView = findViewById(R.id.image);
    this.videoView = new Stub<>(findViewById(R.id.video_player_stub));
  }

  public void set(@NonNull GlideRequests glideRequests,
                  @NonNull Window window,
                  @NonNull Uri source,
                  @NonNull String mediaType,
                  long size,
                  boolean autoplay)
      throws IOException
  {
    if (mediaType.startsWith("image/")) {
      imageView.setVisibility(View.VISIBLE);
      if (videoView.resolved()) videoView.get().setVisibility(View.GONE);
      imageView.setImageUri(glideRequests, source, mediaType);
    } else if (mediaType.startsWith("video/")) {
      imageView.setVisibility(View.GONE);
      videoView.get().setVisibility(View.VISIBLE);
      videoView.get().setWindow(window);
      videoView.get().setVideoSource(new VideoSlide(getContext(), source, size), autoplay);
    } else {
      throw new IOException("Unsupported media type: " + mediaType);
    }
  }

  public void pause() {
    if (this.videoView.resolved()){
      this.videoView.get().pause();
    }
  }

  public void hideControls() {
    if (this.videoView.resolved()){
      this.videoView.get().hideControls();
    }
  }

  public @Nullable View getPlaybackControls() {
    if (this.videoView.resolved()){
      return this.videoView.get().getControlView();
    }
    return null;
  }

  public void cleanup() {
    this.imageView.cleanup();
    if (this.videoView.resolved()) {
      this.videoView.get().cleanup();
    }
  }
}
