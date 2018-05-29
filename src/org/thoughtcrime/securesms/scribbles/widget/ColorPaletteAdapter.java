package org.thoughtcrime.securesms.scribbles.widget;

import android.graphics.PorterDuff;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ColorPaletteAdapter extends RecyclerView.Adapter<ColorPaletteAdapter.ColorViewHolder> {

  private final List<Integer> colors = new ArrayList<>();

  private EventListener eventListener;

  @Override
  public ColorViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    return new ColorViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_color, parent, false));
  }

  @Override
  public void onBindViewHolder(ColorViewHolder holder, int position) {
    holder.bind(colors.get(position), eventListener);
  }

  @Override
  public int getItemCount() {
    return colors.size();
  }

  public void setColors(@NonNull Collection<Integer> colors) {
    this.colors.clear();
    this.colors.addAll(colors);

    notifyDataSetChanged();
  }

  public void setEventListener(@Nullable EventListener eventListener) {
    this.eventListener = eventListener;

    notifyDataSetChanged();
  }

  public interface EventListener {
    void onColorSelected(int color);
  }

  static class ColorViewHolder extends RecyclerView.ViewHolder {

    ImageView foreground;

    ColorViewHolder(View itemView) {
      super(itemView);
      foreground = itemView.findViewById(R.id.palette_item_foreground);
    }

    void bind(int color, @Nullable EventListener eventListener) {
      foreground.setColorFilter(color, PorterDuff.Mode.SRC_IN);

      if (eventListener != null) {
        itemView.setOnClickListener(v -> eventListener.onColorSelected(color));
      }
    }
  }
}
