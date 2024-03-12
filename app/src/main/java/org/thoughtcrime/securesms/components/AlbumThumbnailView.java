/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

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

import com.bumptech.glide.RequestManager;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.transfercontrols.TransferControlView;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideClickListener;
import org.thoughtcrime.securesms.mms.SlidesClickedListener;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.views.Stub;

import java.util.List;

public class AlbumThumbnailView extends FrameLayout {

  private @Nullable SlideClickListener    thumbnailClickListener;
  private @Nullable SlidesClickedListener startTransferClickListener;
  private @Nullable SlidesClickedListener cancelTransferClickListener;
  private @Nullable SlideClickListener    playVideoClickListener;

  private int currentSizeClass;

  private final int[] corners = new int[4];

  private final ViewGroup                 albumCellContainer;
  private final Stub<TransferControlView> transferControlsStub;

  private final SlideClickListener defaultThumbnailClickListener = (v, slide) -> {
    if (thumbnailClickListener != null) {
      thumbnailClickListener.onClick(v, slide);
    }
  };

  private final OnLongClickListener defaultLongClickListener = v -> this.performLongClick();

  public AlbumThumbnailView(@NonNull Context context) {
    super(context);
    inflate(getContext(), R.layout.album_thumbnail_view, this);

    albumCellContainer    = findViewById(R.id.album_cell_container);
    transferControlsStub  = new Stub<>(findViewById(R.id.album_transfer_controls_stub));
  }

  public AlbumThumbnailView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    inflate(getContext(), R.layout.album_thumbnail_view, this);

    albumCellContainer    = findViewById(R.id.album_cell_container);
    transferControlsStub  = new Stub<>(findViewById(R.id.album_transfer_controls_stub));
  }

  public void setSlides(@NonNull RequestManager requestManager, @NonNull List<Slide> slides, boolean showControls) {
    if (slides.size() < 2) {
      throw new IllegalStateException("Provided less than two slides.");
    }

    if (showControls) {
      transferControlsStub.get().setShowSecondaryText(true);
      transferControlsStub.get().setTransferClickListener(
          v -> {
            if (startTransferClickListener != null) {
              startTransferClickListener.onClick(v, slides);
            }
          });
      transferControlsStub.get().setCancelClickListener(
          v -> {
            if (cancelTransferClickListener != null) {
              cancelTransferClickListener.onClick(v, slides);
            }
          });
      transferControlsStub.get().setSlides(slides);
    } else {
      if (transferControlsStub.resolved()) {
        transferControlsStub.get().setVisibility(GONE);
      }
    }

    int sizeClass = sizeClass(slides.size());

    if (sizeClass != currentSizeClass) {
      inflateLayout(sizeClass);
      currentSizeClass = sizeClass;
    }

    showSlides(requestManager, slides);
    applyCorners();
    forceLayout();
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

  public void setStartTransferClickListener(SlidesClickedListener listener) {
    this.startTransferClickListener = listener;
  }

  public void setCancelTransferClickListener(SlidesClickedListener listener) {
    this.cancelTransferClickListener = listener;
  }

  public void setPlayVideoClickListener(SlideClickListener listener) {
    this.playVideoClickListener = listener;
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

    int resId;
    switch (sizeClass) {
      case 2:
        resId = R.layout.album_thumbnail_2;
        break;
      case 3:
        resId = R.layout.album_thumbnail_3;
        break;
      case 4:
        resId = R.layout.album_thumbnail_4;
        break;
      case 5:
        resId = R.layout.album_thumbnail_5;
        break;
      default:
        resId = R.layout.album_thumbnail_many;
        break;
    }

    inflate(getContext(), resId, albumCellContainer);
    if (transferControlsStub.resolved()) {
      int size;
      switch (sizeClass) {
        case 2:
          size = R.dimen.album_2_total_height;
          break;
        case 3:
          size = R.dimen.album_3_total_height;
          break;
        case 4:
          size = R.dimen.album_4_total_height;
          break;
        default:
          size = R.dimen.album_5_total_height;
          break;
      }
      ViewGroup.LayoutParams params = transferControlsStub.get().getLayoutParams();
      params.height = getContext().getResources().getDimensionPixelSize(size);
      transferControlsStub.get().setLayoutParams(params);
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

  private void showSlides(@NonNull RequestManager requestManager, @NonNull List<Slide> slides) {
    boolean showControls = TransferControlView.containsPlayableSlides(slides);
    setSlide(requestManager, slides.get(0), R.id.album_cell_1, showControls);
    setSlide(requestManager, slides.get(1), R.id.album_cell_2, showControls);

    if (slides.size() >= 3) {
      setSlide(requestManager, slides.get(2), R.id.album_cell_3, showControls);
    }

    if (slides.size() >= 4) {
      setSlide(requestManager, slides.get(3), R.id.album_cell_4, showControls);
    }

    if (slides.size() >= 5) {
      setSlide(requestManager, slides.get(4), R.id.album_cell_5, showControls && slides.size() == 5);
    }

    if (slides.size() > 5) {
      TextView text = findViewById(R.id.album_cell_overflow_text);
      text.setText(getContext().getString(R.string.AlbumThumbnailView_plus, slides.size() - 5));
    }
  }

  private void setSlide(@NonNull RequestManager requestManager, @NonNull Slide slide, @IdRes int id, boolean showControls) {
    ThumbnailView cell = findViewById(id);
    cell.showSecondaryText(false);
    cell.setThumbnailClickListener(defaultThumbnailClickListener);
    cell.setStartTransferClickListener(startTransferClickListener);
    cell.setCancelTransferClickListener(cancelTransferClickListener);
    if (MediaUtil.isInstantVideoSupported(slide)) {
      cell.setPlayVideoClickListener(playVideoClickListener);
    }
    cell.setOnLongClickListener(defaultLongClickListener);
    cell.setImageResource(requestManager, slide, showControls, false);
  }

  private int sizeClass(int size) {
    return Math.min(size, 6);
  }
}
