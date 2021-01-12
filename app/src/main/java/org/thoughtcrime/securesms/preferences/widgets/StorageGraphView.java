package org.thoughtcrime.securesms.preferences.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StorageGraphView extends View {

           private final RectF            rect             = new RectF();
           private final Path             path             = new Path();
           private final Paint            paint            = new Paint();
  @NonNull private       StorageBreakdown storageBreakdown;
           private       StorageBreakdown emptyBreakdown;

  public StorageGraphView(Context context) {
    super(context);
    initialize();
  }

  public StorageGraphView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public StorageGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  private void initialize() {
    setWillNotDraw(false);
    paint.setStyle(Paint.Style.FILL);

    Entry emptyEntry = new Entry(ContextCompat.getColor(getContext(), R.color.storage_color_empty), 1);

    emptyBreakdown = new StorageBreakdown(Collections.singletonList(emptyEntry));

    setStorageBreakdown(emptyBreakdown);
  }

  public void setStorageBreakdown(@NonNull StorageBreakdown storageBreakdown) {
    if (storageBreakdown.totalSize == 0) {
      storageBreakdown = emptyBreakdown;
    }
    this.storageBreakdown = storageBreakdown;
    invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    int radius = getHeight() / 2;
    rect.set(0, 0, w, h);
    path.reset();
    path.addRoundRect(rect, radius, radius, Path.Direction.CW);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (storageBreakdown.totalSize == 0) return;

    canvas.clipPath(path);

    int startX     = 0;
    int entryCount = storageBreakdown.entries.size();
    int width      = getWidth();

    for (int i = 0; i < entryCount; i++) {
      Entry entry = storageBreakdown.entries.get(i);
      int endX = i < entryCount - 1 ? startX + (int) (width * entry.size / storageBreakdown.totalSize)
                                    : width;
      rect.left  = startX;
      rect.right = endX;
      paint.setColor(entry.color);
      canvas.drawRect(rect, paint);
      startX = endX;
    }
  }

  public static class StorageBreakdown {

    private final List<Entry> entries;
    private final long        totalSize;

    public StorageBreakdown(@NonNull List<Entry> entries) {
      this.entries = new ArrayList<>(entries);

      long total = 0;
      for (Entry entry : entries) {
        total += entry.size;
      }
      this.totalSize = total;
    }

    public long getTotalSize() {
      return totalSize;
    }
  }

  public static class Entry {

    @ColorInt private final int  color;
              private final long size;

    public Entry(@ColorInt int color, long size) {
      this.color = color;
      this.size  = size;
    }
  }
}
