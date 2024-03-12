package org.thoughtcrime.securesms.stickers;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.database.model.StickerPackRecord;
import org.thoughtcrime.securesms.glide.cache.ApngOptions;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.util.DrawableUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.adapter.SectionedRecyclerViewAdapter;
import org.thoughtcrime.securesms.util.adapter.StableIdGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class StickerManagementAdapter extends SectionedRecyclerViewAdapter<String, StickerManagementAdapter.StickerSection> {

  private static final String TAG_YOUR_STICKERS    = "YourStickers";
  private static final String TAG_MESSAGE_STICKERS = "MessageStickers";
  private static final String TAG_BLESSED_STICKERS = "BlessedStickers";

  private final RequestManager requestManager;
  private final EventListener  eventListener;
  private final boolean        allowApngAnimation;

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

  StickerManagementAdapter(@NonNull RequestManager requestManager, @NonNull EventListener eventListener, boolean allowApngAnimation) {
    this.requestManager     = requestManager;
    this.eventListener      = eventListener;
    this.allowApngAnimation = allowApngAnimation;
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
    section.bindViewHolder(viewHolder, localPosition, requestManager, eventListener, allowApngAnimation);
  }

  @Override
  public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
    if (holder instanceof StickerViewHolder) {
      ((StickerViewHolder) holder).recycle();
    }
  }

  boolean onMove(int start, int end) {
    StickerSection installed = sections.get(0);

    if (!installed.isContent(start)) {
      return false;
    }

    if (!installed.isContent(end)) {
      return false;
    }

    installed.swap(start, end);
    notifyItemMoved(start, end);
    return true;
  }

  boolean isMovable(int position) {
    return sections.get(0).isContent(position);
  }

  @NonNull List<StickerPackRecord> getInstalledPacksInOrder() {
    return sections.get(0).records;
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
                        @NonNull RequestManager requestManager,
                        @NonNull EventListener eventListener,
                        boolean allowApngAnimation)
    {
      if (localPosition == 0) {
        ((HeaderViewHolder) viewHolder).bind(titleResId);
      } else if (records.isEmpty()) {
        ((EmptyViewHolder) viewHolder).bind(emptyResId);
      } else {
        ((StickerViewHolder) viewHolder).bind(requestManager, eventListener, records.get(localPosition - 1), localPosition == records.size(), allowApngAnimation);
      }
    }

    void swap(int start, int end) {
      int localStart = getLocalPosition(start) - 1;
      int localEnd   = getLocalPosition(end) - 1;

      if (localStart < localEnd) {
        for (int i = localStart; i < localEnd; i++) {
          Collections.swap(records, i, i + 1);
        }
      } else {
        for (int i = localStart; i > localEnd; i--) {
          Collections.swap(records, i, i - 1);
        }
      }
    }
  }

  static class StickerViewHolder extends RecyclerView.ViewHolder {

    private final ImageView     cover;
    private final EmojiTextView title;
    private final TextView      author;
    private final View          divider;
    private final View          actionButton;
    private final ImageView     actionButtonImage;
    private final View          shareButton;
    private final ImageView     shareButtonImage;
    private final CharSequence  blessedBadge;

    StickerViewHolder(@NonNull View itemView) {
      super(itemView);

      this.cover             = itemView.findViewById(R.id.sticker_management_cover);
      this.title             = itemView.findViewById(R.id.sticker_management_title);
      this.author            = itemView.findViewById(R.id.sticker_management_author);
      this.divider           = itemView.findViewById(R.id.sticker_management_divider);
      this.actionButton      = itemView.findViewById(R.id.sticker_management_action_button);
      this.actionButtonImage = itemView.findViewById(R.id.sticker_management_action_button_image);
      this.shareButton       = itemView.findViewById(R.id.sticker_management_share_button);
      this.shareButtonImage  = itemView.findViewById(R.id.sticker_management_share_button_image);
      this.blessedBadge      = buildBlessedBadge(itemView.getContext());
    }

    void bind(@NonNull RequestManager requestManager,
              @NonNull EventListener eventListener,
              @NonNull StickerPackRecord stickerPack,
              boolean lastInList,
              boolean allowApngAnimation)
    {
      SpannableStringBuilder titleBuilder = new SpannableStringBuilder(stickerPack.getTitle().orElse(itemView.getResources().getString(R.string.StickerManagementAdapter_untitled)));
      if (BlessedPacks.contains(stickerPack.getPackId())) {
        titleBuilder.append(blessedBadge);
      }

      title.setText(titleBuilder);
      author.setText(stickerPack.getAuthor().orElse(itemView.getResources().getString(R.string.StickerManagementAdapter_unknown)));
      divider.setVisibility(lastInList ? View.GONE : View.VISIBLE);

      requestManager.load(new DecryptableUri(stickerPack.getCover().getUri()))
                   .transition(DrawableTransitionOptions.withCrossFade())
                   .fitCenter()
                   .set(ApngOptions.ANIMATE, allowApngAnimation)
                   .into(cover);

      if (stickerPack.isInstalled()) {
        actionButtonImage.setImageResource(R.drawable.ic_x);
        actionButton.setOnClickListener(v -> eventListener.onStickerPackUninstallClicked(stickerPack.getPackId(), stickerPack.getPackKey()));

        shareButton.setVisibility(View.VISIBLE);
        shareButtonImage.setVisibility(View.VISIBLE);
        shareButton.setOnClickListener(v -> eventListener.onStickerPackShareClicked(stickerPack.getPackId(), stickerPack.getPackKey()));
      } else {
        actionButtonImage.setImageResource(R.drawable.symbol_arrow_down_24);
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

    private static @NonNull CharSequence buildBlessedBadge(@NonNull Context context) {
      SpannableString badgeSpan = new SpannableString("  ");
      Drawable        badge     = ContextCompat.getDrawable(context, R.drawable.symbol_check_circle_fill_24);

      badge.setBounds(0, 0, ViewUtil.dpToPx(18), ViewUtil.dpToPx(18));
      DrawableUtil.tint(badge, ContextCompat.getColor(context, R.color.core_ultramarine));
      badgeSpan.setSpan(new ImageSpan(badge), 1, badgeSpan.length(), 0);

      return badgeSpan;
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
