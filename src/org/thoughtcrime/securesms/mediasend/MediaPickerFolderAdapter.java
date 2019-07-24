package org.thoughtcrime.securesms.mediasend;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideRequests;

import java.util.ArrayList;
import java.util.List;

class MediaPickerFolderAdapter extends RecyclerView.Adapter<MediaPickerFolderAdapter.FolderViewHolder> {

  private final GlideRequests     glideRequests;
  private final EventListener     eventListener;
  private final List<MediaFolder> folders;

  MediaPickerFolderAdapter(@NonNull GlideRequests glideRequests, @NonNull EventListener eventListener) {
    this.glideRequests = glideRequests;
    this.eventListener = eventListener;
    this.folders       = new ArrayList<>();
  }

  @NonNull
  @Override
  public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
    return new FolderViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.mediapicker_folder_item, viewGroup, false));
  }

  @Override
  public void onBindViewHolder(@NonNull FolderViewHolder folderViewHolder, int i) {
    folderViewHolder.bind(folders.get(i), glideRequests, eventListener);
  }

  @Override
  public void onViewRecycled(@NonNull FolderViewHolder holder) {
    holder.recycle();
  }

  @Override
  public int getItemCount() {
    return folders.size();
  }

  void setFolders(@NonNull List<MediaFolder> folders) {
    this.folders.clear();
    this.folders.addAll(folders);
    notifyDataSetChanged();
  }

  static class FolderViewHolder extends RecyclerView.ViewHolder {

    private final ImageView thumbnail;
    private final ImageView icon;
    private final TextView  title;
    private final TextView  count;

    FolderViewHolder(@NonNull View itemView) {
      super(itemView);

      thumbnail = itemView.findViewById(R.id.mediapicker_folder_item_thumbnail);
      icon      = itemView.findViewById(R.id.mediapicker_folder_item_icon);
      title     = itemView.findViewById(R.id.mediapicker_folder_item_title);
      count     = itemView.findViewById(R.id.mediapicker_folder_item_count);
    }

    void bind(@NonNull MediaFolder folder, @NonNull GlideRequests glideRequests, @NonNull EventListener eventListener) {
      title.setText(folder.getTitle());
      count.setText(String.valueOf(folder.getItemCount()));
      icon.setImageResource(folder.getFolderType() == MediaFolder.FolderType.CAMERA ? R.drawable.ic_camera_alt_white_24dp : R.drawable.ic_folder_white_48dp);

      glideRequests.load(folder.getThumbnailUri())
                   .diskCacheStrategy(DiskCacheStrategy.NONE)
                   .transition(DrawableTransitionOptions.withCrossFade())
                   .into(thumbnail);

      itemView.setOnClickListener(v -> eventListener.onFolderClicked(folder));
    }

    void recycle() {
      itemView.setOnClickListener(null);
    }
  }

  interface EventListener {
    void onFolderClicked(@NonNull MediaFolder mediaFolder);
  }
}
