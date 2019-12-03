/*
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.mediaoverview;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.codewaves.stickyheadergrid.StickyHeaderGridAdapter;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.components.AudioView;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.database.MediaDatabase;
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord;
import org.thoughtcrime.securesms.database.loaders.GroupedThreadMediaLoader.GroupedThreadMedia;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

final class MediaGalleryAllAdapter extends StickyHeaderGridAdapter {

  private final Context                        context;
  private final GlideRequests                  glideRequests;
  private final ItemClickListener              itemClickListener;
  private final Map<AttachmentId, MediaRecord> selected          = new HashMap<>();

  private GroupedThreadMedia media;
  private boolean            showFileSizes;
  private boolean            detailView;

  private static final int AUDIO_DETAIL    = 1;
  private static final int GALLERY         = 2;
  private static final int GALLERY_DETAIL  = 3;
  private static final int DOCUMENT_DETAIL = 4;

  public void pause(RecyclerView.ViewHolder holder) {
    if (holder instanceof AudioDetailViewHolder) {
      ((AudioDetailViewHolder) holder).pause();
    }
  }

  private static class HeaderHolder extends HeaderViewHolder {
    TextView textView;

    HeaderHolder(View itemView) {
      super(itemView);
      textView = itemView.findViewById(R.id.text);
    }
  }

  MediaGalleryAllAdapter(@NonNull Context context,
                         @NonNull GlideRequests glideRequests,
                         GroupedThreadMedia media,
                         ItemClickListener clickListener,
                         boolean showFileSizes)
  {
    this.context           = context;
    this.glideRequests     = glideRequests;
    this.media             = media;
    this.itemClickListener = clickListener;
    this.showFileSizes     = showFileSizes;
  }

  public void setMedia(GroupedThreadMedia media) {
    this.media = media;
  }

  @Override
  public HeaderViewHolder onCreateHeaderViewHolder(ViewGroup parent, int headerType) {
    return new HeaderHolder(LayoutInflater.from(context).inflate(R.layout.media_overview_item_header, parent, false));
  }

  @Override
  public ItemViewHolder onCreateItemViewHolder(ViewGroup parent, int itemType) {
    switch (itemType) {
      case GALLERY:
        return new GalleryViewHolder(LayoutInflater.from(context).inflate(R.layout.media_overview_gallery_item, parent, false));
      case GALLERY_DETAIL:
        return new GalleryDetailViewHolder(LayoutInflater.from(context).inflate(R.layout.media_overview_detail_item_media, parent, false));
      case AUDIO_DETAIL:
        return new AudioDetailViewHolder(LayoutInflater.from(context).inflate(R.layout.media_overview_detail_item_audio, parent, false));
      default:
        return new DocumentDetailViewHolder(LayoutInflater.from(context).inflate(R.layout.media_overview_detail_item_document, parent, false));
    }
  }

  @Override
  public int getSectionItemViewType(int section, int offset) {
    MediaDatabase.MediaRecord mediaRecord = media.get(section, offset);
    Slide                     slide       = MediaUtil.getSlideForAttachment(context, mediaRecord.getAttachment());

    if (slide.hasAudio())                     return AUDIO_DETAIL;
    if (slide.hasImage() || slide.hasVideo()) return detailView ? GALLERY_DETAIL : GALLERY;
    if (slide.hasDocument())                  return DOCUMENT_DETAIL;

    return 0;
  }

  @Override
  public void onBindHeaderViewHolder(HeaderViewHolder viewHolder, int section) {
    ((HeaderHolder)viewHolder).textView.setText(media.getName(section));
  }

  @Override
  public void onBindItemViewHolder(ItemViewHolder viewHolder, int section, int offset) {
    MediaDatabase.MediaRecord mediaRecord = media.get(section, offset);
    Slide                     slide       = MediaUtil.getSlideForAttachment(context, mediaRecord.getAttachment());

    ((SelectableViewHolder)viewHolder).bind(context, mediaRecord, slide);
  }

  @Override
  public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
    super.onViewDetachedFromWindow(holder);
    if (holder instanceof SelectableViewHolder) {
      ((SelectableViewHolder) holder).detached();
    }
  }

  @Override
  public int getSectionCount() {
    return media.getSectionCount();
  }

  @Override
  public int getSectionItemCount(int section) {
    return media.getSectionItemCount(section);
  }

  public void toggleSelection(@NonNull MediaRecord mediaRecord) {
    AttachmentId              attachmentId = mediaRecord.getAttachment().getAttachmentId();
    MediaDatabase.MediaRecord removed      = selected.remove(attachmentId);
    if (removed == null) {
      selected.put(attachmentId, mediaRecord);
    }

    notifyDataSetChanged();
  }

  public int getSelectedMediaCount() {
    return selected.size();
  }

  @NonNull
  public Collection<MediaRecord> getSelectedMedia() {
    return new HashSet<>(selected.values());
  }

  public void clearSelection() {
    selected.clear();
    notifyDataSetChanged();
  }

  void selectAllMedia() {
    for (int section = 0; section < media.getSectionCount(); section++) {
      for (int item = 0; item < media.getSectionItemCount(section); item++) {
        MediaRecord mediaRecord = media.get(section, item);
        selected.put(mediaRecord.getAttachment().getAttachmentId(), mediaRecord);
      }
    }
    this.notifyDataSetChanged();
  }

  void setShowFileSizes(boolean showFileSizes) {
    this.showFileSizes = showFileSizes;
  }

  void setDetailView(boolean detailView) {
    this.detailView = detailView;
  }

  class SelectableViewHolder extends ItemViewHolder {

    private final View                      selectedIndicator;
    private       MediaDatabase.MediaRecord mediaRecord;

    SelectableViewHolder(@NonNull View itemView) {
      super(itemView);
      this.selectedIndicator = itemView.findViewById(R.id.selected_indicator);
    }

    public void bind(@NonNull Context context, @NonNull MediaDatabase.MediaRecord mediaRecord, @NonNull Slide slide) {
      this.mediaRecord = mediaRecord;
      updateSelectedView();
    }

    private void updateSelectedView() {
      if (selectedIndicator != null) {
        selectedIndicator.setVisibility(selected.containsKey(mediaRecord.getAttachment().getAttachmentId()) ? View.VISIBLE : View.GONE);
      }
    }

    boolean onLongClick() {
      itemClickListener.onMediaLongClicked(mediaRecord);
      updateSelectedView();
      return true;
    }

    void detached() {
    }
  }

  private class GalleryViewHolder extends SelectableViewHolder {

    private final ThumbnailView thumbnailView;
    private final TextView      imageFileSize;

    GalleryViewHolder(@NonNull View itemView) {
      super(itemView);
      this.thumbnailView = itemView.findViewById(R.id.image);
      this.imageFileSize = itemView.findViewById(R.id.image_file_size);
    }

    @Override
    public void bind(@NonNull Context context, @NonNull MediaDatabase.MediaRecord mediaRecord, @NonNull Slide slide) {
      super.bind(context, mediaRecord, slide);

      if (showFileSizes | detailView) {
        imageFileSize.setText(Util.getPrettyFileSize(slide.getFileSize()));
        imageFileSize.setVisibility(View.VISIBLE);
      } else {
        imageFileSize.setVisibility(View.GONE);
      }

      thumbnailView.setImageResource(glideRequests, slide, false, false);

      thumbnailView.setOnClickListener(view -> itemClickListener.onMediaClicked(mediaRecord));
      thumbnailView.setOnLongClickListener(view -> onLongClick());
    }

    @Override
    void detached() {
      thumbnailView.clear(glideRequests);
    }
  }

  private class DetailViewHolder extends SelectableViewHolder {

    protected final View     itemView;
    private   final TextView line1;
    private   final TextView line2;

    DetailViewHolder(@NonNull View itemView) {
      super(itemView);
      this.line1    = itemView.findViewById(R.id.line1);
      this.line2    = itemView.findViewById(R.id.line2);
      this.itemView = itemView;
    }

    @Override
    public void bind(@NonNull Context context, @NonNull MediaDatabase.MediaRecord mediaRecord, @NonNull Slide slide) {
      super.bind(context, mediaRecord, slide);

      line1.setText(getLine1(context, slide));
      line2.setText(getLine2(mediaRecord, slide));
      line1.setVisibility(View.VISIBLE);
      line2.setVisibility(View.VISIBLE);
      itemView.setOnClickListener(view -> itemClickListener.onMediaClicked(mediaRecord));
      itemView.setOnLongClickListener(view -> onLongClick());
    }

    private String getLine1(@NonNull Context context, @NonNull Slide slide) {
      return slide.getFileName()
                  .or(slide.getCaption())
                  .or(() -> describeUnnamedFile(context, slide));
    }

    private String getLine2(@NonNull MediaDatabase.MediaRecord mediaRecord, @NonNull Slide slide) {
      String date = DateUtils.formatDate(Locale.getDefault(), mediaRecord.getDate());
      return Util.getPrettyFileSize(slide.getFileSize()) + " " + date;
    }

    protected String describeUnnamedFile(@NonNull Context context, @NonNull Slide slide) {
      return context.getString(R.string.DocumentView_unnamed_file);
    }
  }

  private class DocumentDetailViewHolder extends DetailViewHolder {

    private final TextView documentType;

    DocumentDetailViewHolder(@NonNull View itemView) {
      super(itemView);
      this.documentType = itemView.findViewById(R.id.document_extension);
    }

    @Override
    public void bind(@NonNull Context context, @NonNull MediaDatabase.MediaRecord mediaRecord, @NonNull Slide slide) {
      super.bind(context, mediaRecord, slide);

      documentType.setText(slide.getFileType(context).or("").toLowerCase());
    }
  }

  private class AudioDetailViewHolder extends DetailViewHolder {

    private final AudioView audioView;

    AudioDetailViewHolder(@NonNull View itemView) {
      super(itemView);
      this.audioView = itemView.findViewById(R.id.audio);
    }

    @Override
    public void bind(@NonNull Context context, @NonNull MediaDatabase.MediaRecord mediaRecord, @NonNull Slide slide) {
      super.bind(context, mediaRecord, slide);

      if (!slide.hasAudio()) {
        throw new AssertionError();
      }

      audioView.setAudio((AudioSlide) slide, true);
      audioView.setOnClickListener(view -> itemClickListener.onMediaClicked(mediaRecord));
      itemView.setOnClickListener(view -> itemClickListener.onMediaClicked(mediaRecord));
    }

    @Override
    void detached() {
      audioView.stopPlaybackAndReset();
    }

    @Override
    protected String describeUnnamedFile(@NonNull Context context, @NonNull Slide slide) {
      return context.getString(R.string.DocumentView_audio_file);
    }

    public void pause() {
      audioView.stopPlaybackAndReset();
    }
  }

  private class GalleryDetailViewHolder extends DetailViewHolder {

    private final ThumbnailView thumbnailView;

    GalleryDetailViewHolder(@NonNull View itemView) {
      super(itemView);
      this.thumbnailView = itemView.findViewById(R.id.image);
    }

    @Override
    public void bind(@NonNull Context context, @NonNull MediaDatabase.MediaRecord mediaRecord, @NonNull Slide slide) {
      super.bind(context, mediaRecord, slide);

      thumbnailView.setImageResource(glideRequests, slide, false, false);
      thumbnailView.setOnClickListener(view -> itemClickListener.onMediaClicked(mediaRecord));
    }

    @Override
    protected String describeUnnamedFile(@NonNull Context context, @NonNull Slide slide) {
      if (slide.hasVideo()) return context.getString(R.string.DocumentView_video_file);
      if (slide.hasImage()) return context.getString(R.string.DocumentView_image_file);
      return super.describeUnnamedFile(context, slide);
    }

    @Override
    void detached() {
      thumbnailView.clear(glideRequests);
    }
  }

  interface ItemClickListener {
    void onMediaClicked(@NonNull MediaDatabase.MediaRecord mediaRecord);
    void onMediaLongClicked(MediaDatabase.MediaRecord mediaRecord);
  }
}
