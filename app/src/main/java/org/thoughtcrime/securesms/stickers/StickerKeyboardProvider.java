package org.thoughtcrime.securesms.stickers;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.lifecycle.ViewModelProviders;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.MediaKeyboardProvider;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.model.StickerPackRecord;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.stickers.StickerKeyboardPageFragment.EventListener;
import org.thoughtcrime.securesms.stickers.StickerKeyboardRepository.PackListResult;
import org.thoughtcrime.securesms.util.Throttler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A provider to select stickers in the {@link org.thoughtcrime.securesms.components.emoji.MediaKeyboard}.
 */
public final class StickerKeyboardProvider implements MediaKeyboardProvider,
                                                MediaKeyboardProvider.AddObserver,
                                                StickerKeyboardPageFragment.EventListener
{
  private static final int UNSET = -1;

  private final Context              context;
  private final StickerEventListener eventListener;
  private final StickerPagerAdapter  pagerAdapter;
  private final Throttler            stickerThrottler;

  private Controller               controller;
  private Presenter                presenter;
  private boolean                  isSoloProvider;
  private StickerKeyboardViewModel viewModel;
  private int                      currentPosition;

  public StickerKeyboardProvider(@NonNull FragmentActivity activity,
                                 @NonNull StickerEventListener eventListener)
  {
    this.context          = activity;
    this.eventListener    = eventListener;
    this.pagerAdapter     = new StickerPagerAdapter(activity.getSupportFragmentManager(), this);
    this.stickerThrottler = new Throttler(100);
    this.currentPosition  = UNSET;

    initViewModel(activity);
  }

  @Override
  public int getProviderIconView(boolean selected) {
    if (selected) {
      return R.layout.sticker_keyboard_icon_selected;
    } else {
      return R.layout.sticker_keyboard_icon;
    }
  }

  @Override
  public void requestPresentation(@NonNull Presenter presenter, boolean isSoloProvider) {
    this.presenter      = presenter;
    this.isSoloProvider = isSoloProvider;

    PackListResult result = viewModel.getPacks().getValue();

    if (result != null) {
      present(presenter, result, false);
    }
  }

  @Override
  public void setController(@Nullable Controller controller) {
    this.controller = controller;
  }

  @Override
  public void onAddClicked() {
    eventListener.onStickerManagementClicked();
  }

  @Override
  public void onStickerSelected(@NonNull StickerRecord sticker) {
    stickerThrottler.publish(() -> eventListener.onStickerSelected(sticker));
  }

  @Override
  public void onStickerPopupStarted() {
    if (controller != null) {
      controller.setViewPagerEnabled(false);
    }
  }

  @Override
  public void onStickerPopupEnded() {
    if (controller != null) {
      controller.setViewPagerEnabled(true);
    }
  }

  @Override
  public void setCurrentPosition(int currentPosition) {
    this.currentPosition = currentPosition;
  }

  private void initViewModel(@NonNull FragmentActivity activity) {
    StickerKeyboardRepository repository = new StickerKeyboardRepository(DatabaseFactory.getStickerDatabase(activity));
    viewModel = ViewModelProviders.of(activity, new StickerKeyboardViewModel.Factory(activity.getApplication(), repository)).get(StickerKeyboardViewModel.class);

    viewModel.getPacks().observe(activity, result -> {
      if (result == null) return;

      int previousCount = pagerAdapter.getCount();

      pagerAdapter.setPacks(result.getPacks());

      if (presenter != null) {
        present(presenter, result, previousCount != pagerAdapter.getCount());
      }
    });
  }

  private void present(@NonNull Presenter presenter, @NonNull PackListResult result, boolean calculateStartingIndex) {
    if (result.getPacks().isEmpty() && presenter.isVisible()) {
      context.startActivity(StickerManagementActivity.getIntent(context));
      presenter.requestDismissal();
      return;
    }

    int startingIndex = currentPosition;

    if (calculateStartingIndex || startingIndex == UNSET) {
      startingIndex = !result.hasRecents() && result.getPacks().size() > 0 ? 1 : 0;
    }

    presenter.present(this, pagerAdapter, new IconProvider(context, result.getPacks()), null, this, null, startingIndex);

    if (isSoloProvider && result.getPacks().isEmpty()) {
      context.startActivity(StickerManagementActivity.getIntent(context));
    }
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    return obj instanceof StickerKeyboardProvider;
  }

  private static class StickerPagerAdapter extends FragmentStatePagerAdapter {

    private final List<StickerPackRecord> packs;
    private final Map<String, Integer>    itemPositions;
    private final EventListener           eventListener;

    public StickerPagerAdapter(@NonNull FragmentManager fm, @NonNull EventListener eventListener) {
      super(fm);
      this.eventListener = eventListener;
      this.packs         = new ArrayList<>();
      this.itemPositions = new HashMap<>();
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
      String packId = ((StickerKeyboardPageFragment) object).getPackId();

      if (itemPositions.containsKey(packId)) {
        //noinspection ConstantConditions
        return itemPositions.get(packId);
      }

      return POSITION_NONE;
    }

    @Override
    public Fragment getItem(int i) {
      StickerKeyboardPageFragment fragment;

      if (i == 0) {
        fragment = StickerKeyboardPageFragment.newInstance(StickerKeyboardPageFragment.RECENT_PACK_ID);
      } else {
        StickerPackRecord pack = packs.get(i - 1);
        fragment = StickerKeyboardPageFragment.newInstance(pack.getPackId());
      }

      fragment.setEventListener(eventListener);

      return fragment;
    }

    @Override
    public int getCount() {
      return packs.isEmpty() ? 0 : packs.size() + 1;
    }

    void setPacks(@NonNull List<StickerPackRecord> packs) {
      itemPositions.clear();

      if (areListsEqual(this.packs, packs)) {
        itemPositions.put(StickerKeyboardPageFragment.RECENT_PACK_ID, 0);
        for (int i = 0; i < packs.size(); i++) {
          itemPositions.put(packs.get(i).getPackId(), i + 1);
        }
      }

      this.packs.clear();
      this.packs.addAll(packs);

      notifyDataSetChanged();
    }

    boolean areListsEqual(@NonNull List<StickerPackRecord> a, @NonNull List<StickerPackRecord> b) {
      if (a.size() != b.size()) return false;

      for (int i = 0; i < a.size(); i++) {
        if (!a.get(i).equals(b.get(i))) {
          return false;
        }
      }

      return true;
    }
  }

  private static class IconProvider implements TabIconProvider {

    private final Context                 context;
    private final List<StickerPackRecord> packs;

    private IconProvider(@NonNull Context context, List<StickerPackRecord> packs) {
      this.context    = context;
      this.packs      = packs;
    }

    @Override
    public void loadCategoryTabIcon(@NonNull GlideRequests glideRequests, @NonNull ImageView imageView, int index) {
      if (index == 0) {
        Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_recent_20);
        imageView.setImageDrawable(icon);
      } else {
        Uri uri = packs.get(index - 1).getCover().getUri();

        glideRequests.load(new DecryptableStreamUriLoader.DecryptableUri(uri))
                     .into(imageView);
      }
    }
  }

  public interface StickerEventListener {
    void onStickerSelected(@NonNull StickerRecord sticker);
    void onStickerManagementClicked();
  }
}
