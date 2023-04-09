package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.asynclayoutinflater.appcompat.AsyncAppCompatFactory;
import androidx.asynclayoutinflater.view.AsyncLayoutInflater;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.util.concurrent.SerialExecutor;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * A class that can be used to pre-cache layouts. Usage flow:
 *
 * - At some point before you want to use the views, call {@link #cacheUntilLimit(int, ViewGroup, int)}.
 * - Later, use {@link #inflate(int, ViewGroup, boolean)}, which will prefer using cached views
 *   before inflating new ones.
 */
public class CachedInflater {

  private static final String TAG = Log.tag(CachedInflater.class);

  private final Context context;

  /**
   * Does *not* work with the application context.
   */
  public static CachedInflater from(@NonNull Context context) {
    return new CachedInflater(context);
  }

  private CachedInflater(@NonNull Context context) {
    this.context = context;
  }

  /**
   * Identical to {@link LayoutInflater#inflate(int, ViewGroup, boolean)}, but will prioritize
   * pulling a cached view first.
   */
  @MainThread
  @SuppressWarnings("unchecked")
  public <V extends View> V inflate(@LayoutRes int layoutRes, @Nullable ViewGroup parent, boolean attachToRoot) {
    View cached = ViewCache.getInstance().pull(layoutRes, context.getResources().getConfiguration());
    if (cached != null) {
      if (parent != null && attachToRoot) {
        parent.addView(cached);
      }
      return (V) cached;
    } else {
      return (V) LayoutInflater.from(context).inflate(layoutRes, parent, attachToRoot);
    }
  }

  /**
   * Will inflate as many views as necessary until the cache holds the amount you specify.
   */
  @MainThread
  public void cacheUntilLimit(@LayoutRes int layoutRes, @Nullable ViewGroup parent, int limit) {
    ViewCache.getInstance().cacheUntilLimit(context, layoutRes, parent, limit);
  }

  /**
   * Clears all cached views. This should be done if, for instance, the theme changes.
   */
  @MainThread
  public void clear() {
    Log.d(TAG, "Clearing view cache.");
    ViewCache.getInstance().clear();
  }

  private static class ViewCache {

    private static final ViewCache INSTANCE           = new ViewCache();
    private static final Executor  ENQUEUING_EXECUTOR = new SerialExecutor(SignalExecutors.BOUNDED);

    private final Map<Integer, List<View>> cache = new HashMap<>();

    private long  lastClearTime;
    private int   nightModeConfiguration;
    private float fontScale;
    private int   layoutDirection;

    static ViewCache getInstance() {
      return INSTANCE;
    }

    @MainThread
    void cacheUntilLimit(@NonNull Context context, @LayoutRes int layoutRes, @Nullable ViewGroup parent, int limit) {
      Configuration configuration                 = context.getResources().getConfiguration();
      int           currentNightModeConfiguration = ConfigurationUtil.getNightModeConfiguration(configuration);
      float         currentFontScale              = ConfigurationUtil.getFontScale(configuration);
      int           currentLayoutDirection        = configuration.getLayoutDirection();

      if (nightModeConfiguration != currentNightModeConfiguration ||
          fontScale              != currentFontScale              ||
          layoutDirection        != currentLayoutDirection)
      {
        clear();
        nightModeConfiguration = currentNightModeConfiguration;
        fontScale              = currentFontScale;
        layoutDirection        = currentLayoutDirection;
      }

      AsyncLayoutInflater inflater = new AsyncLayoutInflater(context, new AsyncAppCompatFactory());

      int  existingCount = Util.getOrDefault(cache, layoutRes, Collections.emptyList()).size();
      int  inflateCount  = Math.max(limit - existingCount, 0);
      long enqueueTime   = System.currentTimeMillis();

      // Calling AsyncLayoutInflator#inflate can block the calling thread when there's a large number of requests.
      // The method is annotated @UiThread, but unnecessarily so.
      ENQUEUING_EXECUTOR.execute(() -> {
        if (enqueueTime < lastClearTime) {
          Log.d(TAG, "Prefetch is no longer valid. Ignoring " + inflateCount + " inflates.");
          return;
        }

        AsyncLayoutInflater.OnInflateFinishedListener onInflateFinishedListener = (view, resId, p) -> {
          ThreadUtil.assertMainThread();
          if (enqueueTime < lastClearTime) {
            Log.d(TAG, "Prefetch is no longer valid. Ignoring.");
            return;
          }

          List<View> views = cache.get(resId);

          views = views == null ? new LinkedList<>() : views;
          views.add(view);

          cache.put(resId, views);
        };

        for (int i = 0; i < inflateCount; i++) {
          inflater.inflate(layoutRes, parent, onInflateFinishedListener);
        }
      });
    }

    @MainThread
    @Nullable View pull(@LayoutRes int layoutRes, @NonNull Configuration configuration) {
      if (this.nightModeConfiguration != ConfigurationUtil.getNightModeConfiguration(configuration) ||
          this.fontScale              != ConfigurationUtil.getFontScale(configuration)              ||
          this.layoutDirection        != configuration.getLayoutDirection())
      {
        clear();
        return null;
      }

      List<View> views = cache.get(layoutRes);
      return  views != null && !views.isEmpty() ? views.remove(0)
                                                : null;
    }

    @MainThread
    void clear() {
      lastClearTime = System.currentTimeMillis();
      cache.clear();
    }
  }
}
