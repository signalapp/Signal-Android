/*
 * Copyright (C) 2014-2017 Open Whisper Systems
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Process;
import android.provider.OpenableColumns;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import org.thoughtcrime.securesms.components.SearchToolbar;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader.DisplayMode;
import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.FileUtils;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * An activity to quickly share content with contacts
 *
 * @author Jake McGinty
 */
public class ShareActivity extends PassphraseRequiredActionBarActivity
    implements ContactSelectionListFragment.OnContactSelectedListener, SwipeRefreshLayout.OnRefreshListener
{
  private static final String TAG = ShareActivity.class.getSimpleName();

  public static final String EXTRA_THREAD_ID          = "thread_id";
  public static final String EXTRA_ADDRESS_MARSHALLED = "address_marshalled";
  public static final String EXTRA_DISTRIBUTION_TYPE  = "distribution_type";

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ContactSelectionListFragment contactsFragment;
  private SearchToolbar                searchToolbar;
  private ImageView                    searchAction;
  private View                         progressWheel;
  private Uri                          resolvedExtra;
  private String                       mimeType;
  private boolean                      isPassingAlongMedia;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    if (!getIntent().hasExtra(ContactSelectionListFragment.DISPLAY_MODE)) {
      getIntent().putExtra(ContactSelectionListFragment.DISPLAY_MODE,
                           TextSecurePreferences.isSmsEnabled(this)
                               ? DisplayMode.FLAG_ALL
                               : DisplayMode.FLAG_PUSH | DisplayMode.FLAG_GROUPS);
    }

    getIntent().putExtra(ContactSelectionListFragment.REFRESHABLE, false);
    getIntent().putExtra(ContactSelectionListFragment.RECENTS, true);

    setContentView(R.layout.share_activity);

    initializeToolbar();
    initializeResources();
    initializeSearch();
    initializeMedia();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    Log.i(TAG, "onNewIntent()");
    super.onNewIntent(intent);
    setIntent(intent);
    initializeMedia();
  }

  @Override
  public void onResume() {
    Log.i(TAG, "onResume()");
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public void onPause() {
    super.onPause();
    if (!isPassingAlongMedia && resolvedExtra != null) {
      BlobProvider.getInstance().delete(this, resolvedExtra);

      if (!isFinishing()) {
        finish();
      }
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        onBackPressed();
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onBackPressed() {
    if (searchToolbar.isVisible()) searchToolbar.collapse();
    else                           super.onBackPressed();
  }

  private void initializeToolbar() {
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    ActionBar actionBar = getSupportActionBar();

    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
    }
  }

  private void initializeResources() {
    progressWheel    = findViewById(R.id.progress_wheel);
    searchToolbar    = findViewById(R.id.search_toolbar);
    searchAction     = findViewById(R.id.search_action);
    contactsFragment = (ContactSelectionListFragment) getSupportFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);
    contactsFragment.setOnContactSelectedListener(this);
    contactsFragment.setOnRefreshListener(this);
  }

  private void initializeSearch() {
    searchAction.setOnClickListener(v -> searchToolbar.display(searchAction.getX() + (searchAction.getWidth() / 2),
                                                               searchAction.getY() + (searchAction.getHeight() / 2)));

    searchToolbar.setListener(new SearchToolbar.SearchListener() {
      @Override
      public void onSearchTextChange(String text) {
        if (contactsFragment != null) {
          contactsFragment.setQueryFilter(text);
        }
      }

      @Override
      public void onSearchClosed() {
        if (contactsFragment != null) {
          contactsFragment.resetQueryFilter();
        }
      }
    });
  }

  private void initializeMedia() {
    final Context context = this;
    isPassingAlongMedia = false;

    Uri streamExtra = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
    mimeType        = getMimeType(streamExtra);

    if (streamExtra != null && PartAuthority.isLocalUri(streamExtra)) {
      isPassingAlongMedia = true;
      resolvedExtra       = streamExtra;
      handleResolvedMedia(getIntent(), false);
    } else {
      contactsFragment.getView().setVisibility(View.GONE);
      progressWheel.setVisibility(View.VISIBLE);
      new ResolveMediaTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, streamExtra);
    }
  }

  private void handleResolvedMedia(Intent intent, boolean animate) {
    long      threadId         = intent.getLongExtra(EXTRA_THREAD_ID, -1);
    int       distributionType = intent.getIntExtra(EXTRA_DISTRIBUTION_TYPE, -1);
    Address   address          = null;

    if (intent.hasExtra(EXTRA_ADDRESS_MARSHALLED)) {
      Parcel parcel = Parcel.obtain();
      byte[] marshalled = intent.getByteArrayExtra(EXTRA_ADDRESS_MARSHALLED);
      parcel.unmarshall(marshalled, 0, marshalled.length);
      parcel.setDataPosition(0);
      address = parcel.readParcelable(getClassLoader());
      parcel.recycle();
    }

    boolean hasResolvedDestination = threadId != -1 && address != null && distributionType != -1;

    if (!hasResolvedDestination && animate) {
      ViewUtil.fadeIn(contactsFragment.getView(), 300);
      ViewUtil.fadeOut(progressWheel, 300);
    } else if (!hasResolvedDestination) {
      contactsFragment.getView().setVisibility(View.VISIBLE);
      progressWheel.setVisibility(View.GONE);
    } else {
      createConversation(threadId, address, distributionType);
    }
  }

  private void createConversation(long threadId, Address address, int distributionType) {
    final Intent intent = getBaseShareIntent(ConversationActivity.class);
    intent.putExtra(ConversationActivity.ADDRESS_EXTRA, address);
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);

    isPassingAlongMedia = true;
    startActivity(intent);
  }

  private Intent getBaseShareIntent(final @NonNull Class<?> target) {
    final Intent           intent       = new Intent(this, target);
    final String           textExtra    = getIntent().getStringExtra(Intent.EXTRA_TEXT);
    final ArrayList<Media> mediaExtra   = getIntent().getParcelableArrayListExtra(ConversationActivity.MEDIA_EXTRA);
    final StickerLocator   stickerExtra = getIntent().getParcelableExtra(ConversationActivity.STICKER_EXTRA);

    intent.putExtra(ConversationActivity.TEXT_EXTRA, textExtra);
    intent.putExtra(ConversationActivity.MEDIA_EXTRA, mediaExtra);
    intent.putExtra(ConversationActivity.STICKER_EXTRA, stickerExtra);

    if (resolvedExtra != null) intent.setDataAndType(resolvedExtra, mimeType);

    return intent;
  }

  private String getMimeType(@Nullable Uri uri) {
    if (uri != null) {
      final String mimeType = MediaUtil.getMimeType(getApplicationContext(), uri);
      if (mimeType != null) return mimeType;
    }
    return MediaUtil.getCorrectedMimeType(getIntent().getType());
  }

  @Override
  public void onContactSelected(String number) {
    Recipient recipient = Recipient.from(this, Address.fromExternal(this, number), true);
    long existingThread = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipient);
    createConversation(existingThread, recipient.getAddress(), ThreadDatabase.DistributionTypes.DEFAULT);
  }

  @Override
  public void onContactDeselected(String number) {

  }

  @Override
  public void onRefresh() {

  }

  @SuppressLint("StaticFieldLeak")
  private class ResolveMediaTask extends AsyncTask<Uri, Void, Uri> {
    private final Context context;

    ResolveMediaTask(Context context) {
      this.context = context;
    }

    @Override
    protected Uri doInBackground(Uri... uris) {
      try {
        if (uris.length != 1 || uris[0] == null) {
          return null;
        }

        InputStream inputStream;

        if ("file".equals(uris[0].getScheme())) {
          inputStream = openFileUri(uris[0]);
        } else {
          inputStream = context.getContentResolver().openInputStream(uris[0]);
        }

        if (inputStream == null) {
          return null;
        }

        Cursor cursor   = getContentResolver().query(uris[0], new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null);
        String fileName = null;
        Long   fileSize = null;

        try {
          if (cursor != null && cursor.moveToFirst()) {
            try {
              fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
              fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
            } catch (IllegalArgumentException e) {
              Log.w(TAG, e);
            }
          }
        } finally {
          if (cursor != null) cursor.close();
        }

        return BlobProvider.getInstance()
                           .forData(inputStream, fileSize == null ? 0 : fileSize)
                           .withMimeType(mimeType)
                           .withFileName(fileName)
                           .createForMultipleSessionsOnDisk(context, e -> Log.w(TAG, "Failed to write to disk.", e));
      } catch (IOException ioe) {
        Log.w(TAG, ioe);
        return null;
      }
    }

    @Override
    protected void onPostExecute(Uri uri) {
      resolvedExtra = uri;
      handleResolvedMedia(getIntent(), true);
    }

    private InputStream openFileUri(Uri uri) throws IOException {
      FileInputStream fin   = new FileInputStream(uri.getPath());
      int             owner = FileUtils.getFileDescriptorOwner(fin.getFD());
      
      if (owner == -1 || owner == Process.myUid()) {
        fin.close();
        throw new IOException("File owned by application");
      }

      return fin;
    }
  }
}