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
import android.graphics.Bitmap;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipient.RecipientModifiedListener;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.SaveAttachmentTask.Attachment;

import java.io.IOException;

import uk.co.senab.photoview.PhotoViewAttacher;

/**
 * Activity for displaying media attachments in-app
 */
public class MediaPreviewActivity extends PassphraseRequiredActionBarActivity implements RecipientModifiedListener {
  private final static String TAG = MediaPreviewActivity.class.getSimpleName();

  public static final String RECIPIENT_EXTRA = "recipient";
  public static final String DATE_EXTRA      = "date";

  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private MasterSecret masterSecret;
  private boolean      paused;

  private TextView          errorText;
  private Bitmap            bitmap;
  private ImageView         image;
  private PhotoViewAttacher imageAttacher;
  private Uri               mediaUri;
  private String            mediaType;
  private Recipient         recipient;
  private long              date;

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

    initializeViews();
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
      relativeTimeSpan = DateUtils.getRelativeTimeSpanString(date,
                                                             System.currentTimeMillis(),
                                                             DateUtils.MINUTE_IN_MILLIS);
    } else {
      relativeTimeSpan = null;
    }
    getSupportActionBar().setTitle(recipient == null ? getString(R.string.MediaPreviewActivity_you) : recipient.toShortString());
    getSupportActionBar().setSubtitle(relativeTimeSpan);
  }

  @Override
  public void onResume() {
    super.onResume();
    paused = false;
    dynamicLanguage.onResume(this);
    if (recipient != null) recipient.addListener(this);
    initializeMedia();
  }

  @Override
  public void onPause() {
    super.onPause();
    paused = true;
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
    errorText     = (TextView)  findViewById(R.id.error);
    image         = (ImageView) findViewById(R.id.image);
    imageAttacher = new PhotoViewAttacher(image);
  }

  private void initializeResources() {
    final long recipientId = getIntent().getLongExtra(RECIPIENT_EXTRA, -1);

    mediaUri     = getIntent().getData();
    mediaType    = getIntent().getType();
    date         = getIntent().getLongExtra(DATE_EXTRA, -1);

    if (recipientId > -1) {
      recipient = RecipientFactory.getRecipientForId(this, recipientId, true);
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

    if (mediaType != null && mediaType.startsWith("image/")) {
      displayImage();
    }
  }

  private void cleanupMedia() {
    image.setImageDrawable(null);
    if (bitmap != null) {
      bitmap.recycle();
      bitmap = null;
    }
  }

  private void displayImage() {
    new AsyncTask<Void,Void,Bitmap>() {
      @Override
      protected Bitmap doInBackground(Void... params) {
        try {
          int[] maxTextureSizeParams = new int[1];
          GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTextureSizeParams, 0);
          int maxTextureSize = Math.max(maxTextureSizeParams[0], 2048);
          Log.w(TAG, "reported GL_MAX_TEXTURE_SIZE: " + maxTextureSize);
          return BitmapUtil.createScaledBitmap(MediaPreviewActivity.this, masterSecret, mediaUri,
                                               maxTextureSize, maxTextureSize);
        } catch (IOException | BitmapDecodingException e) {
          return null;
        }
      }

      @Override
      protected void onPostExecute(Bitmap bitmap) {
        if (paused) {
          if (bitmap != null) bitmap.recycle();
          return;
        }

        if (bitmap == null) {
          errorText.setText(R.string.MediaPreviewActivity_cant_display);
          errorText.setVisibility(View.VISIBLE);
        } else {
          MediaPreviewActivity.this.bitmap = bitmap;
          image.setImageBitmap(bitmap);
          image.setVisibility(View.VISIBLE);
          imageAttacher.update();
        }
      }
    }.execute();
  }

  private void saveToDisk() {
    SaveAttachmentTask.showWarningDialog(this, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialogInterface, int i) {
        SaveAttachmentTask saveTask = new SaveAttachmentTask(MediaPreviewActivity.this, masterSecret);
        saveTask.execute(new Attachment(mediaUri, mediaType, date));
      }
    });
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    menu.clear();
    MenuInflater inflater = this.getMenuInflater();
    inflater.inflate(R.menu.media_preview, menu);

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

  public static boolean isContentTypeSupported(final String contentType) {
    return contentType != null && contentType.startsWith("image/");
  }
}
