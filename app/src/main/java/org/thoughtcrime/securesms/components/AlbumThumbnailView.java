package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

  private final int[] corners = new int[4];

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
    applyCorners();
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

  public void setRadii(int topLeft, int topRight, int bottomRight, int bottomLeft) {
    corners[0] = topLeft;
    corners[1] = topRight;
    corners[2] = bottomRight;
    corners[3] = bottomLeft;

    applyCorners();
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

  private void applyCorners() {
    if (currentSizeClass < 2) {
      return;
    }

    switch (currentSizeClass) {
      case 2:
        applyCornersForSizeClass2();
        break;
      case 3:
        applyCornersForSizeClass3();
        break;
      case 4:
        applyCornersForSizeClass4();
        break;
      case 5:
        applyCornersForSizeClass5();
        break;
      default:
        applyCornersForManySizeClass();
    }
  }

  private ThumbnailView[] getCells() {
    ThumbnailView one   = findViewById(R.id.album_cell_1);
    ThumbnailView two   = findViewById(R.id.album_cell_2);
    ThumbnailView three = findViewById(R.id.album_cell_3);
    ThumbnailView four  = findViewById(R.id.album_cell_4);
    ThumbnailView five  = findViewById(R.id.album_cell_5);

    return new ThumbnailView[] { one, two, three, four, five };
  }

  private void applyCornersForSizeClass2() {
    ThumbnailView[] cells = getCells();
    setRelativeRadii(cells[0], corners[0], 0, 0, corners[3]);
    setRelativeRadii(cells[1], 0, corners[1], corners[2], 0);
  }

  private void applyCornersForSizeClass3() {
    ThumbnailView[] cells = getCells();
    setRelativeRadii(cells[0], corners[0], 0, 0, corners[3]);
    setRelativeRadii(cells[1], 0, corners[1], 0, 0);
    setRelativeRadii(cells[2], 0, 0, corners[2], 0);
  }

  private void applyCornersForSizeClass4() {
    ThumbnailView[] cells = getCells();
    setRelativeRadii(cells[0], corners[0], 0, 0, 0);
    setRelativeRadii(cells[1], 0, corners[1], 0, 0);
    setRelativeRadii(cells[2], 0, 0, 0, corners[3]);
    setRelativeRadii(cells[3], 0, 0, corners[2], 0);
  }

  private void applyCornersForSizeClass5() {
    ThumbnailView[] cells = getCells();
    setRelativeRadii(cells[0], corners[0], 0, 0, 0);
    setRelativeRadii(cells[1], 0, corners[1], 0, 0);
    setRelativeRadii(cells[2], 0, 0, 0, corners[3]);
    setRelativeRadii(cells[3], 0, 0, 0, 0);
    setRelativeRadii(cells[4], 0, 0, corners[2], 0);
  }

  private void setRelativeRadii(@NonNull ThumbnailView cell, int topLeft, int topRight, int bottomRight, int bottomLeft) {
    boolean isLTR = getRootView().getLayoutDirection() == LAYOUT_DIRECTION_LTR;
    cell.setRadii(
        isLTR ? topLeft : topRight,
        isLTR ? topRight : topLeft,
        isLTR ? bottomRight : bottomLeft,
        isLTR ? bottomLeft : bottomRight
    );
  }

  private void applyCornersForManySizeClass() {
    applyCornersForSizeClass5();
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
