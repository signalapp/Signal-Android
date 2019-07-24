package org.thoughtcrime.securesms.stickers;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.GlideRequests;

import java.util.ArrayList;
import java.util.List;

public final class StickerPackPreviewAdapter extends RecyclerView.Adapter<StickerPackPreviewAdapter.StickerViewHolder>  {

  private final GlideRequests                 glideRequests;
  private final List<StickerManifest.Sticker> list;

  public StickerPackPreviewAdapter(@NonNull GlideRequests glideRequests) {
    this.glideRequests = glideRequests;
    this.list          = new ArrayList<>();
  }

  @Override
  public @NonNull StickerViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new StickerViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.sticker_preview_list_item, viewGroup, false));
  }

  @Override
  public void onBindViewHolder(@NonNull StickerViewHolder stickerViewHolder, int i) {
    stickerViewHolder.bind(glideRequests, list.get(i));
  }

  @Override
  public int getItemCount() {
    return list.size();
  }

  void setStickers(List<StickerManifest.Sticker> stickers) {
    list.clear();
    list.addAll(stickers);
    notifyDataSetChanged();
  }

  static class StickerViewHolder extends RecyclerView.ViewHolder {

    private final ImageView image;

    private StickerViewHolder(@NonNull View itemView) {
      super(itemView);
      this.image = itemView.findViewById(R.id.sticker_install_item_image);
    }

    void bind(@NonNull GlideRequests glideRequests, @NonNull StickerManifest.Sticker sticker) {
      Object model = sticker.getUri().isPresent() ? new DecryptableStreamUriLoader.DecryptableUri(sticker.getUri().get())
                                                  : new StickerRemoteUri(sticker.getPackId(), sticker.getPackKey(), sticker.getId());
      glideRequests.load(model)
                   .transition(DrawableTransitionOptions.withCrossFade())
                   .into(image);
    }
  }
}
