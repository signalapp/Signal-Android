package org.thoughtcrime.securesms.stickers;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.model.StickerPackRecord;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.adapter.SectionedRecyclerViewAdapter;
import org.thoughtcrime.securesms.util.adapter.StableIdGenerator;

import java.util.ArrayList;
import java.util.List;

final class StickerManagementAdapter extends SectionedRecyclerViewAdapter<String, StickerManagementAdapter.StickerSection> {

  private static final String TAG_YOUR_STICKERS    = "YourStickers";
  private static final String TAG_MESSAGE_STICKERS = "MessageStickers";
  private static final String TAG_BLESSED_STICKERS = "BlessedStickers";

  private final GlideRequests  glideRequests;
  private final EventListener  eventListener;

  private final List<StickerSection> sections = new ArrayList<StickerSection>(3) {{
    StickerSection yourStickers    = new StickerSection(TAG_YOUR_STICKERS,
                                                        R.string.StickerManagementAdapter_installed_stickers,
                                                        R.string.StickerManagementAdapter_no_stickers_installed,
                                                        new ArrayList<>(),
                                                        0);
    StickerSection messageStickers = new StickerSection(TAG_MESSAGE_STICKERS,
                                                        R.string.StickerManagementAdapter_stickers_you_received,
                                                        R.string.StickerManagementAdapter_stickers_from_incoming_messages_will_appear_here,
                                                        new ArrayList<>(),
                                                        yourStickers.size());

    add(yourStickers);
    add(messageStickers);
  }};

  StickerManagementAdapter(@NonNull GlideRequests glideRequests, @NonNull EventListener eventListener) {
    this.glideRequests = glideRequests;
    this.eventListener = eventListener;
  }

  @Override
  protected @NonNull List<StickerSection> getSections() {
    return sections;
  }

