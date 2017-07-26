/**
 * Copyright (C) 2014 Open Whisper Systems
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
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Process;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.FileUtils;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An activity to quickly share content with contacts
 *
 * @author Jake McGinty
 */
public class ShareActivity extends PassphraseRequiredActionBarActivity
    implements ShareFragment.ConversationSelectedListener
{
  private static final String TAG = ShareActivity.class.getSimpleName();

  public static final String EXTRA_THREAD_ID            = "thread_id";
  public static final String EXTRA_ADDRESSES_MARSHALLED = "addresses";
  public static final String EXTRA_DISTRIBUTION_TYPE    = "distribution_type";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme   ();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private MasterSecret masterSecret;
  private ViewGroup    fragmentContainer;
  private View         progressWheel;
  private Uri          resolvedExtra;
  private String       mimeType;
  private boolean      isPassingAlongMedia;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
    setContentView(R.layout.share_activity);

    fragmentContainer = ViewUtil.findById(this, R.id.drawer_layout);
    progressWheel     = ViewUtil.findById(this, R.id.progress_wheel);

    initFragment(R.id.drawer_layout, new ShareFragment(), masterSecret);
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
    getSupportActionBar().setTitle(R.string.ShareActivity_share_with);
  }

  @Override
  public void onPause() {
    super.onPause();
    if (!isPassingAlongMedia && resolvedExtra != null) {
      PersistentBlobProvider.getInstance(this).delete(resolvedExtra);
    }
    if (!isFinishing()) {
      finish();
    }
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
      fragmentContainer.setVisibility(View.GONE);
      progressWheel.setVisibility(View.VISIBLE);
      new ResolveMediaTask(context).execute(streamExtra);
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.share, menu);
    super.onPrepareOptionsMenu(menu);
    return true;
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

  @Override
  public void onCreateConversation(long threadId, Recipients recipients, int distributionType) {
    createConversation(threadId, recipients.getAddresses(), distributionType);
  }

  private void handleResolvedMedia(Intent intent, boolean animate) {
    long      threadId         = intent.getLongExtra(EXTRA_THREAD_ID, -1);
    int       distributionType = intent.getIntExtra(EXTRA_DISTRIBUTION_TYPE, -1);
    Address[] addresses        = null;

    if (intent.hasExtra(EXTRA_ADDRESSES_MARSHALLED)) {
      Parcel parcel = Parcel.obtain();
      byte[] marshalled = intent.getByteArrayExtra(EXTRA_ADDRESSES_MARSHALLED);
      parcel.unmarshall(marshalled, 0, marshalled.length);
      parcel.setDataPosition(0);
      addresses = parcel.createTypedArray(Address.CREATOR);
      parcel.recycle();
    }

    boolean hasResolvedDestination = threadId != -1 && addresses != null && distributionType != -1;

    if (!hasResolvedDestination && animate) {
      ViewUtil.fadeIn(fragmentContainer, 300);
      ViewUtil.fadeOut(progressWheel, 300);
    } else if (!hasResolvedDestination) {
      fragmentContainer.setVisibility(View.VISIBLE);
      progressWheel.setVisibility(View.GONE);
    } else {
      createConversation(threadId, addresses, distributionType);
    }
  }

  private void createConversation(long threadId, Address[] addresses, int distributionType) {
    final Intent intent = getBaseShareIntent(ConversationActivity.class);
    intent.putExtra(ConversationActivity.ADDRESSES_EXTRA, addresses);
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);

    isPassingAlongMedia = true;
    startActivity(intent);
  }

  private Intent getBaseShareIntent(final @NonNull Class<?> target) {
    final Intent intent      = new Intent(this, target);
    final String textExtra   = getIntent().getStringExtra(Intent.EXTRA_TEXT);
    intent.putExtra(ConversationActivity.TEXT_EXTRA, textExtra);
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

  private class ResolveMediaTask extends AsyncTask<Uri, Void, Uri> {
    private final Context context;

    public ResolveMediaTask(Context context) {
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

        return PersistentBlobProvider.getInstance(context).create(masterSecret, inputStream, mimeType, fileName, fileSize);
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