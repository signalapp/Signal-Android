/**
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

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.codewaves.stickyheadergrid.StickyHeaderGridLayoutManager;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord;
import org.thoughtcrime.securesms.database.loaders.BucketedThreadMediaLoader;
import org.thoughtcrime.securesms.database.loaders.BucketedThreadMediaLoader.BucketedThreadMedia;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for displaying media attachments in-app
 */
public class MediaOverviewActivity extends PassphraseRequiredActionBarActivity implements LoaderManager.LoaderCallbacks<BucketedThreadMedia> {
  private final static String TAG = MediaOverviewActivity.class.getSimpleName();

  public static final String ADDRESS_EXTRA   = "address";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private MasterSecret masterSecret;

  private RecyclerView      gridView;
  private StickyHeaderGridLayoutManager gridManager;
  private TextView          noImages;
  private Recipient         recipient;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle bundle, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    setContentView(R.layout.media_overview_activity);

    initializeResources();
    initializeActionBar();

    getSupportLoaderManager().initLoader(0, null, MediaOverviewActivity.this);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    if (gridManager != null) {
      this.gridManager = new StickyHeaderGridLayoutManager(getResources().getInteger(R.integer.media_overview_cols));
      this.gridView.setLayoutManager(gridManager);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  private void initializeActionBar() {
    getSupportActionBar().setTitle(recipient.toShortString());
  }

  private void initializeResources() {
    this.noImages    = ViewUtil.findById(this, R.id.no_images);
    this.gridView    = ViewUtil.findById(this, R.id.media_grid);
    this.gridManager = new StickyHeaderGridLayoutManager(getResources().getInteger(R.integer.media_overview_cols));

    Address address = getIntent().getParcelableExtra(ADDRESS_EXTRA);

    this.recipient = Recipient.from(this, address, true);
    this.recipient.addListener(recipient -> initializeActionBar());

    this.gridView.setAdapter(new MediaAdapter(this, masterSecret, new BucketedThreadMedia(this), dynamicLanguage.getCurrentLocale(), address));
    this.gridView.setLayoutManager(gridManager);
    this.gridView.setHasFixedSize(true);
  }

  private void saveToDisk() {
    final Context c = this;

    SaveAttachmentTask.showWarningDialog(this, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialogInterface, int i) {
        new ProgressDialogAsyncTask<Void, Void, List<SaveAttachmentTask.Attachment>>(c,
                                                                                     R.string.ConversationFragment_collecting_attahments,
                                                                                     R.string.please_wait) {
          @Override
          protected List<SaveAttachmentTask.Attachment> doInBackground(Void... params) {
            long  threadId                                  = DatabaseFactory.getThreadDatabase(c).getThreadIdFor(recipient);
            Cursor cursor                                   = DatabaseFactory.getMediaDatabase(c).getMediaForThread(threadId);
            List<SaveAttachmentTask.Attachment> attachments = new ArrayList<>(cursor.getCount());

            while (cursor.moveToNext()) {
              MediaRecord record = MediaRecord.from(c, masterSecret, cursor);
              attachments.add(new SaveAttachmentTask.Attachment(record.getAttachment().getDataUri(),
                                                                record.getContentType(),
                                                                record.getDate(),
                                                                null));
            }

            cursor.close();

            return attachments;
          }

          @Override
          protected void onPostExecute(List<SaveAttachmentTask.Attachment> attachments) {
            super.onPostExecute(attachments);

            SaveAttachmentTask saveTask = new SaveAttachmentTask(c, masterSecret, gridView, attachments.size());
            saveTask.execute(attachments.toArray(new SaveAttachmentTask.Attachment[attachments.size()]));
          }
        }.execute();
      }
    }, gridView.getAdapter().getItemCount());
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    menu.clear();
    if (gridView.getAdapter() != null && gridView.getAdapter().getItemCount() > 0) {
      MenuInflater inflater = this.getMenuInflater();
      inflater.inflate(R.menu.media_overview, menu);
    }

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
    case R.id.save:         saveToDisk(); return true;
    case android.R.id.home: finish();     return true;
    }

    return false;
  }

  @Override
  public Loader<BucketedThreadMedia> onCreateLoader(int i, Bundle bundle) {
    return new BucketedThreadMediaLoader(this, masterSecret, recipient.getAddress());
  }

  @Override
  public void onLoadFinished(Loader<BucketedThreadMedia> loader, BucketedThreadMedia bucketedThreadMedia) {
    ((MediaAdapter)gridView.getAdapter()).setMedia(bucketedThreadMedia);
    ((MediaAdapter)gridView.getAdapter()).notifyAllSectionsDataSetChanged();

    noImages.setVisibility(gridView.getAdapter().getItemCount() > 0 ? View.GONE : View.VISIBLE);
    invalidateOptionsMenu();
  }

  @Override
  public void onLoaderReset(Loader<BucketedThreadMedia> cursorLoader) {
    ((MediaAdapter)gridView.getAdapter()).setMedia(new BucketedThreadMedia(this));
  }
}
