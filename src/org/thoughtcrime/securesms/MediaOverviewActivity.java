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
package org.thoughtcrime.securesms;

import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.codewaves.stickyheadergrid.StickyHeaderGridLayoutManager;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter;
import org.thoughtcrime.securesms.database.loaders.BucketedThreadMediaLoader;
import org.thoughtcrime.securesms.database.loaders.BucketedThreadMediaLoader.BucketedThreadMedia;
import org.thoughtcrime.securesms.database.loaders.ThreadMediaLoader;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Locale;

/**
 * Activity for displaying media attachments in-app
 */
public class MediaOverviewActivity extends PassphraseRequiredActionBarActivity  {
  private final static String TAG = MediaOverviewActivity.class.getSimpleName();

  public static final String ADDRESS_EXTRA   = "address";

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private Toolbar      toolbar;
  private TabLayout    tabLayout;
  private ViewPager    viewPager;
  private MasterSecret masterSecret;
  private Recipient    recipient;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle bundle, @NonNull MasterSecret masterSecret) {
    setContentView(R.layout.media_overview_activity);
    this.masterSecret = masterSecret;

    initializeResources();
    initializeToolbar();

    this.tabLayout.setupWithViewPager(viewPager);
    this.viewPager.setAdapter(new MediaOverviewPagerAdapter(getSupportFragmentManager()));
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case android.R.id.home: finish(); return true;
    }

    return false;
  }

  private void initializeResources() {
    Address address = getIntent().getParcelableExtra(ADDRESS_EXTRA);

    this.viewPager = ViewUtil.findById(this, R.id.pager);
    this.toolbar   = ViewUtil.findById(this, R.id.toolbar);
    this.tabLayout = ViewUtil.findById(this, R.id.tab_layout);
    this.recipient = Recipient.from(this, address, true);
  }

  private void initializeToolbar() {
    setSupportActionBar(this.toolbar);
    getSupportActionBar().setTitle(recipient.toShortString());
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    this.recipient.addListener(recipient -> getSupportActionBar().setTitle(recipient.toShortString()));
  }

  private class MediaOverviewPagerAdapter extends FragmentStatePagerAdapter {

    MediaOverviewPagerAdapter(FragmentManager fragmentManager) {
      super(fragmentManager);
    }

    @Override
    public Fragment getItem(int position) {
      Fragment fragment;

      if      (position == 0) fragment = new MediaOverviewGalleryFragment();
      else if (position == 1) fragment = new MediaOverviewDocumentsFragment();
      else                    throw new AssertionError();

      Bundle args = new Bundle();
      args.putString(MediaOverviewGalleryFragment.ADDRESS_EXTRA, recipient.getAddress().serialize());
      args.putParcelable(MediaOverviewGalleryFragment.MASTER_SECRET_EXTRA, masterSecret);
      args.putSerializable(MediaOverviewGalleryFragment.LOCALE_EXTRA, dynamicLanguage.getCurrentLocale());

      fragment.setArguments(args);

      return fragment;
    }

    @Override
    public int getCount() {
      return 2;
    }

    @Override
    public CharSequence getPageTitle(int position) {
      if      (position == 0) return getString(R.string.MediaOverviewActivity_Media);
      else if (position == 1) return getString(R.string.MediaOverviewActivity_Documents);
      else                    throw new AssertionError();
    }
  }

  public static abstract class MediaOverviewFragment<T> extends Fragment implements LoaderManager.LoaderCallbacks<T> {

    public static final String ADDRESS_EXTRA       = "address";
    public static final String MASTER_SECRET_EXTRA = "master_secret";
    public static final String LOCALE_EXTRA        = "locale_extra";

    protected TextView     noMedia;
    protected Recipient    recipient;
    protected MasterSecret masterSecret;
    protected RecyclerView recyclerView;
    protected Locale       locale;

    @Override
    public void onCreate(Bundle bundle) {
      super.onCreate(bundle);

      String       address      = getArguments().getString(ADDRESS_EXTRA);
      MasterSecret masterSecret = getArguments().getParcelable(MASTER_SECRET_EXTRA);
      Locale       locale       = (Locale)getArguments().getSerializable(LOCALE_EXTRA);

      if (address == null)      throw new AssertionError();
      if (masterSecret == null) throw new AssertionError();
      if (locale == null)       throw new AssertionError();

      this.recipient    = Recipient.from(getContext(), Address.fromSerialized(address), true);
      this.masterSecret = masterSecret;
      this.locale       = locale;

      getLoaderManager().initLoader(0, null, this);
    }
  }

  public static class MediaOverviewGalleryFragment extends MediaOverviewFragment<BucketedThreadMedia> {

    private StickyHeaderGridLayoutManager gridManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      View view = inflater.inflate(R.layout.media_overview_gallery_fragment, container, false);

      this.recyclerView = ViewUtil.findById(view, R.id.media_grid);
      this.noMedia      = ViewUtil.findById(view, R.id.no_images);
      this.gridManager  = new StickyHeaderGridLayoutManager(getResources().getInteger(R.integer.media_overview_cols));

      this.recyclerView.setAdapter(new MediaGalleryAdapter(getContext(), masterSecret, new BucketedThreadMedia(getContext()), locale, recipient.getAddress()));
      this.recyclerView.setLayoutManager(gridManager);
      this.recyclerView.setHasFixedSize(true);

      return view;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
      super.onConfigurationChanged(newConfig);
      if (gridManager != null) {
        this.gridManager = new StickyHeaderGridLayoutManager(getResources().getInteger(R.integer.media_overview_cols));
        this.recyclerView.setLayoutManager(gridManager);
      }
    }

    @Override
    public Loader<BucketedThreadMedia> onCreateLoader(int i, Bundle bundle) {
      return new BucketedThreadMediaLoader(getContext(), masterSecret, recipient.getAddress());
    }

    @Override
    public void onLoadFinished(Loader<BucketedThreadMedia> loader, BucketedThreadMedia bucketedThreadMedia) {
      ((MediaGalleryAdapter) recyclerView.getAdapter()).setMedia(bucketedThreadMedia);
      ((MediaGalleryAdapter) recyclerView.getAdapter()).notifyAllSectionsDataSetChanged();

      noMedia.setVisibility(recyclerView.getAdapter().getItemCount() > 0 ? View.GONE : View.VISIBLE);
      getActivity().invalidateOptionsMenu();
    }

    @Override
    public void onLoaderReset(Loader<BucketedThreadMedia> cursorLoader) {
      ((MediaGalleryAdapter) recyclerView.getAdapter()).setMedia(new BucketedThreadMedia(getContext()));
    }
  }

  public static class MediaOverviewDocumentsFragment extends MediaOverviewFragment<Cursor> {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      View                  view    = inflater.inflate(R.layout.media_overview_documents_fragment, container, false);
      MediaDocumentsAdapter adapter = new MediaDocumentsAdapter(getContext(), masterSecret, null, locale);

      this.recyclerView  = ViewUtil.findById(view, R.id.recycler_view);
      this.noMedia       = ViewUtil.findById(view, R.id.no_documents);

      this.recyclerView.setAdapter(adapter);
      this.recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
      this.recyclerView.addItemDecoration(new StickyHeaderDecoration(adapter, false, true));
      this.recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));

      return view;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
      return new ThreadMediaLoader(getContext(), masterSecret, recipient.getAddress(), false);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
      ((CursorRecyclerViewAdapter)this.recyclerView.getAdapter()).changeCursor(data);
      getActivity().invalidateOptionsMenu();

      this.noMedia.setVisibility(data.getCount() > 0 ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
      ((CursorRecyclerViewAdapter)this.recyclerView.getAdapter()).changeCursor(null);
      getActivity().invalidateOptionsMenu();
    }
  }
}
