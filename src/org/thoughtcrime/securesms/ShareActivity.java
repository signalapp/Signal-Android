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

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.doomonafireball.betterpickers.Utils;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.MemoryCleaner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import de.gdata.messaging.util.GUtil;

/**
 * An activity to quickly share content with contacts
 *
 * @author Jake McGinty
 */
public class ShareActivity extends PassphraseRequiredActionBarActivity
    implements ShareFragment.ConversationSelectedListener
{
  public final static String MASTER_SECRET_EXTRA = "master_secret";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme   ();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ShareFragment fragment;
  private MasterSecret  masterSecret;

  @Override
  public void onCreate(Bundle icicle) {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    super.onCreate(icicle);

    setContentView(R.layout.share_activity);
    initializeResources();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
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
    if (!isFinishing()) finish();
  }

  @Override
  public void onDestroy() {
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
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

  @Override
  public void onMasterSecretCleared() {
    startActivity(new Intent(this, RoutingActivity.class));
    super.onMasterSecretCleared();
  }

  private void handleNewConversation() {
    Intent intent = getBaseShareIntent(NewConversationActivity.class);
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

    startActivity(intent);
  }

  private void initializeResources() {
    this.masterSecret = getIntent().getParcelableExtra(MASTER_SECRET_EXTRA);

    this.fragment = (ShareFragment)this.getSupportFragmentManager()
        .findFragmentById(R.id.fragment_content);

    this.fragment.setMasterSecret(masterSecret);
  }

  private Intent getBaseShareIntent(final Class<?> target) {
    final Intent intent = new Intent(this, target);
    final Intent originalIntent = getIntent();
    final String draftText   = originalIntent.getStringExtra(ConversationActivity.DRAFT_TEXT_EXTRA);
    final Uri    draftImage  = originalIntent.getParcelableExtra(ConversationActivity.DRAFT_IMAGE_EXTRA);
    final Uri    draftVideo  = originalIntent.getParcelableExtra(ConversationActivity.DRAFT_VIDEO_EXTRA);
    final Uri    draftAudio  = originalIntent.getParcelableExtra(ConversationActivity.DRAFT_AUDIO_EXTRA);
    final String mediaType   = originalIntent.getStringExtra(ConversationActivity.DRAFT_MEDIA_TYPE_EXTRA);

    intent.putExtra(ConversationActivity.DRAFT_TEXT_EXTRA, draftText);
    intent.putExtra(ConversationActivity.DRAFT_IMAGE_EXTRA, GUtil.getUsableGoogleImageUri(draftImage));
    intent.putExtra(ConversationActivity.DRAFT_VIDEO_EXTRA, GUtil.getUsableGoogleImageUri(draftVideo));
    intent.putExtra(ConversationActivity.DRAFT_AUDIO_EXTRA, GUtil.getUsableGoogleImageUri(draftAudio));
    intent.putExtra(ConversationActivity.DRAFT_MEDIA_TYPE_EXTRA, mediaType);
    intent.putExtra(NewConversationActivity.MASTER_SECRET_EXTRA, masterSecret);

    return intent;
  }
}
