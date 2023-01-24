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
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.codewaves.stickyheadergrid.StickyHeaderGridAdapter;

import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.components.AudioView;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState;
import org.thoughtcrime.securesms.database.MediaTable;
import org.thoughtcrime.securesms.database.MediaTable.MediaRecord;
import org.thoughtcrime.securesms.database.loaders.GroupedThreadMediaLoader.GroupedThreadMedia;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.livedata.LiveDataPair;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

final class MediaGalleryAllAdapter extends StickyHeaderGridAdapter {

  private static final long SELECTION_ANIMATION_DURATION = TimeUnit.MILLISECONDS.toMillis(150);

  private final Context                        context;
  private final boolean                        showThread;
  private final GlideRequests                  glideRequests;
  private final ItemClickListener              itemClickListener;
  private final Map<AttachmentId, MediaRecord> selected = new HashMap<>();
  private final AudioItemListener              audioItemListener;

  private GroupedThreadMedia media;
  private boolean            showFileSizes;
  private boolean            detailView;

  private static final int AUDIO_DETAIL    = 1;
  public static final  int GALLERY         = 2;
  private static final int GALLERY_DETAIL  = 3;
  private static final int DOCUMENT_DETAIL = 4;

  private static final int PAYLOAD_SELECTED = 1;

  void detach(RecyclerView.ViewHolder holder) {
    if (holder instanceof SelectableViewHolder) {
      ((SelectableViewHolder) holder).onDetached();
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
                         @NonNull AudioItemListener audioItemListener,
                         boolean showFileSizes,
                         boolean showThread)
  {
    this.context           = context;
    this.glideRequests     = glideRequests;
    this.media             = media;
    this.itemClickListener = clickListener;
    this.audioItemListener = audioItemListener;
    this.showFileSizes     = showFileSizes;
    this.showThread        = showThread;
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
    MediaTable.MediaRecord mediaRecord = media.get(section, offset);
    Slide                  slide       = MediaUtil.getSlideForAttachment(context, mediaRecord.getAttachment());

    if (slide.hasAudio()) return AUDIO_DETAIL;
    if (slide.hasImage() || slide.hasVideo()) return detailView ? GALLERY_DETAIL : GALLERY;
    if (slide.hasDocument()) return DOCUMENT_DETAIL;

    return 0;
  }

  @Override
  public void onBindHeaderViewHolder(HeaderViewHolder viewHolder, int section) {
    ((HeaderHolder) viewHolder).textView.setText(media.getName(section));
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
    if (holder instanceof SelectableViewHolder && payloads.contains(PAYLOAD_SELECTED)) {
      SelectableViewHolder selectableViewHolder = (SelectableViewHolder) holder;
      selectableViewHolder.animateSelectedView();
    } else {
      super.onBindViewHolder(holder, position, payloads);
    }
  }

  @Override
  public void onBindItemViewHolder(ItemViewHolder viewHolder, int section, int offset) {
    MediaTable.MediaRecord mediaRecord = media.get(section, offset);
    Slide                  slide       = MediaUtil.getSlideForAttachment(context, mediaRecord.getAttachment());

    ((SelectableViewHolder) viewHolder).bind(context, mediaRecord, slide);
  }

  @Override
  public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
    super.onViewDetachedFromWindow(holder);
    if (holder instanceof SelectableViewHolder) {
      ((SelectableViewHolder) holder).onDetached();
    }
  }

