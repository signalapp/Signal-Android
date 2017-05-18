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

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.thoughtcrime.securesms.components.ZoomingImageView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.SaveAttachmentTask.Attachment;
import org.thoughtcrime.securesms.video.VideoPlayer;

import java.io.IOException;

import uk.co.senab.photoview.PhotoViewAttacher.OnViewTapListener;

/**
 * Activity for displaying media attachments in-app
 */
public class MediaPreviewActivity extends PassphraseRequiredActionBarActivity implements RecipientModifiedListener {
  private final static String TAG = MediaPreviewActivity.class.getSimpleName();

  public static final String ADDRESS_EXTRA   = "address";
  public static final String THREAD_ID_EXTRA = "thread_id";
  public static final String DATE_EXTRA      = "date";
  public static final String SIZE_EXTRA      = "size";

  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private MasterSecret masterSecret;

  private ZoomingImageView image;
  private VideoPlayer      video;

  private Uri       mediaUri;
  private String    mediaType;
  private Recipient recipient;
  private long      threadId;
  private long      date;
  private long      size;

  @Override
  protected void onCreate(Bundle bundle, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
    this.setTheme(R.style.TextSecure_DarkTheme);
    dynamicLanguage.onCreate(this);

    setFullscreenIfPossible();
    supportRequestWindowFeature(AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR_OVERLAY);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                         WindowManager.LayoutParams.FLAG_FULLSCREEN);

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    setContentView(R.layout.media_preview_activity);

    initializeViews();
    initializeListeners();
    initializeResources();
    initializeActionBar();
  }

  @TargetApi(VERSION_CODES.JELLY_BEAN)
  private void setFullscreenIfPossible() {
    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
      getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
  }

  @Override
  public void onModified(Recipient recipient) {
    initializeActionBar();
  }

  private void initializeActionBar() {
    final CharSequence relativeTimeSpan;
    if (date > 0) {
      relativeTimeSpan = DateUtils.getExtendedRelativeTimeSpanString(this,dynamicLanguage.getCurrentLocale(),date);
    } else {
      relativeTimeSpan = getString(R.string.MediaPreviewActivity_draft);
    }
    getSupportActionBar().setTitle(recipient == null ? getString(R.string.MediaPreviewActivity_you)
                                                     : recipient.toShortString());
    getSupportActionBar().setSubtitle(relativeTimeSpan);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicLanguage.onResume(this);
    if (recipient != null) recipient.addListener(this);
    initializeMedia();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (recipient != null) recipient.removeListener(this);
    cleanupMedia();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (recipient != null) recipient.removeListener(this);
    setIntent(intent);
    initializeResources();
    initializeActionBar();
  }

  private void initializeViews() {
    image = (ZoomingImageView) findViewById(R.id.image);
    video = (VideoPlayer) findViewById(R.id.video_player);
  }

  private void initializeListeners() {
    image.setOnViewTapListener(new OnViewTapListener() {
      @Override
      public void onViewTap(View view, float v, float v1) {
        toggleActionBar();
      }
    });
  }

  private void toggleActionBar() {
    ActionBar actionBar = getSupportActionBar();
    if (actionBar.isShowing()) actionBar.hide();
    else                       actionBar.show();
  }

  private void initializeResources() {
    Address address = getIntent().getParcelableExtra(ADDRESS_EXTRA);

    mediaUri     = getIntent().getData();
    mediaType    = getIntent().getType();
    date         = getIntent().getLongExtra(DATE_EXTRA, -1);
    size         = getIntent().getLongExtra(SIZE_EXTRA, 0);
    threadId     = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);

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

    Log.w(TAG, "Loading Part URI: " + mediaUri);

    try {
      if (mediaType != null && mediaType.startsWith("image/")) {
        image.setVisibility(View.VISIBLE);
        video.setVisibility(View.GONE);
        image.setImageUri(masterSecret, mediaUri, mediaType);
      } else if (mediaType != null && mediaType.startsWith("video/")) {
        image.setVisibility(View.GONE);
        video.setVisibility(View.VISIBLE);
        video.setWindow(getWindow());
        video.setVideoSource(masterSecret, new VideoSlide(this, mediaUri, size));
      }
    } catch (IOException e) {
      Log.w(TAG, e);
      Toast.makeText(getApplicationContext(), R.string.MediaPreviewActivity_unssuported_media_type, Toast.LENGTH_LONG).show();
      finish();
    }
  }

  private void cleanupMedia() {
    image.cleanup();
    video.cleanup();
  }

  private void showOverview() {
    Intent intent = new Intent(this, MediaOverviewActivity.class);
    intent.putExtra(MediaOverviewActivity.THREAD_ID_EXTRA, threadId);
    startActivity(intent);
  }

  private void forward() {
    Intent composeIntent = new Intent(this, ShareActivity.class);
    composeIntent.putExtra(Intent.EXTRA_STREAM, mediaUri);
    composeIntent.setType(mediaType);
    startActivity(composeIntent);
  }

  private void saveToDisk() {
    SaveAttachmentTask.showWarningDialog(this, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialogInterface, int i) {
        SaveAttachmentTask saveTask = new SaveAttachmentTask(MediaPreviewActivity.this, masterSecret, image);
        long saveDate = (date > 0) ? date : System.currentTimeMillis();
        saveTask.execute(new Attachment(mediaUri, mediaType, saveDate, null));
      }
    });
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    menu.clear();
    MenuInflater inflater = this.getMenuInflater();
    inflater.inflate(R.menu.media_preview, menu);
    if (threadId == -1) menu.findItem(R.id.media_preview__overview).setVisible(false);

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

  public static boolean isContentTypeSupported(final String contentType) {
    return contentType != null && (contentType.startsWith("image/") || contentType.startsWith("video/"));
  }
}