  @Override
  protected @NonNull RecyclerView.ViewHolder createHeaderViewHolder(@NonNull ViewGroup parent) {
    return new HeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.sticker_management_header_item, parent, false));
  }

  @Override
  protected @NonNull RecyclerView.ViewHolder createContentViewHolder(@NonNull ViewGroup parent) {
    return new StickerViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.sticker_management_sticker_item, parent, false));
  }

  @Override
  protected @NonNull RecyclerView.ViewHolder createEmptyViewHolder(@NonNull ViewGroup viewGroup) {
    return new EmptyViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.sticker_management_empty_item, viewGroup, false));
  }

  @Override
  public void bindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, @NonNull StickerSection section, int localPosition) {
    section.bindViewHolder(viewHolder, localPosition, glideRequests, eventListener);
  }

  @Override
  public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
    if (holder instanceof StickerViewHolder) {
      ((StickerViewHolder) holder).recycle();
    }
  }

  void setPackLists(@NonNull List<StickerPackRecord> installedPacks,
                    @NonNull List<StickerPackRecord> availablePacks,
                    @NonNull List<StickerPackRecord> blessedPacks)
  {
    StickerSection yourStickers    = new StickerSection(TAG_YOUR_STICKERS,
                                                        R.string.StickerManagementAdapter_installed_stickers,
                                                        R.string.StickerManagementAdapter_no_stickers_installed,
                                                        installedPacks,
                                                        0);
    StickerSection blessedStickers = new StickerSection(TAG_BLESSED_STICKERS,
                                                        R.string.StickerManagementAdapter_signal_artist_series,
                                                        0,
                                                        blessedPacks,
                                                        yourStickers.size());
    StickerSection messageStickers = new StickerSection(TAG_MESSAGE_STICKERS,
                                                        R.string.StickerManagementAdapter_stickers_you_received,
                                                        R.string.StickerManagementAdapter_stickers_from_incoming_messages_will_appear_here,
                                                        availablePacks,
                                                        yourStickers.size() + (blessedPacks.isEmpty() ? 0 : blessedStickers.size()));

    sections.clear();
    sections.add(yourStickers);

    if (!blessedPacks.isEmpty()) {
      sections.add(blessedStickers);
    }

    sections.add(messageStickers);

    notifyDataSetChanged();
  }

  public static class StickerSection extends SectionedRecyclerViewAdapter.Section<String> {

    private static final String STABLE_ID_HEADER = "header";
    private static final String STABLE_ID_TEXT   = "text";

    private final String                  tag;
    private final int                     titleResId;
    private final int                     emptyResId;
    private final List<StickerPackRecord> records;

    StickerSection(@NonNull String tag,
                   @StringRes int titleResId,
                   @StringRes int emptyResId,
                   @NonNull List<StickerPackRecord> records,
                   int offset)
    {
      super(offset);

      this.tag        = tag;
      this.titleResId = titleResId;
      this.emptyResId = emptyResId;
      this.records    = records;
    }

    @Override
    public boolean hasEmptyState() {
      return true;
    }

    @Override
    public int getContentSize() {
      return records.size();
    }

    @Override
    public long getItemId(@NonNull StableIdGenerator<String> idGenerator, int globalPosition) {
      int localPosition = getLocalPosition(globalPosition);

      if (localPosition == 0) {
        return idGenerator.getId(tag + "_" + STABLE_ID_HEADER);
      } else if (records.isEmpty()) {
        return idGenerator.getId(tag + "_" + STABLE_ID_TEXT);
      } else {
        return idGenerator.getId(records.get(localPosition - 1).getPackId());
      }
    }

    void bindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder,
                        int localPosition,
                        @NonNull GlideRequests glideRequests,
                        @NonNull EventListener eventListener)
    {
      if (localPosition == 0) {
        ((HeaderViewHolder) viewHolder).bind(titleResId);
      } else if (records.isEmpty()) {
        ((EmptyViewHolder) viewHolder).bind(emptyResId);
      } else {
        ((StickerViewHolder) viewHolder).bind(glideRequests, eventListener, records.get(localPosition - 1), localPosition == records.size());
      }
    }
  }

  static class StickerViewHolder extends RecyclerView.ViewHolder {

    private final ImageView cover;
    private final TextView  title;
    private final TextView  author;
    private final View      badge;
    private final View      divider;
    private final View      actionButton;
    private final ImageView actionButtonImage;
    private final View      shareButton;
    private final ImageView shareButtonImage;

    StickerViewHolder(@NonNull View itemView) {
      super(itemView);

      this.cover             = itemView.findViewById(R.id.sticker_management_cover);
      this.title             = itemView.findViewById(R.id.sticker_management_title);
      this.author            = itemView.findViewById(R.id.sticker_management_author);
      this.badge             = itemView.findViewById(R.id.sticker_management_blessed_badge);
      this.divider           = itemView.findViewById(R.id.sticker_management_divider);
      this.actionButton      = itemView.findViewById(R.id.sticker_management_action_button);
      this.actionButtonImage = itemView.findViewById(R.id.sticker_management_action_button_image);
      this.shareButton       = itemView.findViewById(R.id.sticker_management_share_button);
      this.shareButtonImage  = itemView.findViewById(R.id.sticker_management_share_button_image);
    }

    void bind(@NonNull GlideRequests glideRequests,
              @NonNull EventListener eventListener,
              @NonNull StickerPackRecord stickerPack,
              boolean lastInList)
    {
      title.setText(stickerPack.getTitle().or(itemView.getResources().getString(R.string.StickerManagementAdapter_untitled)));
      author.setText(stickerPack.getAuthor().or(itemView.getResources().getString(R.string.StickerManagementAdapter_unknown)));
      divider.setVisibility(lastInList ? View.GONE : View.VISIBLE);
      badge.setVisibility(BlessedPacks.contains(stickerPack.getPackId()) ? View.VISIBLE : View.GONE);

      glideRequests.load(new DecryptableUri(stickerPack.getCover().getUri()))
                   .transition(DrawableTransitionOptions.withCrossFade())
                   .into(cover);

      if (stickerPack.isInstalled()) {
        actionButtonImage.setImageResource(R.drawable.ic_x);
        actionButton.setOnClickListener(v -> eventListener.onStickerPackUninstallClicked(stickerPack.getPackId(), stickerPack.getPackKey()));

        shareButton.setVisibility(View.VISIBLE);
        shareButtonImage.setVisibility(View.VISIBLE);
        shareButton.setOnClickListener(v -> eventListener.onStickerPackShareClicked(stickerPack.getPackId(), stickerPack.getPackKey()));
      } else {
        actionButtonImage.setImageResource(R.drawable.ic_arrow_down);
        actionButton.setOnClickListener(v -> eventListener.onStickerPackInstallClicked(stickerPack.getPackId(), stickerPack.getPackKey()));

        shareButton.setVisibility(View.GONE);
        shareButtonImage.setVisibility(View.GONE);
        shareButton.setOnClickListener(null);
      }

      itemView.setOnClickListener(v -> eventListener.onStickerPackClicked(stickerPack.getPackId(), stickerPack.getPackKey()));
    }

    void recycle() {
      actionButton.setOnClickListener(null);
      shareButton.setOnClickListener(null);
      itemView.setOnClickListener(null);
    }
  }

  static class HeaderViewHolder extends RecyclerView.ViewHolder {

    private final TextView titleView;

    HeaderViewHolder(@NonNull View itemView) {
      super(itemView);

      this.titleView = itemView.findViewById(R.id.sticker_management_header);
    }

    void bind(@StringRes int title) {
      titleView.setText(title);
    }
  }

  static class EmptyViewHolder extends RecyclerView.ViewHolder {

    private final TextView text;

    EmptyViewHolder(@NonNull View itemView) {
      super(itemView);

      this.text = itemView.findViewById(R.id.sticker_management_empty_text);
    }

    void bind(@StringRes int title) {
      text.setText(title);
    }
  }

  interface EventListener {
    void onStickerPackClicked(@NonNull String packId, @NonNull String packKey);
    void onStickerPackUninstallClicked(@NonNull String packId, @NonNull String packKey);
    void onStickerPackInstallClicked(@NonNull String packId, @NonNull String packKey);
    void onStickerPackShareClicked(@NonNull String packId, @NonNull String packKey);
  }

  private static class NoSectionException extends IllegalStateException {}
}
