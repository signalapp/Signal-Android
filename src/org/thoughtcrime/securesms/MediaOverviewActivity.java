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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import org.thoughtcrime.securesms.MediaAdapter.ItemClickListener;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.util.AbstractCursorLoader;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Activity for displaying media attachments in-app
 */
public class MediaOverviewActivity extends PassphraseRequiredActionBarActivity implements LoaderManager.LoaderCallbacks<Cursor> {
  private final static String TAG = MediaOverviewActivity.class.getSimpleName();

  public static final String ADDRESS_EXTRA   = "address";
  public static final String THREAD_ID_EXTRA = "thread_id";

  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private final ActionModeCallback actionModeCallback     = new ActionModeCallback();
  private final ItemClickListener  selectionClickListener = new MediaOverviewItemClickListener();

  private MasterSecret masterSecret;

  private RecyclerView      gridView;
  private GridLayoutManager gridManager;
  private TextView          noImages;
  private Recipient         recipient;
  private long              threadId;
  private ActionMode        actionMode;

  @Override
  protected void onPreCreate() {
    this.setTheme(R.style.TextSecure_DarkTheme);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle bundle, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
    setFullscreenIfPossible();

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    setContentView(R.layout.media_overview_activity);

    initializeResources();
    initializeActionBar();
    getSupportLoaderManager().initLoader(0, null, MediaOverviewActivity.this);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    if (gridManager != null) gridManager.setSpanCount(getResources().getInteger(R.integer.media_overview_cols));
  }

  @TargetApi(VERSION_CODES.JELLY_BEAN)
  private void setFullscreenIfPossible() {
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                         WindowManager.LayoutParams.FLAG_FULLSCREEN);

    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
      getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicLanguage.onResume(this);
  }

  private void initializeActionBar() {
    getSupportActionBar().setTitle(recipient == null
                                   ? getString(R.string.AndroidManifest__all_media)
                                   : getString(R.string.AndroidManifest__all_media_named, recipient.toShortString()));
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  private void initializeResources() {
    threadId = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);

    noImages = (TextView    ) findViewById(R.id.no_images );
    gridView = (RecyclerView) findViewById(R.id.media_grid);
    gridManager = new GridLayoutManager(this, getResources().getInteger(R.integer.media_overview_cols));
    gridView.setLayoutManager(gridManager);
    gridView.setHasFixedSize(true);

    Address address = getIntent().getParcelableExtra(ADDRESS_EXTRA);

    if (address != null) {
      recipient = Recipient.from(this, address, true);
    } else if (threadId > -1) {
      recipient = DatabaseFactory.getThreadDatabase(this).getRecipientForThreadId(threadId);
    } else {
      recipient = null;
    }

    if (recipient != null) {
      recipient.addListener(new RecipientModifiedListener() {
        @Override
        public void onModified(Recipient recipients) {
          initializeActionBar();
        }
      });
    }
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
            Cursor cursor                                   = DatabaseFactory.getMediaDatabase(c).getMediaForThread(threadId);
            List<SaveAttachmentTask.Attachment> attachments = new ArrayList<>(cursor.getCount());

            while (cursor != null && cursor.moveToNext()) {
              MediaRecord record = MediaRecord.from(c, masterSecret, cursor);
              attachments.add(new SaveAttachmentTask.Attachment(record.getAttachment().getDataUri(),
                                                                record.getContentType(),
                                                                record.getDate(),
                                                                null));
            }

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

  private void handleDeleteAttachments(final Set<MediaRecord> mediaRecords) {
    int                 attachmentCount = mediaRecords.size();
    AlertDialog.Builder builder         = new AlertDialog.Builder(this);

    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setTitle(getResources().getQuantityString(R.plurals.MediaOverviewActivity_delete_selected_attachments, attachmentCount, attachmentCount));
    builder.setMessage(getResources().getQuantityString(R.plurals.MediaOverviewActivity_this_will_permanently_delete_all_n_selected_attachments, attachmentCount, attachmentCount));
    builder.setCancelable(true);

    builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        new ProgressDialogAsyncTask<MediaRecord, Void, Void>(MediaOverviewActivity.this,
                                                             R.string.MediaOverviewActivity_deleting,
                                                             R.string.MediaOverviewActivity_deleting_messages)
        {
          @Override
          protected Void doInBackground(MediaRecord... mediaRecords) {
            AttachmentDatabase database = DatabaseFactory.getAttachmentDatabase(MediaOverviewActivity.this);
            for (MediaRecord mediaRecord : mediaRecords) {
              database.deleteAttachmentsForMessage(((DatabaseAttachment) mediaRecord.getAttachment()).getMmsId());
            }

            return null;
          }
        }.execute(mediaRecords.toArray(new MediaRecord[mediaRecords.size()]));
      }
    });

    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }

  private void handleSelectAllMedia() {
    getListAdapter().selectAllMedia();
  }

  private MediaAdapter getListAdapter() {
    return (MediaAdapter) gridView.getAdapter();
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
  public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
    return new ThreadMediaLoader(this, threadId);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
    Log.w(TAG, "onLoadFinished()");
    gridView.setAdapter(new MediaAdapter(this, masterSecret, selectionClickListener, cursor, threadId));
    noImages.setVisibility(gridView.getAdapter().getItemCount() > 0 ? View.GONE : View.VISIBLE);
    invalidateOptionsMenu();
  }

  @Override
  public void onLoaderReset(Loader<Cursor> cursorLoader) {
    ((CursorRecyclerViewAdapter)gridView.getAdapter()).changeCursor(null);
  }

  public static class ThreadMediaLoader extends AbstractCursorLoader {
    private final long threadId;

    public ThreadMediaLoader(Context context, long threadId) {
      super(context);
      this.threadId = threadId;
    }

    @Override
    public Cursor getCursor() {
      return DatabaseFactory.getMediaDatabase(getContext()).getMediaForThread(threadId);
    }
  }

  private class MediaOverviewItemClickListener implements ItemClickListener {

    @Override
    public void onItemClick(MediaRecord mediaRecord) {
      if (actionMode != null) {
        getListAdapter().toggleSelection(mediaRecord);
        if (getListAdapter().getSelectedItems().isEmpty()) actionMode.finish();
      }
    }

    @Override
    public void onItemLongClick(MediaRecord mediaRecord) {
      if (actionMode == null) {
        getListAdapter().toggleSelection(mediaRecord);
        actionMode = startSupportActionMode(actionModeCallback);
      }
    }
  }

  private class ActionModeCallback implements ActionMode.Callback {

    private int statusBarColor;

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      MenuInflater inflater = mode.getMenuInflater();
      inflater.inflate(R.menu.media_overview_context, menu);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        Window window = getWindow();
        statusBarColor = window.getStatusBarColor();
        window.setStatusBarColor(getResources().getColor(R.color.action_mode_status_bar));
      }

      return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
      return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
      getListAdapter().clearSelection();

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        getWindow().setStatusBarColor(statusBarColor);
      }

      actionMode = null;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
      switch(item.getItemId()) {
        case R.id.menu_context_delete_attachments:
          handleDeleteAttachments(getListAdapter().getSelectedItems());
          actionMode.finish();
          return true;
        case R.id.menu_context_select_all:
          handleSelectAllMedia();
          return true;
      }

      return false;
    }
  }
}
