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
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.util.Pair;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.components.MediaView;
import org.thoughtcrime.securesms.components.viewpager.ExtendedOnPageChangedListener;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord;
import org.thoughtcrime.securesms.database.loaders.PagingMediaLoader;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.util.AttachmentUtil;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.SaveAttachmentTask.Attachment;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.util.WeakHashMap;

/**
 * Activity for displaying media attachments in-app
 */
public class MediaPreviewActivity extends PassphraseRequiredActionBarActivity implements RecipientModifiedListener, LoaderManager.LoaderCallbacks<Pair<Cursor, Integer>> {

  private final static String TAG = MediaPreviewActivity.class.getSimpleName();

  public static final String ADDRESS_EXTRA        = "address";
  public static final String DATE_EXTRA           = "date";
  public static final String SIZE_EXTRA           = "size";
  public static final String OUTGOING_EXTRA       = "outgoing";
  public static final String LEFT_IS_RECENT_EXTRA = "left_is_recent";

  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ViewPager mediaPager;
  private Uri       initialMediaUri;
  private String    initialMediaType;
  private long      initialMediaSize;
  private Recipient conversationRecipient;
  private boolean   leftIsRecent;

  private int restartItem = -1;

  @SuppressWarnings("ConstantConditions")
  @Override
  protected void onCreate(Bundle bundle, boolean ready) {
    this.setTheme(R.style.TextSecure_DarkTheme);
    dynamicLanguage.onCreate(this);

    setFullscreenIfPossible();
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                         WindowManager.LayoutParams.FLAG_FULLSCREEN);

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    setContentView(R.layout.media_preview_activity);

    initializeViews();
    initializeResources();
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

