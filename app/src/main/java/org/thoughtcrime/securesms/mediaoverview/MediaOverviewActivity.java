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
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AnimatingToggle;
import org.thoughtcrime.securesms.components.BoldSelectionTabItem;
import org.thoughtcrime.securesms.components.ControllableTabLayout;
import org.thoughtcrime.securesms.database.MediaTable;
import org.thoughtcrime.securesms.database.MediaTable.Sorting;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.loaders.MediaLoader;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.signal.core.util.concurrent.SimpleTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for displaying media attachments in-app
 */
public final class MediaOverviewActivity extends PassphraseRequiredActivity {

  private static final String THREAD_ID_EXTRA = "thread_id";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  private Toolbar                toolbar;
  private ControllableTabLayout  tabLayout;
  private ViewPager              viewPager;
  private TextView               sortOrder;
  private View                   sortOrderArrow;
  private Sorting                currentSorting;
  private Boolean                currentDetailLayout;
  private MediaOverviewViewModel model;
  private AnimatingToggle        displayToggle;
  private View                   viewGrid;
  private View                   viewDetail;
  private long                   threadId;

  public static Intent forThread(@NonNull Context context, long threadId) {
    Intent intent = new Intent(context, MediaOverviewActivity.class);
    intent.putExtra(MediaOverviewActivity.THREAD_ID_EXTRA, threadId);
    return intent;
  }

