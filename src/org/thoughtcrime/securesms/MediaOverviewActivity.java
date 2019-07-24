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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.codewaves.stickyheadergrid.StickyHeaderGridLayoutManager;

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter;
import org.thoughtcrime.securesms.database.MediaDatabase;
import org.thoughtcrime.securesms.database.loaders.BucketedThreadMediaLoader;
import org.thoughtcrime.securesms.database.loaders.BucketedThreadMediaLoader.BucketedThreadMedia;
import org.thoughtcrime.securesms.database.loaders.ThreadMediaLoader;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.AttachmentUtil;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Activity for displaying media attachments in-app
 */
public class MediaOverviewActivity extends PassphraseRequiredActionBarActivity {

  @SuppressWarnings("unused")
  private final static String TAG = MediaOverviewActivity.class.getSimpleName();

  public static final String ADDRESS_EXTRA   = "address";

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private Toolbar      toolbar;
  private TabLayout    tabLayout;
  private ViewPager    viewPager;
  private Recipient    recipient;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle bundle, boolean ready) {
    setContentView(R.layout.media_overview_activity);

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
    this.recipient.addListener(recipient -> {
      Util.runOnMain(() -> getSupportActionBar().setTitle(recipient.toShortString()));
    });
  }

  public void onEnterMultiSelect() {
    tabLayout.setEnabled(false);
    viewPager.setEnabled(false);
  }

  public void onExitMultiSelect() {
    tabLayout.setEnabled(true);
    viewPager.setEnabled(true);
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

    public static final String ADDRESS_EXTRA = "address";
    public static final String LOCALE_EXTRA  = "locale_extra";

    protected TextView     noMedia;
    protected Recipient    recipient;
    protected RecyclerView recyclerView;
    protected Locale       locale;

    @Override
    public void onCreate(Bundle bundle) {
      super.onCreate(bundle);

      String       address      = getArguments().getString(ADDRESS_EXTRA);
      Locale       locale       = (Locale)getArguments().getSerializable(LOCALE_EXTRA);

      if (address == null)      throw new AssertionError();
      if (locale == null)       throw new AssertionError();

      this.recipient    = Recipient.from(getContext(), Address.fromSerialized(address), true);
      this.locale       = locale;

      getLoaderManager().initLoader(0, null, this);
    }
  }

  public static class MediaOverviewGalleryFragment
      extends MediaOverviewFragment<BucketedThreadMedia>
      implements MediaGalleryAdapter.ItemClickListener
  {

    private StickyHeaderGridLayoutManager gridManager;
    private ActionMode                    actionMode;
    private ActionModeCallback            actionModeCallback = new ActionModeCallback();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      View view = inflater.inflate(R.layout.media_overview_gallery_fragment, container, false);

      this.recyclerView = ViewUtil.findById(view, R.id.media_grid);
      this.noMedia      = ViewUtil.findById(view, R.id.no_images);
      this.gridManager  = new StickyHeaderGridLayoutManager(getResources().getInteger(R.integer.media_overview_cols));

      this.recyclerView.setAdapter(new MediaGalleryAdapter(getContext(),
                                                           GlideApp.with(this),
                                                           new BucketedThreadMedia(getContext()),
                                                           locale,
                                                           this));
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
    public @NonNull Loader<BucketedThreadMedia> onCreateLoader(int i, Bundle bundle) {
      return new BucketedThreadMediaLoader(getContext(), recipient.getAddress());
    }

    @Override
    public void onLoadFinished(@NonNull Loader<BucketedThreadMedia> loader, BucketedThreadMedia bucketedThreadMedia) {
      ((MediaGalleryAdapter) recyclerView.getAdapter()).setMedia(bucketedThreadMedia);
      ((MediaGalleryAdapter) recyclerView.getAdapter()).notifyAllSectionsDataSetChanged();

      noMedia.setVisibility(recyclerView.getAdapter().getItemCount() > 0 ? View.GONE : View.VISIBLE);
      getActivity().invalidateOptionsMenu();
    }

    @Override
    public void onLoaderReset(@NonNull Loader<BucketedThreadMedia> cursorLoader) {
      ((MediaGalleryAdapter) recyclerView.getAdapter()).setMedia(new BucketedThreadMedia(getContext()));
    }

    @Override
    public void onMediaClicked(@NonNull MediaDatabase.MediaRecord mediaRecord) {
      if (actionMode != null) {
        handleMediaMultiSelectClick(mediaRecord);
      } else {
        handleMediaPreviewClick(mediaRecord);
      }
    }

    private void handleMediaMultiSelectClick(@NonNull MediaDatabase.MediaRecord mediaRecord) {
      MediaGalleryAdapter adapter = getListAdapter();

      adapter.toggleSelection(mediaRecord);
      if (adapter.getSelectedMediaCount() == 0) {
        actionMode.finish();
      } else {
        actionMode.setTitle(String.valueOf(adapter.getSelectedMediaCount()));
      }
    }

    private void handleMediaPreviewClick(@NonNull MediaDatabase.MediaRecord mediaRecord) {
      if (mediaRecord.getAttachment().getDataUri() == null) {
        return;
      }

      Context context = getContext();
      if (context == null) {
        return;
      }

      Intent intent = new Intent(context, MediaPreviewActivity.class);
      intent.putExtra(MediaPreviewActivity.DATE_EXTRA, mediaRecord.getDate());
      intent.putExtra(MediaPreviewActivity.SIZE_EXTRA, mediaRecord.getAttachment().getSize());
      intent.putExtra(MediaPreviewActivity.ADDRESS_EXTRA, recipient.getAddress());
      intent.putExtra(MediaPreviewActivity.OUTGOING_EXTRA, mediaRecord.isOutgoing());
      intent.putExtra(MediaPreviewActivity.LEFT_IS_RECENT_EXTRA, true);

      intent.setDataAndType(mediaRecord.getAttachment().getDataUri(), mediaRecord.getContentType());
      context.startActivity(intent);
    }

    @Override
    public void onMediaLongClicked(MediaDatabase.MediaRecord mediaRecord) {
      if (actionMode == null) {
        ((MediaGalleryAdapter) recyclerView.getAdapter()).toggleSelection(mediaRecord);
        recyclerView.getAdapter().notifyDataSetChanged();

        enterMultiSelect();
      }
    }

    @SuppressWarnings("CodeBlock2Expr")
    @SuppressLint({"InlinedApi","StaticFieldLeak"})
    private void handleSaveMedia(@NonNull Collection<MediaDatabase.MediaRecord> mediaRecords) {
      final Context context = getContext();
      SaveAttachmentTask.showWarningDialog(context, (dialogInterface, which) -> {
        Permissions.with(this)
                   .request(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                   .ifNecessary()
                   .withPermanentDenialDialog(getString(R.string.MediaPreviewActivity_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
                   .onAnyDenied(() -> Toast.makeText(getContext(), R.string.MediaPreviewActivity_unable_to_write_to_external_storage_without_permission, Toast.LENGTH_LONG).show())
                   .onAllGranted(() -> {
                     new ProgressDialogAsyncTask<Void, Void, List<SaveAttachmentTask.Attachment>>(context,
                                                                                                  R.string.MediaOverviewActivity_collecting_attachments,
                                                                                                  R.string.please_wait) {
                       @Override
                       protected List<SaveAttachmentTask.Attachment> doInBackground(Void... params) {
                         List<SaveAttachmentTask.Attachment> attachments = new LinkedList<>();

                         for (MediaDatabase.MediaRecord mediaRecord : mediaRecords) {
                           if (mediaRecord.getAttachment().getDataUri() != null) {
                             attachments.add(new SaveAttachmentTask.Attachment(mediaRecord.getAttachment().getDataUri(),
                                                                               mediaRecord.getContentType(),
                                                                               mediaRecord.getDate(),
                                                                               mediaRecord.getAttachment().getFileName()));
                           }
                         }

                         return attachments;
                       }

                       @Override
                       protected void onPostExecute(List<SaveAttachmentTask.Attachment> attachments) {
                         super.onPostExecute(attachments);
                         SaveAttachmentTask saveTask = new SaveAttachmentTask(context,
                                                                              attachments.size());
                         saveTask.executeOnExecutor(THREAD_POOL_EXECUTOR,
                                                    attachments.toArray(new SaveAttachmentTask.Attachment[attachments.size()]));
                         actionMode.finish();
                       }
                     }.execute();
                   })
                   .execute();
      }, mediaRecords.size());
    }

    @SuppressLint("StaticFieldLeak")
    private void handleDeleteMedia(@NonNull Collection<MediaDatabase.MediaRecord> mediaRecords) {
      int recordCount       = mediaRecords.size();
      Resources res         = getContext().getResources();
      String confirmTitle   = res.getQuantityString(R.plurals.MediaOverviewActivity_Media_delete_confirm_title,
                                                    recordCount,
                                                    recordCount);
      String confirmMessage = res.getQuantityString(R.plurals.MediaOverviewActivity_Media_delete_confirm_message,
                                                    recordCount,
                                                    recordCount);

      AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
      builder.setIconAttribute(R.attr.dialog_alert_icon);
      builder.setTitle(confirmTitle);
      builder.setMessage(confirmMessage);
      builder.setCancelable(true);

      builder.setPositiveButton(R.string.delete, (dialogInterface, i) -> {
        new ProgressDialogAsyncTask<MediaDatabase.MediaRecord, Void, Void>(getContext(),
                                                                           R.string.MediaOverviewActivity_Media_delete_progress_title,
                                                                           R.string.MediaOverviewActivity_Media_delete_progress_message)
        {
          @Override
          protected Void doInBackground(MediaDatabase.MediaRecord... records) {
            if (records == null || records.length == 0) {
              return null;
            }

            for (MediaDatabase.MediaRecord record : records) {
              AttachmentUtil.deleteAttachment(getContext(), record.getAttachment());
            }
            return null;
          }

        }.execute(mediaRecords.toArray(new MediaDatabase.MediaRecord[mediaRecords.size()]));
      });
      builder.setNegativeButton(android.R.string.cancel, null);
      builder.show();
    }

    private void handleSelectAllMedia() {
      getListAdapter().selectAllMedia();
      actionMode.setTitle(String.valueOf(getListAdapter().getSelectedMediaCount()));
    }

    private MediaGalleryAdapter getListAdapter() {
      return (MediaGalleryAdapter) recyclerView.getAdapter();
    }

    private void enterMultiSelect() {
      actionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(actionModeCallback);
      ((MediaOverviewActivity) getActivity()).onEnterMultiSelect();
    }

    private class ActionModeCallback implements ActionMode.Callback {

      private int originalStatusBarColor;

      @Override
      public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.media_overview_context, menu);
        mode.setTitle("1");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          Window window = getActivity().getWindow();
          originalStatusBarColor = window.getStatusBarColor();
          window.setStatusBarColor(getResources().getColor(R.color.action_mode_status_bar));
        }
        return true;
      }

      @Override
      public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
      }

      @Override
      public boolean onActionItemClicked(ActionMode mode, MenuItem menuItem) {
        switch (menuItem.getItemId()) {
          case R.id.save:
            handleSaveMedia(getListAdapter().getSelectedMedia());
            return true;
          case R.id.delete:
            handleDeleteMedia(getListAdapter().getSelectedMedia());
            actionMode.finish();
            return true;
          case R.id.select_all:
            handleSelectAllMedia();
            return true;
        }
        return false;
      }

      @Override
      public void onDestroyActionMode(ActionMode mode) {
        actionMode = null;
        getListAdapter().clearSelection();
        ((MediaOverviewActivity) getActivity()).onExitMultiSelect();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          getActivity().getWindow().setStatusBarColor(originalStatusBarColor);
        }
      }
    }
  }

  public static class MediaOverviewDocumentsFragment extends MediaOverviewFragment<Cursor> {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      View                  view    = inflater.inflate(R.layout.media_overview_documents_fragment, container, false);
      MediaDocumentsAdapter adapter = new MediaDocumentsAdapter(getContext(), null, locale);

      this.recyclerView  = ViewUtil.findById(view, R.id.recycler_view);
      this.noMedia       = ViewUtil.findById(view, R.id.no_documents);

      this.recyclerView.setAdapter(adapter);
      this.recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
      this.recyclerView.addItemDecoration(new StickyHeaderDecoration(adapter, false, true));
      this.recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));

      return view;
    }

    @Override
    public @NonNull Loader<Cursor> onCreateLoader(int id, Bundle args) {
      return new ThreadMediaLoader(getContext(), recipient.getAddress(), false);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
      ((CursorRecyclerViewAdapter)this.recyclerView.getAdapter()).changeCursor(data);
      getActivity().invalidateOptionsMenu();

      this.noMedia.setVisibility(data.getCount() > 0 ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
      ((CursorRecyclerViewAdapter)this.recyclerView.getAdapter()).changeCursor(null);
      getActivity().invalidateOptionsMenu();
    }
  }
}
