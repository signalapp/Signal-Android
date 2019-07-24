package org.thoughtcrime.securesms.components;

import android.content.Context;
import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideClickListener;
import org.thoughtcrime.securesms.mms.SlidesClickedListener;
import org.thoughtcrime.securesms.util.views.Stub;

import java.util.List;

public class AlbumThumbnailView extends FrameLayout {

  private @Nullable SlideClickListener    thumbnailClickListener;
  private @Nullable SlidesClickedListener downloadClickListener;

  private int currentSizeClass;

  private ViewGroup                 albumCellContainer;
  private Stub<TransferControlView> transferControls;

  private final SlideClickListener defaultThumbnailClickListener = (v, slide) -> {
      if (thumbnailClickListener != null) {
        thumbnailClickListener.onClick(v, slide);
      }
  };

  private final OnLongClickListener defaultLongClickListener = v -> this.performLongClick();

  public AlbumThumbnailView(@NonNull Context context) {
    super(context);
    initialize();
  }

  public AlbumThumbnailView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  private void initialize() {
    inflate(getContext(), R.layout.album_thumbnail_view, this);

    albumCellContainer = findViewById(R.id.album_cell_container);
    transferControls   = new Stub<>(findViewById(R.id.album_transfer_controls_stub));
  }

  public void setSlides(@NonNull GlideRequests glideRequests, @NonNull List<Slide> slides, boolean showControls) {
    if (slides.size() < 2) {
      throw new IllegalStateException("Provided less than two slides.");
    }

    if (showControls) {
      transferControls.get().setShowDownloadText(true);
      transferControls.get().setSlides(slides);
      transferControls.get().setDownloadClickListener(v -> {
        if (downloadClickListener != null) {
          downloadClickListener.onClick(v, slides);
        }
      });
    } else {
      if (transferControls.resolved()) {
        transferControls.get().setVisibility(GONE);
      }
    }

    int sizeClass = sizeClass(slides.size());

    if (sizeClass != currentSizeClass) {
      inflateLayout(sizeClass);
      currentSizeClass = sizeClass;
    }

    showSlides(glideRequests, slides);
  }

  public void setCellBackgroundColor(@ColorInt int color) {
    ViewGroup cellRoot = findViewById(R.id.album_thumbnail_root);

    if (cellRoot != null) {
      for (int i = 0; i < cellRoot.getChildCount(); i++) {
        cellRoot.getChildAt(i).setBackgroundColor(color);
      }
    }
  }

  public void setThumbnailClickListener(@Nullable SlideClickListener listener) {
    thumbnailClickListener = listener;
  }

  public void setDownloadClickListener(@Nullable SlidesClickedListener listener) {
    downloadClickListener = listener;
  }

  private void inflateLayout(int sizeClass) {
    albumCellContainer.removeAllViews();

    switch (sizeClass) {
      case 2:
        inflate(getContext(), R.layout.album_thumbnail_2, albumCellContainer);
        break;
      case 3:
        inflate(getContext(), R.layout.album_thumbnail_3, albumCellContainer);
        break;
      case 4:
        inflate(getContext(), R.layout.album_thumbnail_4, albumCellContainer);
        break;
      case 5:
        inflate(getContext(), R.layout.album_thumbnail_5, albumCellContainer);
        break;
      default:
        inflate(getContext(), R.layout.album_thumbnail_many, albumCellContainer);
        break;
    }
  }

  private void showSlides(@NonNull GlideRequests glideRequests, @NonNull List<Slide> slides) {
    setSlide(glideRequests, slides.get(0), R.id.album_cell_1);
    setSlide(glideRequests, slides.get(1), R.id.album_cell_2);

    if (slides.size() >= 3) {
      setSlide(glideRequests, slides.get(2), R.id.album_cell_3);
    }

    if (slides.size() >= 4) {
      setSlide(glideRequests, slides.get(3), R.id.album_cell_4);
    }

    if (slides.size() >= 5) {
      setSlide(glideRequests, slides.get(4), R.id.album_cell_5);
    }

    if (slides.size() > 5) {
      TextView text = findViewById(R.id.album_cell_overflow_text);
      text.setText(getContext().getString(R.string.AlbumThumbnailView_plus, slides.size() - 5));
    }
  }

  private void setSlide(@NonNull GlideRequests glideRequests, @NonNull Slide slide, @IdRes int id) {
    ThumbnailView cell = findViewById(id);
    cell.setImageResource(glideRequests, slide, false, false);
    cell.setThumbnailClickListener(defaultThumbnailClickListener);
    cell.setOnLongClickListener(defaultLongClickListener);
  }

  private int sizeClass(int size) {
    return Math.min(size, 6);
  }
}
