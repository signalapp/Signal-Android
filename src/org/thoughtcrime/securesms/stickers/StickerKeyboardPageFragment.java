package org.thoughtcrime.securesms.stickers;

import androidx.lifecycle.ViewModelProviders;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.stickers.StickerKeyboardPageAdapter.StickerKeyboardPageViewHolder;
import org.whispersystems.libsignal.util.Pair;

/**
 * An individual page of stickers in the {@link StickerKeyboardProvider}.
 */
public final class StickerKeyboardPageFragment extends Fragment implements StickerKeyboardPageAdapter.EventListener,
                                                                           StickerRolloverTouchListener.RolloverStickerRetriever
{

  private static final String TAG = Log.tag(StickerKeyboardPageFragment.class);

  private static final String KEY_PACK_ID = "pack_id";

  public static final String RECENT_PACK_ID = StickerKeyboardPageViewModel.RECENT_PACK_ID;

  private RecyclerView               list;
  private StickerKeyboardPageAdapter adapter;
  private GridLayoutManager          layoutManager;

  private StickerKeyboardPageViewModel viewModel;
  private EventListener                eventListener;
  private StickerRolloverTouchListener listTouchListener;

  private String                       packId;

  public static StickerKeyboardPageFragment newInstance(@NonNull String packId) {
    Bundle args = new Bundle();
    args.putString(KEY_PACK_ID, packId);

    StickerKeyboardPageFragment fragment = new StickerKeyboardPageFragment();
    fragment.setArguments(args);
    fragment.packId = packId;

    return fragment;
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.sticker_keyboard_page, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    GlideRequests glideRequests = GlideApp.with(this);

    this.list              = view.findViewById(R.id.sticker_keyboard_list);
    this.adapter           = new StickerKeyboardPageAdapter(glideRequests, this);
    this.layoutManager     = new GridLayoutManager(requireContext(), 2);
    this.listTouchListener = new StickerRolloverTouchListener(requireContext(), glideRequests, eventListener, this);
    this.packId            = getArguments().getString(KEY_PACK_ID);

    list.setLayoutManager(layoutManager);
    list.setAdapter(adapter);
    list.addOnItemTouchListener(listTouchListener);

    initViewModel(packId);
    onScreenWidthChanged(getScreenWidth());
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    onScreenWidthChanged(getScreenWidth());
  }

  @Override
  public void onStickerClicked(@NonNull StickerRecord sticker) {
    if (eventListener != null) {
      eventListener.onStickerSelected(sticker);
    }
  }

  @Override
  public void onStickerLongClicked(@NonNull View targetView) {
    if (listTouchListener != null) {
      listTouchListener.enterHoverMode(list, targetView);
    }
  }

  @Override
  public @Nullable Pair<Object, String> getStickerDataFromView(@NonNull View view) {
    if (list != null) {
      StickerKeyboardPageViewHolder holder = (StickerKeyboardPageViewHolder) list.getChildViewHolder(view);
      if (holder != null && holder.getCurrentSticker() != null) {
        return new Pair<>(new DecryptableStreamUriLoader.DecryptableUri(holder.getCurrentSticker().getUri()),
                          holder.getCurrentSticker().getEmoji());
      }
    }
    return null;
  }

  public void setEventListener(@NonNull EventListener eventListener) {
    this.eventListener = eventListener;
  }

  public @NonNull String getPackId() {
    return packId;
  }

  private void initViewModel(@NonNull String packId) {
    StickerKeyboardRepository repository = new StickerKeyboardRepository(DatabaseFactory.getStickerDatabase(requireContext()));
    viewModel = ViewModelProviders.of(this, new StickerKeyboardPageViewModel.Factory(requireActivity().getApplication(), repository)).get(StickerKeyboardPageViewModel.class);

    viewModel.getStickers(packId).observe(getViewLifecycleOwner(), stickerRecords -> {
      if (stickerRecords == null) return;

      adapter.setStickers(stickerRecords, calculateStickerSize(getScreenWidth()));
    });
  }

  private void onScreenWidthChanged(@Px int newWidth) {
    if (layoutManager != null) {
      layoutManager.setSpanCount(calculateColumnCount(newWidth));
      adapter.setStickerSize(calculateStickerSize(newWidth));
    }
  }

  private int getScreenWidth() {
    Point size = new Point();
    requireActivity().getWindowManager().getDefaultDisplay().getSize(size);
    return size.x;
  }

  private int calculateColumnCount(@Px int screenWidth) {
    float modifier = getResources().getDimensionPixelOffset(R.dimen.sticker_page_item_padding);
    float divisor  = getResources().getDimensionPixelOffset(R.dimen.sticker_page_item_divisor);
    return (int) ((screenWidth - modifier) / divisor);
  }

  private int calculateStickerSize(@Px int screenWidth) {
    float multiplier  = getResources().getDimensionPixelOffset(R.dimen.sticker_page_item_multiplier);
    int   columnCount = calculateColumnCount(screenWidth);

    return (int) ((screenWidth - ((columnCount + 1) * multiplier)) / columnCount);
  }

  interface EventListener extends StickerRolloverTouchListener.RolloverEventListener {
    void onStickerSelected(@NonNull StickerRecord sticker);
  }
}
