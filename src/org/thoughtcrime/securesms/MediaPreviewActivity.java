/*
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

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.CursorPagerAdapter;
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord;
import org.thoughtcrime.securesms.database.loaders.ThreadMediaLoader;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.SaveAttachmentTask.Attachment;
import org.thoughtcrime.securesms.util.Util;

/**
 * Activity for displaying media attachments in-app
 */
public class MediaPreviewActivity extends    PassphraseRequiredActionBarActivity
                                  implements LoaderManager.LoaderCallbacks<Cursor>,
                                             RecipientModifiedListener,
                                             ViewPager.OnPageChangeListener {
  private final static String TAG = MediaPreviewActivity.class.getSimpleName();

  public static final String ADDRESS_EXTRA  = "address";
  public static final String DATE_EXTRA     = "date";
  public static final String SIZE_EXTRA     = "size";
  public static final String OUTGOING_EXTRA = "outgoing";

  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private MasterSecret masterSecret;

  private ViewPager viewPager;
  private Uri       mediaUri;
  private String    mediaType;
  private Recipient recipient;
  private long      date;
  private long      size;
  private boolean   outgoing;

  @Override
  protected void onCreate(Bundle bundle, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
    this.setTheme(R.style.TextSecure_DarkTheme);
    dynamicLanguage.onCreate(this);

    setFullscreenIfPossible();
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                         WindowManager.LayoutParams.FLAG_FULLSCREEN);

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    setContentView(R.layout.media_preview_activity);
    viewPager = (ViewPager) findViewById(R.id.viewPager);

    initializeResources();
    initializeActionBar();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @TargetApi(VERSION_CODES.JELLY_BEAN)
  private void setFullscreenIfPossible() {
    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
      getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
  }

  @Override
  public void onModified(Recipient recipient) {
    Util.runOnMain(this::initializeActionBar);
  }

  private void initializeActionBar() {
    final CharSequence relativeTimeSpan;

    if (date > 0) {
      relativeTimeSpan = DateUtils.getExtendedRelativeTimeSpanString(this,dynamicLanguage.getCurrentLocale(),date);
    } else {
      relativeTimeSpan = getString(R.string.MediaPreviewActivity_draft);
    }

    if (outgoing) getSupportActionBar().setTitle(getString(R.string.MediaPreviewActivity_you));
    else          getSupportActionBar().setTitle(recipient.toShortString());

    getSupportActionBar().setSubtitle(relativeTimeSpan);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicLanguage.onResume(this);
    if (recipient != null) recipient.addListener(this);
    viewPager.addOnPageChangeListener(this);
    initializeMedia();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (recipient != null) recipient.removeListener(this);
    viewPager.clearOnPageChangeListeners();
    cleanupMedia();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (recipient != null) recipient.removeListener(this);
    viewPager.clearOnPageChangeListeners();
    setIntent(intent);
    initializeResources();
    initializeActionBar();
  }

  private void initializeResources() {
    Address address = getIntent().getParcelableExtra(ADDRESS_EXTRA);

    mediaUri  = getIntent().getData();
    mediaType = getIntent().getType();
    date      = getIntent().getLongExtra(DATE_EXTRA, -1);
    size      = getIntent().getLongExtra(SIZE_EXTRA, 0);
    outgoing  = getIntent().getBooleanExtra(OUTGOING_EXTRA, false);

    if (address != null) {
      recipient = Recipient.from(this, address, true);
      recipient.addListener(this);
    } else {
      recipient = null;
    }
  }

  private void initializeMedia() {
    if (!isContentTypeSupported(mediaType)) {
      Log.w(TAG, "Unsupported media type sent to MediaPreviewActivity, finishing.");
      Toast.makeText(getApplicationContext(), R.string.MediaPreviewActivity_unssuported_media_type, Toast.LENGTH_LONG).show();
      finish();
    }

    if (recipient != null) {
      getSupportLoaderManager().initLoader(0, null, MediaPreviewActivity.this);
    } else {
      viewPager.setAdapter(new MediaPreviewDraftAdapter(this,
                                                        getWindow(),
                                                        masterSecret,
                                                        mediaUri,
                                                        mediaType,
                                                        size));
    }
  }

  private void initializeViewPagerAdapter(Cursor cursor) {
    final MediaPreviewThreadAdapter adapter = new MediaPreviewThreadAdapter(this,
                                                                            getWindow(),
                                                                            masterSecret,
                                                                            cursor);
    viewPager.addOnPageChangeListener(adapter);
    final int startPosition = adapter.getAndSetStartPosition(mediaUri);
    viewPager.setAdapter(adapter);
    viewPager.setCurrentItem(startPosition);
  }

  private void cleanupMedia() {
    viewPager.setAdapter(null);
  }

  private void showOverview() {
    Intent intent = new Intent(this, MediaOverviewActivity.class);
    intent.putExtra(MediaOverviewActivity.ADDRESS_EXTRA, recipient.getAddress());
    startActivity(intent);
  }

  private void forward() {
    Intent composeIntent = new Intent(this, ShareActivity.class);
    composeIntent.putExtra(Intent.EXTRA_STREAM, mediaUri);
    composeIntent.setType(mediaType);
    startActivity(composeIntent);
  }

  private void saveToDisk() {
    SaveAttachmentTask.showWarningDialog(this, (dialogInterface, i) -> {
      Permissions.with(this)
                 .request(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                 .ifNecessary()
                 .withPermanentDenialDialog(getString(R.string.MediaPreviewActivity_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
                 .onAnyDenied(() -> Toast.makeText(this, R.string.MediaPreviewActivity_unable_to_write_to_external_storage_without_permission, Toast.LENGTH_LONG).show())
                 .onAllGranted(() -> {
                   SaveAttachmentTask saveTask = new SaveAttachmentTask(MediaPreviewActivity.this, masterSecret, viewPager);
                   long saveDate = (date > 0) ? date : System.currentTimeMillis();
                   saveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new Attachment(mediaUri, mediaType, saveDate, null));
                 })
                 .execute();
    });
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    menu.clear();
    MenuInflater inflater = this.getMenuInflater();
    inflater.inflate(R.menu.media_preview, menu);
    if (recipient == null) menu.findItem(R.id.media_preview__overview).setVisible(false);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case R.id.media_preview__overview: showOverview(); return true;
      case R.id.media_preview__forward:  forward();      return true;
      case R.id.save:                    saveToDisk();   return true;
      case android.R.id.home:            finish();       return true;
    }

    return false;
  }

  @Override
  public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
    return new ThreadMediaLoader(this, masterSecret, recipient.getAddress(), true);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
    Log.w(TAG, "onLoadFinished()");
    initializeViewPagerAdapter(cursor);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> cursorLoader) {
    CursorPagerAdapter adapter = (CursorPagerAdapter) viewPager.getAdapter();
    if (adapter != null) adapter.changeCursor(null);
  }

  @Override
  public void onPageScrollStateChanged(int state) {}

  @Override
  public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

  @Override
  public void onPageSelected(int position) {
    if (viewPager.getAdapter().getCount() > 1) {
      updateResources(position);
      initializeActionBar();
    }
  }

  private void updateResources(int position) {
    MediaRecord mediaRecord = ((MediaPreviewThreadAdapter) viewPager.getAdapter())
                                .getMediaRecord(position);
    mediaUri  = mediaRecord.getAttachment().getDataUri();
    mediaType = mediaRecord.getContentType();
    date      = mediaRecord.getDate();
    size      = mediaRecord.getAttachment().getSize();

    if (recipient != null) recipient.removeListener(this);
    Address newAddress = mediaRecord.getAddress();
    if (newAddress != null) {
      recipient = Recipient.from(this, newAddress, true);
      recipient.addListener(this);
    } else {
      recipient = null;
    }
  }

  public static boolean isContentTypeSupported(final String contentType) {
    return contentType != null && (contentType.startsWith("image/") || contentType.startsWith("video/"));
  }
}