  @Override
  public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
    super.onViewAttachedToWindow(holder);
    if (holder instanceof SelectableViewHolder) {
      ((SelectableViewHolder) holder).onAttached();
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
    AttachmentId           attachmentId = mediaRecord.getAttachment().getAttachmentId();
    MediaTable.MediaRecord removed      = selected.remove(attachmentId);
    if (removed == null) {
      selected.put(attachmentId, mediaRecord);
    }

    notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTED);
  }

  public int getSelectedMediaCount() {
    return selected.size();
  }

  public long getSelectedMediaTotalFileSize() {
    //noinspection ConstantConditions attacment cannot be null if selected
    return Stream.of(selected.values())
                 .collect(Collectors.summingLong(a -> a.getAttachment().getSize()));
  }

  @NonNull
  public Collection<MediaRecord> getSelectedMedia() {
    return new HashSet<>(selected.values());
  }

  public void clearSelection() {
    selected.clear();
    notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTED);
  }

  void selectAllMedia() {
    int sectionCount = media.getSectionCount();
    for (int section = 0; section < sectionCount; section++) {
      int sectionItemCount = media.getSectionItemCount(section);
      for (int item = 0; item < sectionItemCount; item++) {
        MediaRecord mediaRecord = media.get(section, item);
        selected.put(mediaRecord.getAttachment().getAttachmentId(), mediaRecord);
      }
    }
    this.notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTED);
  }

  void setShowFileSizes(boolean showFileSizes) {
    this.showFileSizes = showFileSizes;
  }

  void setDetailView(boolean detailView) {
    this.detailView = detailView;
  }

  class SelectableViewHolder extends ItemViewHolder {

    protected final View selectedIndicator;

    private MediaTable.MediaRecord mediaRecord;
    private boolean                bound;

    SelectableViewHolder(@NonNull View itemView) {
      super(itemView);
      this.selectedIndicator = itemView.findViewById(R.id.selected_indicator);
    }

    public void bind(@NonNull Context context, @NonNull MediaTable.MediaRecord mediaRecord, @NonNull Slide slide) {
      if (bound) {
        unbind();
      }
      this.mediaRecord = mediaRecord;
      updateSelectedView();
      bound = true;
    }

    void rebind() {
      bound = true;
    }

    void unbind() {
      bound = false;
    }

    protected boolean isSelected() {
      return selected.containsKey(mediaRecord.getAttachment().getAttachmentId());
    }

    protected void updateSelectedView() {
      if (selectedIndicator != null) {
        selectedIndicator.animate().cancel();
        selectedIndicator.setAlpha(isSelected() ? 1f : 0f);
      }
    }

    protected void animateSelectedView() {
      if (selectedIndicator != null) {
        selectedIndicator.animate()
                         .alpha(isSelected() ? 1f : 0f)
                         .setDuration(SELECTION_ANIMATION_DURATION);
      }
    }

    boolean onLongClick() {
      itemClickListener.onMediaLongClicked(mediaRecord);
      return true;
    }

    void onDetached() {
      if (bound) {
        unbind();
      }
    }

    void onAttached() {
      if (!bound) {
        rebind();
      }
    }
  }

  private class GalleryViewHolder extends SelectableViewHolder {

    private static final float SCALE_SELECTED = 0.83f;
    private static final float SCALE_NORMAL   = 1f;

    private final ThumbnailView thumbnailView;
    private final TextView      imageFileSize;

    private Slide slide;

    GalleryViewHolder(@NonNull View itemView) {
      super(itemView);
      this.thumbnailView = itemView.findViewById(R.id.image);
      this.imageFileSize = itemView.findViewById(R.id.image_file_size);
    }

    @Override
    public void bind(@NonNull Context context, @NonNull MediaTable.MediaRecord mediaRecord, @NonNull Slide slide) {
      super.bind(context, mediaRecord, slide);
      this.slide = slide;
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
    protected void updateSelectedView() {
      super.updateSelectedView();

      thumbnailView.animate().cancel();

      float scale = isSelected() ? SCALE_SELECTED : SCALE_NORMAL;
      thumbnailView.setScaleX(scale);
      thumbnailView.setScaleY(scale);
    }

    @Override
    void rebind() {
      thumbnailView.setImageResource(glideRequests, slide, false, false);
      super.rebind();
    }

    @Override
    void unbind() {
      thumbnailView.clear(glideRequests);
      super.unbind();
    }

    @Override
    public void animateSelectedView() {
      super.animateSelectedView();

      float scale = isSelected() ? SCALE_SELECTED : SCALE_NORMAL;
      thumbnailView.animate()
                   .scaleX(scale)
                   .scaleY(scale)
                   .setDuration(SELECTION_ANIMATION_DURATION);
    }
  }

  private abstract class DetailViewHolder extends SelectableViewHolder implements Observer<Pair<Recipient, Recipient>> {

    protected final View                               itemView;
    private final   TextView                           line1;
    private final   TextView                           line2;
    private LiveDataPair<Recipient, Recipient> liveDataPair;
    private Optional<String>                   fileName;
    private String                             fileTypeDescription;
    private         Handler                            handler;
    private         Runnable                           selectForMarque;

    DetailViewHolder(@NonNull View itemView) {
      super(itemView);
      this.line1    = itemView.findViewById(R.id.line1);
      this.line2    = itemView.findViewById(R.id.line2);
      this.itemView = itemView;
    }

    @Override
    public void bind(@NonNull Context context, @NonNull MediaTable.MediaRecord mediaRecord, @NonNull Slide slide) {
      super.bind(context, mediaRecord, slide);

      fileName            = slide.getFileName();
      fileTypeDescription = getFileTypeDescription(context, slide);

      line1.setText(fileName.orElse(fileTypeDescription));
      line2.setText(getLine2(context, mediaRecord, slide));
      itemView.setOnClickListener(view -> itemClickListener.onMediaClicked(mediaRecord));
      itemView.setOnLongClickListener(view -> onLongClick());
      selectForMarque = () -> line1.setSelected(true);
      handler         = new Handler(Looper.getMainLooper());
      handler.postDelayed(selectForMarque, 2500);

      LiveRecipient from = mediaRecord.isOutgoing() ? Recipient.self().live() : Recipient.live(mediaRecord.getRecipientId());
      LiveRecipient to   = Recipient.live(mediaRecord.getThreadRecipientId());

      liveDataPair = new LiveDataPair<>(from.getLiveData(), to.getLiveData(), Recipient.UNKNOWN, Recipient.UNKNOWN);
      liveDataPair.observeForever(this);
    }

    @Override
    void rebind() {
      liveDataPair.observeForever(this);
      handler.postDelayed(selectForMarque, 2500);
      super.rebind();
    }

    @Override
    void unbind() {
      liveDataPair.removeObserver(this);
      handler.removeCallbacks(selectForMarque);
      line1.setSelected(false);
      super.unbind();
    }

    private String getLine2(@NonNull Context context, @NonNull MediaTable.MediaRecord mediaRecord, @NonNull Slide slide) {
      return context.getString(R.string.MediaOverviewActivity_detail_line_3_part,
                               Util.getPrettyFileSize(slide.getFileSize()),
                               getFileTypeDescription(context, slide),
                               DateUtils.formatDateWithoutDayOfWeek(Locale.getDefault(), mediaRecord.getDate()));
    }

    protected String getFileTypeDescription(@NonNull Context context, @NonNull Slide slide) {
      return context.getString(R.string.MediaOverviewActivity_file);
    }

    @Override
    public void onChanged(Pair<Recipient, Recipient> fromToPair) {
      line1.setText(describe(fromToPair.first(), fromToPair.second()));
    }

    protected @Nullable String getMediaTitle() {
      return fileName.orElse(null);
    }

    private @NonNull String describe(@NonNull Recipient from, @NonNull Recipient thread) {
      if (from == Recipient.UNKNOWN && thread == Recipient.UNKNOWN) {
        return fileName.orElse(fileTypeDescription);
      }

      String sentFromToString = getSentFromToString(from, thread);
      String mediaTitle       = getMediaTitle();

      if (mediaTitle != null) {
        return context.getString(R.string.MediaOverviewActivity_detail_line_2_part,
                                 mediaTitle,
                                 sentFromToString);
      } else {
        return sentFromToString;
      }
    }

    private String getSentFromToString(@NonNull Recipient from, @NonNull Recipient thread) {
      if (from.isSelf() && from == thread) {
        return context.getString(R.string.note_to_self);
      }

      if (showThread && (from.isSelf() || thread.isGroup())) {
        if (from.isSelf()) {
          return context.getString(R.string.MediaOverviewActivity_sent_by_you_to_s, thread.getDisplayName(context));
        } else {
          return context.getString(R.string.MediaOverviewActivity_sent_by_s_to_s, from.getDisplayName(context), thread.getDisplayName(context));
        }
      } else {
        if (from.isSelf()) {
          return context.getString(R.string.MediaOverviewActivity_sent_by_you);
        } else {
          return context.getString(R.string.MediaOverviewActivity_sent_by_s, from.getDisplayName(context));
        }
      }
    }
  }

  private class DocumentDetailViewHolder extends DetailViewHolder {

    private final TextView documentType;

    DocumentDetailViewHolder(@NonNull View itemView) {
      super(itemView);
      this.documentType = itemView.findViewById(R.id.document_extension);
    }

    @Override
    public void bind(@NonNull Context context, @NonNull MediaTable.MediaRecord mediaRecord, @NonNull Slide slide) {
      super.bind(context, mediaRecord, slide);

      documentType.setText(slide.getFileType(context).orElse("").toLowerCase());
    }
  }

  private class AudioDetailViewHolder extends DetailViewHolder {

    private final AudioView audioView;

    private boolean isVoiceNote;

    AudioDetailViewHolder(@NonNull View itemView) {
      super(itemView);
      this.audioView = itemView.findViewById(R.id.audio);
    }

    @Override
    public void bind(@NonNull Context context, @NonNull MediaTable.MediaRecord mediaRecord, @NonNull Slide slide) {
      if (!slide.hasAudio()) {
        throw new AssertionError();
      }

      isVoiceNote = slide.asAttachment().isVoiceNote();

      super.bind(context, mediaRecord, slide);

      long mmsId = Objects.requireNonNull(mediaRecord.getAttachment()).getMmsId();

      audioItemListener.unregisterPlaybackStateObserver(audioView.getPlaybackStateObserver());
      audioView.setAudio((AudioSlide) slide, new AudioViewCallbacksAdapter(audioItemListener, mmsId), true, true);
      audioItemListener.registerPlaybackStateObserver(audioView.getPlaybackStateObserver());

      audioView.setOnClickListener(view -> itemClickListener.onMediaClicked(mediaRecord));
      itemView.setOnClickListener(view -> itemClickListener.onMediaClicked(mediaRecord));
    }

    @Override
    protected @NonNull String getMediaTitle() {
      return context.getString(R.string.ThreadRecord_voice_message);
    }

    @Override
    void rebind() {
      super.rebind();
      audioItemListener.registerPlaybackStateObserver(audioView.getPlaybackStateObserver());
    }

    @Override
    void unbind() {
      super.unbind();
      audioItemListener.unregisterPlaybackStateObserver(audioView.getPlaybackStateObserver());
    }

    @Override
    protected String getFileTypeDescription(@NonNull Context context, @NonNull Slide slide) {
      return context.getString(R.string.MediaOverviewActivity_audio);
    }
  }

  private class GalleryDetailViewHolder extends DetailViewHolder {

    private final ThumbnailView thumbnailView;

    private Slide slide;

    GalleryDetailViewHolder(@NonNull View itemView) {
      super(itemView);
      this.thumbnailView = itemView.findViewById(R.id.image);
    }

    @Override
    public void bind(@NonNull Context context, @NonNull MediaTable.MediaRecord mediaRecord, @NonNull Slide slide) {
      super.bind(context, mediaRecord, slide);
      this.slide = slide;
      thumbnailView.setImageResource(glideRequests, slide, false, false);
      thumbnailView.setOnClickListener(view -> itemClickListener.onMediaClicked(mediaRecord));
      thumbnailView.setOnLongClickListener(view -> onLongClick());
    }

    @Override
    protected String getFileTypeDescription(@NonNull Context context, @NonNull Slide slide) {
      if (slide.hasVideo()) return context.getString(R.string.MediaOverviewActivity_video);
      if (slide.hasImage()) return context.getString(R.string.MediaOverviewActivity_image);
      return super.getFileTypeDescription(context, slide);
    }

    @Override
    void rebind() {
      thumbnailView.setImageResource(glideRequests, slide, false, false);
      super.rebind();
    }

    @Override
    void unbind() {
      thumbnailView.clear(glideRequests);
      super.unbind();
    }
  }

  private static final class AudioViewCallbacksAdapter implements AudioView.Callbacks {

    private final AudioItemListener audioItemListener;
    private final long              messageId;

    private AudioViewCallbacksAdapter(@NonNull AudioItemListener audioItemListener, long messageId) {
      this.audioItemListener = audioItemListener;
      this.messageId         = messageId;
    }

    @Override
    public void onPlay(@NonNull Uri audioUri, double progress) {
      audioItemListener.onPlay(audioUri, progress, messageId);
    }

    @Override
    public void onPause(@NonNull Uri audioUri) {
      audioItemListener.onPause(audioUri);
    }

    @Override
    public void onSeekTo(@NonNull Uri audioUri, double progress) {
      audioItemListener.onSeekTo(audioUri, progress);
    }

    @Override
    public void onStopAndReset(@NonNull Uri audioUri) {
      audioItemListener.onStopAndReset(audioUri);
    }

    @Override
    public void onSpeedChanged(float speed, boolean isPlaying) {
    }

    @Override
    public void onProgressUpdated(long durationMillis, long playheadMillis) {
    }
  }

  interface ItemClickListener {
    void onMediaClicked(@NonNull MediaTable.MediaRecord mediaRecord);

    void onMediaLongClicked(MediaTable.MediaRecord mediaRecord);
  }

  interface AudioItemListener {
    void onPlay(@NonNull Uri audioUri, double progress, long messageId);

    void onPause(@NonNull Uri audioUri);

    void onSeekTo(@NonNull Uri audioUri, double progress);

    void onStopAndReset(@NonNull Uri audioUri);

    void registerPlaybackStateObserver(@NonNull Observer<VoiceNotePlaybackState> observer);

    void unregisterPlaybackStateObserver(@NonNull Observer<VoiceNotePlaybackState> observer);
  }
}
