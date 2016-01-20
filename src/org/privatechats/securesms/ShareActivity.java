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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

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

  private final DynamicTheme    dynamicTheme    = new DynamicTheme   ();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private MasterSecret masterSecret;
  private ViewGroup    fragmentContainer;
  private View         progressWheel;
  private Uri          resolvedExtra;
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
    super.onNewIntent(intent);
    setIntent(intent);
    initializeMedia();
  }

  @Override
  public void onResume() {
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
    if (streamExtra != null && PartAuthority.isLocalUri(streamExtra)) {
      isPassingAlongMedia = true;
      resolvedExtra       = streamExtra;
      fragmentContainer.setVisibility(View.VISIBLE);
      progressWheel.setVisibility(View.GONE);
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
    createConversation(threadId, recipients, distributionType);
  }

  private void createConversation(long threadId, Recipients recipients, int distributionType) {
    final Intent intent = getBaseShareIntent(ConversationActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);

    isPassingAlongMedia = true;
    startActivity(intent);
  }

  private Intent getBaseShareIntent(final @NonNull Class<?> target) {
    final Intent intent      = new Intent(this, target);
    final String textExtra   = getIntent().getStringExtra(Intent.EXTRA_TEXT);
    final Uri    streamExtra = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
    final String type        = streamExtra != null ? getMimeType(streamExtra)
                                                   : MediaUtil.getCorrectedMimeType(getIntent().getType());
    intent.putExtra(ConversationActivity.TEXT_EXTRA, textExtra);
    if (resolvedExtra != null) intent.setDataAndType(resolvedExtra, type);

    return intent;
  }

  private String getMimeType(Uri uri) {
    final String type = MediaUtil.getMimeType(getApplicationContext(), uri);
    return type == null ? MediaUtil.getCorrectedMimeType(getIntent().getType())
                        : type;
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

        InputStream input = context.getContentResolver().openInputStream(uris[0]);
        if (input == null) {
          return null;
        }

        return PersistentBlobProvider.getInstance(context).create(masterSecret, input);
      } catch (IOException ioe) {
        Log.w(TAG, ioe);
        return null;
      }
    }

    @Override
    protected void onPostExecute(Uri uri) {
      resolvedExtra = uri;
      ViewUtil.fadeIn(fragmentContainer, 300);
      ViewUtil.fadeOut(progressWheel, 300);
    }
  }
}