  public static Intent forAll(@NonNull Context context) {
    return forThread(context, MediaTable.ALL_THREADS);
  }

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle bundle, boolean ready) {
    setContentView(R.layout.media_overview_activity);

    initializeResources();
    initializeToolbar();

    boolean allThreads = threadId == MediaTable.ALL_THREADS;

    BoldSelectionTabItem.registerListeners(tabLayout);
    fillTabLayoutIfFits(tabLayout);
    tabLayout.setupWithViewPager(viewPager);
    viewPager.setAdapter(new MediaOverviewPagerAdapter(getSupportFragmentManager()));

    model = MediaOverviewViewModel.getMediaOverviewViewModel(this);
    model.setSortOrder(allThreads ? Sorting.Largest : Sorting.Newest);
    model.setDetailLayout(allThreads);
    model.getSortOrder().observe(this, this::setSorting);
    model.getDetailLayout().observe(this, this::setDetailLayout);

    sortOrder.setOnClickListener(this::showSortOrderDialog);
    sortOrderArrow.setOnClickListener(this::showSortOrderDialog);

    displayToggle.setOnClickListener(v -> setDetailLayout(!currentDetailLayout));

    viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
      @Override
      public void onPageSelected(int position) {
        boolean gridToggleEnabled = allowGridSelectionOnPage(position);
        displayToggle.animate()
                     .alpha(gridToggleEnabled ? 1 : 0)
                     .start();
        displayToggle.setEnabled(gridToggleEnabled);
      }
    });

    viewPager.setCurrentItem(allThreads ? 3 : 0);
  }

  private static boolean allowGridSelectionOnPage(int page) {
    return page == 0;
  }

  private void setSorting(@NonNull Sorting sorting) {
    if (currentSorting == sorting) return;

    sortOrder.setText(sortingToString(sorting));
    currentSorting = sorting;
    model.setSortOrder(sorting);
  }

  private static @StringRes int sortingToString(@NonNull Sorting sorting) {
    switch (sorting) {
      case Oldest  : return R.string.MediaOverviewActivity_Oldest;
      case Newest  : return R.string.MediaOverviewActivity_Newest;
      case Largest : return R.string.MediaOverviewActivity_Storage_used;
      default      : throw new AssertionError();
    }
  }

  private void setDetailLayout(@NonNull Boolean detailLayout) {
    if (currentDetailLayout == detailLayout) return;

    currentDetailLayout = detailLayout;
    model.setDetailLayout(detailLayout);
    displayToggle.display(detailLayout ? viewGrid : viewDetail);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }

    return false;
  }

  private void initializeResources() {
    Intent intent   = getIntent();
    long   threadId = intent.getLongExtra(THREAD_ID_EXTRA, Long.MIN_VALUE);

    if (threadId == Long.MIN_VALUE) throw new AssertionError();

    this.viewPager      = findViewById(R.id.pager);
    this.toolbar        = findViewById(R.id.toolbar);
    this.tabLayout      = findViewById(R.id.tab_layout);
    this.sortOrder      = findViewById(R.id.sort_order);
    this.sortOrderArrow = findViewById(R.id.sort_order_arrow);
    this.displayToggle  = findViewById(R.id.grid_list_toggle);
    this.viewDetail     = findViewById(R.id.view_detail);
    this.viewGrid       = findViewById(R.id.view_grid);
    this.threadId       = threadId;
  }

  private void initializeToolbar() {
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    if (threadId == MediaTable.ALL_THREADS) {
      getSupportActionBar().setTitle(R.string.MediaOverviewActivity_All_storage_use);
    } else {
      SimpleTask.run(() -> SignalDatabase.threads().getRecipientForThreadId(threadId),
        (recipient) -> {
          if (recipient != null) {
            getSupportActionBar().setTitle(recipient.getDisplayName(this));
            recipient.live().observe(this, r -> getSupportActionBar().setTitle(r.getDisplayName(this)));
          }
        }
      );
    }
  }

  public void onEnterMultiSelect() {
    tabLayout.setEnabled(false);
    viewPager.setEnabled(false);
    toolbar.setVisibility(View.INVISIBLE);
  }

  public void onExitMultiSelect() {
    tabLayout.setEnabled(true);
    viewPager.setEnabled(true);
    toolbar.setVisibility(View.VISIBLE);
  }

  private void showSortOrderDialog(View v) {
    new MaterialAlertDialogBuilder(MediaOverviewActivity.this)
      .setTitle(R.string.MediaOverviewActivity_Sort_by)
      .setSingleChoiceItems(R.array.MediaOverviewActivity_Sort_by,
        currentSorting.ordinal(),
        (dialog, item) -> {
          setSorting(Sorting.values()[item]);
          dialog.dismiss();
        })
      .create()
      .show();
  }

  private static void fillTabLayoutIfFits(@NonNull TabLayout tabLayout) {
    tabLayout.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      int totalWidth = 0;
      int maxWidth   = 0;
      ViewGroup tabs = (ViewGroup) tabLayout.getChildAt(0);

      for (int i = 0; i < tabLayout.getTabCount(); i++) {
        int tabWidth = tabs.getChildAt(i).getWidth();
        totalWidth += tabWidth;
        maxWidth = Math.max(maxWidth, tabWidth);
      }

      int viewWidth = right - left;
      if (totalWidth < viewWidth) {
        tabLayout.setTabMode(TabLayout.MODE_FIXED);
      }
    });
  }

  private class MediaOverviewPagerAdapter extends FragmentStatePagerAdapter {

    private final List<Pair<MediaLoader.MediaType, CharSequence>> pages;

    MediaOverviewPagerAdapter(FragmentManager fragmentManager) {
      super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);

      pages = new ArrayList<>(4);
      pages.add(new Pair<>(MediaLoader.MediaType.GALLERY,  getString(R.string.MediaOverviewActivity_Media)));
      pages.add(new Pair<>(MediaLoader.MediaType.DOCUMENT, getString(R.string.MediaOverviewActivity_Files)));
      pages.add(new Pair<>(MediaLoader.MediaType.AUDIO,    getString(R.string.MediaOverviewActivity_Audio)));
      pages.add(new Pair<>(MediaLoader.MediaType.ALL,      getString(R.string.MediaOverviewActivity_All)));
    }

    @Override
    public  @NonNull Fragment getItem(int position) {
      MediaOverviewPageFragment.GridMode gridMode = allowGridSelectionOnPage(position)
                                                       ? MediaOverviewPageFragment.GridMode.FOLLOW_MODEL
                                                       : MediaOverviewPageFragment.GridMode.FIXED_DETAIL;

      return MediaOverviewPageFragment.newInstance(threadId, pages.get(position).first(), gridMode);
    }

    @Override
    public int getCount() {
      return pages.size();
    }

    @Override
    public CharSequence getPageTitle(int position) {
      return pages.get(position).second();
    }
  }
}