  @SuppressWarnings("ConstantConditions")
  private void initializeActionBar() {
    MediaItem mediaItem = getCurrentMediaItem();

    if (mediaItem != null) {
      CharSequence relativeTimeSpan;

      if (mediaItem.date > 0) {
        relativeTimeSpan = DateUtils.getExtendedRelativeTimeSpanString(this,dynamicLanguage.getCurrentLocale(), mediaItem.date);
      } else {
        relativeTimeSpan = getString(R.string.MediaPreviewActivity_draft);
      }

      if      (mediaItem.outgoing)          getSupportActionBar().setTitle(getString(R.string.MediaPreviewActivity_you));
      else if (mediaItem.recipient != null) getSupportActionBar().setTitle(mediaItem.recipient.toShortString());
      else                                  getSupportActionBar().setTitle("");

      getSupportActionBar().setSubtitle(relativeTimeSpan);
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    dynamicLanguage.onResume(this);
    initializeMedia();
  }

  @Override
  public void onPause() {
    super.onPause();
    restartItem = cleanupMedia();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    initializeResources();
  }

  private void initializeViews() {
    mediaPager = findViewById(R.id.media_pager);
    mediaPager.setOffscreenPageLimit(1);
    mediaPager.addOnPageChangeListener(new ViewPagerListener());
  }

  private void initializeResources() {
    Address address = getIntent().getParcelableExtra(ADDRESS_EXTRA);

    initialMediaUri  = getIntent().getData();
    initialMediaType = getIntent().getType();
    initialMediaSize = getIntent().getLongExtra(SIZE_EXTRA, 0);
    leftIsRecent     = getIntent().getBooleanExtra(LEFT_IS_RECENT_EXTRA, false);
    restartItem      = -1;

    if (address != null) {
      conversationRecipient = Recipient.from(this, address, true);
    } else {
      conversationRecipient = null;
    }
  }

  private void initializeMedia() {
    if (!isContentTypeSupported(initialMediaType)) {
      Log.w(TAG, "Unsupported media type sent to MediaPreviewActivity, finishing.");
      Toast.makeText(getApplicationContext(), R.string.MediaPreviewActivity_unssuported_media_type, Toast.LENGTH_LONG).show();
      finish();
    }

    Log.w(TAG, "Loading Part URI: " + initialMediaUri);

    if (conversationRecipient != null) {
      getSupportLoaderManager().restartLoader(0, null, this);
    } else {
      mediaPager.setAdapter(new SingleItemPagerAdapter(this, GlideApp.with(this), getWindow(), initialMediaUri, initialMediaType, initialMediaSize));
    }
  }

  private int cleanupMedia() {
    int restartItem = mediaPager.getCurrentItem();

    mediaPager.removeAllViews();
    mediaPager.setAdapter(null);

    return restartItem;
  }

  private void showOverview() {
    Intent intent = new Intent(this, MediaOverviewActivity.class);
    intent.putExtra(MediaOverviewActivity.ADDRESS_EXTRA, conversationRecipient.getAddress());
    startActivity(intent);
  }

  private void forward() {
    MediaItem mediaItem = getCurrentMediaItem();

    if (mediaItem != null) {
      Intent composeIntent = new Intent(this, ShareActivity.class);
      composeIntent.putExtra(Intent.EXTRA_STREAM, mediaItem.uri);
      composeIntent.setType(mediaItem.type);
      startActivity(composeIntent);
    }
  }

  @SuppressWarnings("CodeBlock2Expr")
  @SuppressLint("InlinedApi")
  private void saveToDisk() {
    MediaItem mediaItem = getCurrentMediaItem();

    if (mediaItem != null) {
      SaveAttachmentTask.showWarningDialog(this, (dialogInterface, i) -> {
        Permissions.with(this)
                   .request(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                   .ifNecessary()
                   .withPermanentDenialDialog(getString(R.string.MediaPreviewActivity_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
                   .onAnyDenied(() -> Toast.makeText(this, R.string.MediaPreviewActivity_unable_to_write_to_external_storage_without_permission, Toast.LENGTH_LONG).show())
                   .onAllGranted(() -> {
                     SaveAttachmentTask saveTask = new SaveAttachmentTask(MediaPreviewActivity.this);
                     long saveDate = (mediaItem.date > 0) ? mediaItem.date : System.currentTimeMillis();
                     saveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new Attachment(mediaItem.uri, mediaItem.type, saveDate, null));
                   })
                   .execute();
      });
    }
  }

  @SuppressLint("StaticFieldLeak")
  private void deleteMedia() {
    MediaItem mediaItem = getCurrentMediaItem();
    if (mediaItem == null || mediaItem.attachment == null) {
      return;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setTitle(R.string.MediaPreviewActivity_media_delete_confirmation_title);
    builder.setMessage(R.string.MediaPreviewActivity_media_delete_confirmation_message);
    builder.setCancelable(true);

    builder.setPositiveButton(R.string.delete, (dialogInterface, which) -> {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... voids) {
          if (mediaItem.attachment == null) {
            return null;
          }
          AttachmentUtil.deleteAttachment(MediaPreviewActivity.this.getApplicationContext(),
                                          mediaItem.attachment);
          return null;
        }
      }.execute();

      finish();
    });
    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    menu.clear();
    MenuInflater inflater = this.getMenuInflater();
    inflater.inflate(R.menu.media_preview, menu);

    if (!isMediaInDb()) {
      menu.findItem(R.id.media_preview__overview).setVisible(false);
      menu.findItem(R.id.delete).setVisible(false);
    }

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case R.id.media_preview__overview: showOverview(); return true;
      case R.id.media_preview__forward:  forward();      return true;
      case R.id.save:                    saveToDisk();   return true;
      case R.id.delete:                  deleteMedia();  return true;
      case android.R.id.home:            finish();       return true;
    }

    return false;
  }

  private boolean isMediaInDb() {
    return conversationRecipient != null;
  }

  private @Nullable MediaItem getCurrentMediaItem() {
    MediaItemAdapter adapter = (MediaItemAdapter)mediaPager.getAdapter();

    if (adapter != null) {
      return adapter.getMediaItemFor(mediaPager.getCurrentItem());
    } else {
      return null;
    }
  }

  public static boolean isContentTypeSupported(final String contentType) {
    return contentType != null && (contentType.startsWith("image/") || contentType.startsWith("video/"));
  }

  @Override
  public Loader<Pair<Cursor, Integer>> onCreateLoader(int id, Bundle args) {
    return new PagingMediaLoader(this, conversationRecipient, initialMediaUri, leftIsRecent);
  }

  @Override
  public void onLoadFinished(Loader<Pair<Cursor, Integer>> loader, @Nullable Pair<Cursor, Integer> data) {
    if (data != null) {
      @SuppressWarnings("ConstantConditions")
      CursorPagerAdapter adapter = new CursorPagerAdapter(this, GlideApp.with(this), getWindow(), data.first, data.second, leftIsRecent);
      mediaPager.setAdapter(adapter);
      adapter.setActive(true);

      if (restartItem < 0) mediaPager.setCurrentItem(data.second);
      else                 mediaPager.setCurrentItem(restartItem);
    }
  }

  @Override
  public void onLoaderReset(Loader<Pair<Cursor, Integer>> loader) {

  }

  private class ViewPagerListener extends ExtendedOnPageChangedListener {

    @Override
    public void onPageSelected(int position) {
      super.onPageSelected(position);

      MediaItemAdapter adapter = (MediaItemAdapter)mediaPager.getAdapter();

      if (adapter != null) {
        MediaItem item = adapter.getMediaItemFor(position);
        if (item.recipient != null) item.recipient.addListener(MediaPreviewActivity.this);

        initializeActionBar();
      }
    }


    @Override
    public void onPageUnselected(int position) {
      MediaItemAdapter adapter = (MediaItemAdapter)mediaPager.getAdapter();

      if (adapter != null) {
        MediaItem item = adapter.getMediaItemFor(position);
        if (item.recipient != null) item.recipient.removeListener(MediaPreviewActivity.this);

        adapter.pause(position);
      }
    }
  }

  private static class SingleItemPagerAdapter extends PagerAdapter implements MediaItemAdapter {

    private final GlideRequests glideRequests;
    private final Window        window;
    private final Uri           uri;
    private final String        mediaType;
    private final long          size;

    private final LayoutInflater inflater;

    SingleItemPagerAdapter(@NonNull Context context, @NonNull GlideRequests glideRequests,
                           @NonNull Window window, @NonNull Uri uri, @NonNull String mediaType,
                           long size)
    {
      this.glideRequests = glideRequests;
      this.window        = window;
      this.uri           = uri;
      this.mediaType     = mediaType;
      this.size          = size;
      this.inflater      = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
      return 1;
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
      return view == object;
    }

    @Override
    public @NonNull Object instantiateItem(@NonNull ViewGroup container, int position) {
      View      itemView  = inflater.inflate(R.layout.media_view_page, container, false);
      MediaView mediaView = itemView.findViewById(R.id.media_view);

      try {
        mediaView.set(glideRequests, window, uri, mediaType, size, true);
      } catch (IOException e) {
        Log.w(TAG, e);
      }

      container.addView(itemView);

      return itemView;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
      MediaView mediaView = ((FrameLayout)object).findViewById(R.id.media_view);
      mediaView.cleanup();

      container.removeView((FrameLayout)object);
    }

    @Override
    public MediaItem getMediaItemFor(int position) {
      return new MediaItem(null, null, uri, mediaType, -1, true);
    }

    @Override
    public void pause(int position) {

    }
  }

  private static class CursorPagerAdapter extends PagerAdapter implements MediaItemAdapter {

    private final WeakHashMap<Integer, MediaView> mediaViews = new WeakHashMap<>();

    private final Context       context;
    private final GlideRequests glideRequests;
    private final Window        window;
    private final Cursor        cursor;
    private final boolean       leftIsRecent;

    private boolean active;
    private int     autoPlayPosition;

    CursorPagerAdapter(@NonNull Context context, @NonNull GlideRequests glideRequests,
                       @NonNull Window window, @NonNull Cursor cursor, int autoPlayPosition,
                       boolean leftIsRecent)
    {
      this.context          = context.getApplicationContext();
      this.glideRequests    = glideRequests;
      this.window           = window;
      this.cursor           = cursor;
      this.autoPlayPosition = autoPlayPosition;
      this.leftIsRecent     = leftIsRecent;
    }

    public void setActive(boolean active) {
      this.active = active;
      notifyDataSetChanged();
    }

    @Override
    public int getCount() {
      if (!active) return 0;
      else         return cursor.getCount();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
      return view == object;
    }

    @Override
    public @NonNull Object instantiateItem(@NonNull ViewGroup container, int position) {
      View      itemView       = LayoutInflater.from(context).inflate(R.layout.media_view_page, container, false);
      MediaView mediaView      = itemView.findViewById(R.id.media_view);
      boolean   autoplay       = position == autoPlayPosition;
      int       cursorPosition = getCursorPosition(position);

      autoPlayPosition = -1;

      cursor.moveToPosition(cursorPosition);

      MediaRecord mediaRecord = MediaRecord.from(context, cursor);

      try {
        //noinspection ConstantConditions
        mediaView.set(glideRequests, window, mediaRecord.getAttachment().getDataUri(), mediaRecord.getAttachment().getContentType(), mediaRecord.getAttachment().getSize(), autoplay);
      } catch (IOException e) {
        Log.w(TAG, e);
      }

      mediaViews.put(position, mediaView);
      container.addView(itemView);

      return itemView;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
      MediaView mediaView = ((FrameLayout)object).findViewById(R.id.media_view);
      mediaView.cleanup();

      mediaViews.remove(position);
      container.removeView((FrameLayout)object);
    }

    public MediaItem getMediaItemFor(int position) {
      cursor.moveToPosition(getCursorPosition(position));
      MediaRecord mediaRecord = MediaRecord.from(context, cursor);
      Address     address     = mediaRecord.getAddress();

      if (mediaRecord.getAttachment().getDataUri() == null) throw new AssertionError();

      return new MediaItem(address != null ? Recipient.from(context, address,true) : null,
                           mediaRecord.getAttachment(),
                           mediaRecord.getAttachment().getDataUri(),
                           mediaRecord.getContentType(),
                           mediaRecord.getDate(),
                           mediaRecord.isOutgoing());
    }

    @Override
    public void pause(int position) {
      MediaView mediaView = mediaViews.get(position);
      if (mediaView != null) mediaView.pause();
    }

    private int getCursorPosition(int position) {
      if (leftIsRecent) return position;
      else              return cursor.getCount() - 1 - position;
    }
  }

  private static class MediaItem {
    private final @Nullable Recipient          recipient;
    private final @Nullable DatabaseAttachment attachment;
    private final @NonNull  Uri                uri;
    private final @NonNull  String             type;
    private final           long               date;
    private final           boolean            outgoing;

    private MediaItem(@Nullable Recipient recipient,
                      @Nullable DatabaseAttachment attachment,
                      @NonNull Uri uri,
                      @NonNull String type,
                      long date,
                      boolean outgoing)
    {
      this.recipient  = recipient;
      this.attachment = attachment;
      this.uri        = uri;
      this.type       = type;
      this.date       = date;
      this.outgoing   = outgoing;
    }
  }

  interface MediaItemAdapter {
    MediaItem getMediaItemFor(int position);
    void pause(int position);
  }
}
