package org.thoughtcrime.securesms.components.emoji;

import android.widget.ImageView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;

import org.thoughtcrime.securesms.mms.GlideRequests;

public interface MediaKeyboardProvider {
  @LayoutRes int getProviderIconView(boolean selected);
  /** @return True if the click was handled with provider-specific logic, otherwise false */
  void requestPresentation(@NonNull Presenter presenter, boolean isSoloProvider);
  void setController(@Nullable Controller controller);
  void setCurrentPosition(int currentPosition);

  interface BackspaceObserver {
    void onBackspaceClicked();
  }

  interface AddObserver {
    void onAddClicked();
  }

  interface SearchObserver {
    void onSearchOpened();
    void onSearchClosed();
    void onSearchChanged(@NonNull String query);
  }

  interface Controller {
    void setViewPagerEnabled(boolean enabled);
  }

  interface Presenter {
    void present(@NonNull MediaKeyboardProvider provider,
                 @NonNull PagerAdapter pagerAdapter,
                 @NonNull TabIconProvider iconProvider,
                 @Nullable BackspaceObserver backspaceObserver,
                 @Nullable AddObserver addObserver,
                 @Nullable SearchObserver searchObserver,
                 int startingIndex);
    int getCurrentPosition();
    void requestDismissal();
    boolean isVisible();
  }

  interface TabIconProvider {
    void loadCategoryTabIcon(@NonNull GlideRequests glideRequests, @NonNull ImageView imageView, int index);
  }
}
