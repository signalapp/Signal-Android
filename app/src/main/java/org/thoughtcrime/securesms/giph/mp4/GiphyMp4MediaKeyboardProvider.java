package org.thoughtcrime.securesms.giph.mp4;

import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.MediaKeyboardProvider;
import org.thoughtcrime.securesms.mms.GlideRequests;

/**
 * MediaKeyboardProvider for MP4 Gifs
 */
@SuppressWarnings("unused")
public final class GiphyMp4MediaKeyboardProvider implements MediaKeyboardProvider {

  private final GiphyMp4MediaKeyboardPagerAdapter pagerAdapter;

  private Controller controller;

  public GiphyMp4MediaKeyboardProvider(@NonNull FragmentActivity fragmentActivity, boolean isForMms) {
    pagerAdapter = new GiphyMp4MediaKeyboardPagerAdapter(fragmentActivity.getSupportFragmentManager(), isForMms);
  }

  @Override
  public int getProviderIconView(boolean selected) {
    if (selected) {
      return R.layout.giphy_mp4_keyboard_icon_selected;
    } else {
      return R.layout.giphy_mp4_keyboard_icon;
    }
  }

  @Override
  public void requestPresentation(@NonNull Presenter presenter, boolean isSoloProvider) {
    presenter.present(this, pagerAdapter, new GiphyMp4MediaKeyboardTabIconProvider(), null, null, null, 0);
  }

  @Override
  public void setController(@Nullable Controller controller) {
    this.controller = controller;
  }

  @Override
  public void setCurrentPosition(int currentPosition) {
    // ignored.
  }

  private static final class GiphyMp4MediaKeyboardPagerAdapter extends FragmentStatePagerAdapter {

    private final boolean isForMms;

    public GiphyMp4MediaKeyboardPagerAdapter(@NonNull FragmentManager fm, boolean isForMms) {
      super(fm, FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
      this.isForMms = isForMms;
    }

    @Override
    public @NonNull Fragment getItem(int position) {
      return GiphyMp4Fragment.create(isForMms);
    }

    @Override public int getCount() {
      return 1;
    }
  }

  private static final class GiphyMp4MediaKeyboardTabIconProvider implements TabIconProvider {
    @Override public void loadCategoryTabIcon(@NonNull GlideRequests glideRequests, @NonNull ImageView imageView, int index) { }
  }
}
