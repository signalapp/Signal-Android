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
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import org.thoughtcrime.securesms.components.SearchToolbar;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
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
  private Uri[]                        resolvedExtra;
  private String[]                     mimeType;
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
                               ? ContactSelectionListFragment.DISPLAY_MODE_ALL
                               : ContactSelectionListFragment.DISPLAY_MODE_PUSH_ONLY);
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
    Log.w(TAG, "onNewIntent()");
    super.onNewIntent(intent);
    setIntent(intent);
    initializeMedia();
  }

  @Override
  public void onResume() {
    Log.w(TAG, "onResume()");
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public void onPause() {
    super.onPause();
    if (!isPassingAlongMedia && resolvedExtra != null) {
      for (int i = 0; i < resolvedExtra.length; i++) {
        PersistentBlobProvider.getInstance(this).delete(this, resolvedExtra[i]);
      }
    }

    if (!isFinishing()) {
      finish();
    }
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
      public void onSearchReset() {
        if (contactsFragment != null) {
          contactsFragment.resetQueryFilter();
        }
      }
    });
  }

  private void initializeMedia() {
    final Context context = this;
    isPassingAlongMedia = false;

    Uri[] streamExtra;
    if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
      streamExtra = new Uri[]{getIntent().getParcelableExtra(Intent.EXTRA_STREAM)};
    } else {
      streamExtra = getIntent().getParcelableArrayListExtra(Intent.EXTRA_STREAM).toArray(new Uri[0]);
    }

    contactsFragment.getView().setVisibility(View.GONE);
    progressWheel.setVisibility(View.VISIBLE);
    new ResolveMediaTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, streamExtra);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
    case R.id.menu_new_message: handleNewConversation(); return true;
    case android.R.id.home:     finish();                return true;
    }
    return false;
  }

  private void handleNewConversation() {
    Intent intent = getBaseShareIntent(NewConversationActivity.class);
    isPassingAlongMedia = true;
    startActivity(intent);
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
    final Intent intent      = new Intent(this, target);
    final String textExtra   = getIntent().getStringExtra(Intent.EXTRA_TEXT);
    intent.putExtra(ConversationActivity.TEXT_EXTRA, textExtra);
    if (resolvedExtra != null) {
      ArrayList<String> uris = new ArrayList<>();
      ArrayList<String> mimeTypes = new ArrayList<>();

      for (int i = 0; i < resolvedExtra.length; i++) {
        uris.add(resolvedExtra[i].toString());
        mimeTypes.add(mimeType[i]);
      }

      intent.putStringArrayListExtra(ConversationActivity.URIS_EXTRA, uris);
      intent.putStringArrayListExtra(ConversationActivity.MIMETYPES_EXTRA, mimeTypes);
    }

    return intent;
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
  private class ResolveMediaTask extends AsyncTask<Uri, Void, ResolveMediaTask.Result> {
    private final Context context;

    ResolveMediaTask(Context context) {
      this.context = context;
    }

    protected class Result {
      Uri[] uris;
      public String[] mimeType;

      public Result(Uri[] uris, String[] mimeType) {
        this.uris = uris;
        this.mimeType = mimeType;
      }
    }

    @Override
    protected Result doInBackground(Uri... uris) {
      Uri[] result = new Uri[uris.length];
      String[] mimeType = new String[uris.length];

      for (int i = 0; i < uris.length; i++) {
        try {
          mimeType[i] = getMimeType(uris[i]);

          if (PartAuthority.isLocalUri(uris[i])) {
            result[i] = uris[i];
          } else {
            InputStream inputStream;

            if ("file".equals(uris[i].getScheme())) {
              inputStream = openFileUri(uris[i]);
            } else {
              inputStream = context.getContentResolver().openInputStream(uris[i]);
            }

            if (inputStream == null) {
              return null;
            }

            Cursor cursor = getContentResolver().query(uris[0], new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null);
            String fileName = null;
            Long fileSize = null;

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

            Uri uri = PersistentBlobProvider.getInstance(context)
                    .create(context, inputStream, mimeType[i], fileName, fileSize);
            if (uri == null) {
              return null;
            } else {
              result[i] = uri;
            }
          }
        } catch (IOException ioe) {
          Log.w(TAG, ioe);
          return null;
        }
      }

      return new Result(result, mimeType);
    }

    @Override
    protected void onPostExecute(Result result) {
      if (result != null) {
        resolvedExtra = result.uris;
        mimeType = result.mimeType;
      } else {
        resolvedExtra = null;
        mimeType = null;
      }

      handleResolvedMedia(getIntent(), true);
    }

    private String getMimeType(@Nullable Uri uri) {
      if (uri != null) {
        final String mimeType = MediaUtil.getMimeType(getApplicationContext(), uri);
        if (mimeType != null) return mimeType;
      }
      return MediaUtil.getCorrectedMimeType(getIntent().getType());
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