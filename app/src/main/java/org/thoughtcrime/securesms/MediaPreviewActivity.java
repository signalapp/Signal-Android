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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ShareCompat;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.lifecycle.ViewModelProviders;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.animation.DepthPageTransformer;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.components.viewpager.ExtendedOnPageChangedListener;
import org.thoughtcrime.securesms.database.MediaDatabase;
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord;
import org.thoughtcrime.securesms.database.loaders.PagingMediaLoader;
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity;
import org.thoughtcrime.securesms.mediapreview.MediaPreviewFragment;
import org.thoughtcrime.securesms.mediapreview.MediaPreviewViewModel;
import org.thoughtcrime.securesms.mediapreview.MediaRailAdapter;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sharing.ShareActivity;
import org.thoughtcrime.securesms.util.AttachmentUtil;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.FullscreenHelper;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.SaveAttachmentTask.Attachment;
import org.thoughtcrime.securesms.util.StorageUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Activity for displaying media attachments in-app
 */
public final class MediaPreviewActivity extends PassphraseRequiredActivity
  implements LoaderManager.LoaderCallbacks<Pair<Cursor, Integer>>,
             MediaRailAdapter.RailItemListener,
             MediaPreviewFragment.Events
{

  private final static String TAG = Log.tag(MediaPreviewActivity.class);

  private static final int NOT_IN_A_THREAD = -2;

  public static final String THREAD_ID_EXTRA      = "thread_id";
  public static final String DATE_EXTRA           = "date";
  public static final String SIZE_EXTRA           = "size";
  public static final String CAPTION_EXTRA        = "caption";
  public static final String LEFT_IS_RECENT_EXTRA = "left_is_recent";
  public static final String HIDE_ALL_MEDIA_EXTRA = "came_from_all_media";
  public static final String SHOW_THREAD_EXTRA    = "show_thread";
  public static final String SORTING_EXTRA        = "sorting";
  public static final String IS_VIDEO_GIF         = "is_video_gif";

  private ViewPager             mediaPager;
  private View                  detailsContainer;
  private TextView              caption;
  private View                  captionContainer;
  private RecyclerView          albumRail;
  private MediaRailAdapter      albumRailAdapter;
  private ViewGroup             playbackControlsContainer;
  private Uri                   initialMediaUri;
  private String                initialMediaType;
  private long                  initialMediaSize;
  private String                initialCaption;
  private boolean               initialMediaIsVideoGif;
  private boolean               leftIsRecent;
  private MediaPreviewViewModel viewModel;
  private ViewPagerListener     viewPagerListener;

  private int                   restartItem      = -1;
  private long                  threadId         = NOT_IN_A_THREAD;
  private boolean               cameFromAllMedia;
  private boolean               showThread;
  private MediaDatabase.Sorting sorting;
  private FullscreenHelper      fullscreenHelper;

  private @Nullable Cursor cursor = null;

  public static @NonNull Intent intentFromMediaRecord(@NonNull Context context,
                                                      @NonNull MediaRecord mediaRecord,
                                                      boolean leftIsRecent)
  {
    DatabaseAttachment attachment = Objects.requireNonNull(mediaRecord.getAttachment());
    Intent intent = new Intent(context, MediaPreviewActivity.class);
    intent.putExtra(MediaPreviewActivity.THREAD_ID_EXTRA, mediaRecord.getThreadId());
    intent.putExtra(MediaPreviewActivity.DATE_EXTRA, mediaRecord.getDate());
    intent.putExtra(MediaPreviewActivity.SIZE_EXTRA, attachment.getSize());
    intent.putExtra(MediaPreviewActivity.CAPTION_EXTRA, attachment.getCaption());
    intent.putExtra(MediaPreviewActivity.LEFT_IS_RECENT_EXTRA, leftIsRecent);
    intent.putExtra(MediaPreviewActivity.IS_VIDEO_GIF, attachment.isVideoGif());
    intent.setDataAndType(attachment.getUri(), mediaRecord.getContentType());
    return intent;
  }

  @Override
  protected void attachBaseContext(@NonNull Context newBase) {
    getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    super.attachBaseContext(newBase);
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  protected void onCreate(Bundle bundle, boolean ready) {
    this.setTheme(R.style.TextSecure_MediaPreview);
    setContentView(R.layout.media_preview_activity);

    setSupportActionBar(findViewById(R.id.toolbar));

    viewModel = ViewModelProviders.of(this).get(MediaPreviewViewModel.class);

    fullscreenHelper = new FullscreenHelper(this);

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    initializeViews();
    initializeResources();
    initializeObservers();
  }

  @SuppressLint("MissingSuperCall")
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onRailItemClicked(int distanceFromActive) {
    mediaPager.setCurrentItem(mediaPager.getCurrentItem() + distanceFromActive);
  }

  @Override
  public void onRailItemDeleteClicked(int distanceFromActive) {
    throw new UnsupportedOperationException("Callback unsupported.");
  }

  @SuppressWarnings("ConstantConditions")
  private void initializeActionBar() {
    MediaItem mediaItem = getCurrentMediaItem();

    if (mediaItem != null) {
      getSupportActionBar().setTitle(getTitleText(mediaItem));
      getSupportActionBar().setSubtitle(getSubTitleText(mediaItem));
    }
  }

  private @NonNull String getTitleText(@NonNull MediaItem mediaItem) {
    String from;
    if      (mediaItem.outgoing)          from = getString(R.string.MediaPreviewActivity_you);
    else if (mediaItem.recipient != null) from = mediaItem.recipient.getDisplayName(this);
    else                                  from = "";

    if (showThread) {
      String    to              = null;
      Recipient threadRecipient = mediaItem.threadRecipient;

      if (threadRecipient != null) {
        if (mediaItem.outgoing || threadRecipient.isGroup()) {
          if (threadRecipient.isSelf()) {
            from = getString(R.string.note_to_self);
          } else {
            to = threadRecipient.getDisplayName(this);
          }
        } else {
          to = getString(R.string.MediaPreviewActivity_you);
        }
      }

      return to != null ? getString(R.string.MediaPreviewActivity_s_to_s, from, to)
                        : from;
    } else {
      return from;
    }
  }

  private @NonNull String getSubTitleText(@NonNull MediaItem mediaItem) {
    if (mediaItem.date > 0) {
      return DateUtils.getExtendedRelativeTimeSpanString(this, Locale.getDefault(), mediaItem.date);
    } else {
      return getString(R.string.MediaPreviewActivity_draft);
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    initializeMedia();
  }

  @Override
  public void onPause() {
    super.onPause();
    restartItem = cleanupMedia();
  }

  @Override
  protected void onDestroy() {
    if (cursor != null) {
      cursor.close();
      cursor = null;
    }
    super.onDestroy();
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
    mediaPager.setPageTransformer(true, new DepthPageTransformer());

    viewPagerListener = new ViewPagerListener();
    mediaPager.addOnPageChangeListener(viewPagerListener);

    albumRail        = findViewById(R.id.media_preview_album_rail);
    albumRailAdapter = new MediaRailAdapter(GlideApp.with(this), this, false);

    albumRail.setItemAnimator(null); // Or can crash when set to INVISIBLE while animating by FullscreenHelper https://issuetracker.google.com/issues/148720682
    albumRail.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
    albumRail.setAdapter(albumRailAdapter);

    detailsContainer          = findViewById(R.id.media_preview_details_container);
    caption                   = findViewById(R.id.media_preview_caption);
    captionContainer          = findViewById(R.id.media_preview_caption_container);
    playbackControlsContainer = findViewById(R.id.media_preview_playback_controls_container);

    View toolbarLayout = findViewById(R.id.toolbar_layout);

    anchorMarginsToBottomInsets(detailsContainer);

    fullscreenHelper.configureToolbarSpacer(findViewById(R.id.toolbar_cutout_spacer));

    fullscreenHelper.showAndHideWithSystemUI(getWindow(), detailsContainer, toolbarLayout);
  }

  private void initializeResources() {
    Intent intent = getIntent();

    threadId         = intent.getLongExtra(THREAD_ID_EXTRA, NOT_IN_A_THREAD);
    cameFromAllMedia = intent.getBooleanExtra(HIDE_ALL_MEDIA_EXTRA, false);
    showThread       = intent.getBooleanExtra(SHOW_THREAD_EXTRA, false);
    sorting          = MediaDatabase.Sorting.values()[intent.getIntExtra(SORTING_EXTRA, 0)];

    initialMediaUri        = intent.getData();
    initialMediaType       = intent.getType();
    initialMediaSize       = intent.getLongExtra(SIZE_EXTRA, 0);
    initialCaption         = intent.getStringExtra(CAPTION_EXTRA);
    leftIsRecent           = intent.getBooleanExtra(LEFT_IS_RECENT_EXTRA, false);
    initialMediaIsVideoGif = intent.getBooleanExtra(IS_VIDEO_GIF, false);
    restartItem            = -1;
  }

  private void initializeObservers() {
    viewModel.getPreviewData().observe(this, previewData -> {
      if (previewData == null || mediaPager == null || mediaPager.getAdapter() == null) {
        return;
      }

      if (!((MediaItemAdapter) mediaPager.getAdapter()).hasFragmentFor(mediaPager.getCurrentItem())) {
        Log.d(TAG, "MediaItemAdapter wasn't ready. Posting again...");
        viewModel.resubmitPreviewData();
      }

      View playbackControls = ((MediaItemAdapter) mediaPager.getAdapter()).getPlaybackControls(mediaPager.getCurrentItem());

      if (previewData.getAlbumThumbnails().isEmpty() && previewData.getCaption() == null && playbackControls == null) {
        detailsContainer.setVisibility(View.GONE);
      } else {
        detailsContainer.setVisibility(View.VISIBLE);
      }

      albumRail.setVisibility(previewData.getAlbumThumbnails().isEmpty() ? View.GONE : View.VISIBLE);
      albumRailAdapter.setMedia(previewData.getAlbumThumbnails(), previewData.getActivePosition());
      albumRail.smoothScrollToPosition(previewData.getActivePosition());

      captionContainer.setVisibility(previewData.getCaption() == null ? View.GONE : View.VISIBLE);
      caption.setText(previewData.getCaption());

      if (playbackControls != null) {
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        playbackControls.setLayoutParams(params);

        playbackControlsContainer.removeAllViews();
        playbackControlsContainer.addView(playbackControls);
      } else {
        playbackControlsContainer.removeAllViews();
      }
    });
  }

  private void initializeMedia() {
    if (!isContentTypeSupported(initialMediaType)) {
      Log.w(TAG, "Unsupported media type sent to MediaPreviewActivity, finishing.");
      Toast.makeText(getApplicationContext(), R.string.MediaPreviewActivity_unssuported_media_type, Toast.LENGTH_LONG).show();
      finish();
    }

    Log.i(TAG, "Loading Part URI: " + initialMediaUri);

    if (isMediaInDb()) {
      LoaderManager.getInstance(this).restartLoader(0, null, this);
    } else {
      mediaPager.setAdapter(new SingleItemPagerAdapter(getSupportFragmentManager(), initialMediaUri, initialMediaType, initialMediaSize, initialMediaIsVideoGif));

      if (initialCaption != null) {
        detailsContainer.setVisibility(View.VISIBLE);
        captionContainer.setVisibility(View.VISIBLE);
        caption.setText(initialCaption);
      }
    }
  }

  private int cleanupMedia() {
    int restartItem = mediaPager.getCurrentItem();

    mediaPager.removeAllViews();
    mediaPager.setAdapter(null);
    viewModel.setCursor(this, null, leftIsRecent);

    return restartItem;
  }

  private void showOverview() {
    startActivity(MediaOverviewActivity.forThread(this, threadId));
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

  private void share() {
    MediaItem mediaItem = getCurrentMediaItem();

    if (mediaItem != null) {
      Uri    publicUri   = PartAuthority.getAttachmentPublicUri(mediaItem.uri);
      String mimeType    = Intent.normalizeMimeType(mediaItem.type);
      Intent shareIntent = ShareCompat.IntentBuilder.from(this)
                                                    .setStream(publicUri)
                                                    .setType(mimeType)
                                                    .createChooserIntent()
                                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

      try {
        startActivity(shareIntent);
      } catch (ActivityNotFoundException e) {
        Log.w(TAG, "No activity existed to share the media.", e);
        Toast.makeText(this, R.string.MediaPreviewActivity_cant_find_an_app_able_to_share_this_media, Toast.LENGTH_LONG).show();
      }
    }
  }

  @SuppressWarnings("CodeBlock2Expr")
  @SuppressLint("InlinedApi")
  private void saveToDisk() {
    MediaItem mediaItem = getCurrentMediaItem();

    if (mediaItem != null) {
      SaveAttachmentTask.showWarningDialog(this, (dialogInterface, i) -> {
        if (StorageUtil.canWriteToMediaStore()) {
          performSavetoDisk(mediaItem);
          return;
        }

        Permissions.with(this)
                   .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                   .ifNecessary()
                   .withPermanentDenialDialog(getString(R.string.MediaPreviewActivity_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
                   .onAnyDenied(() -> Toast.makeText(this, R.string.MediaPreviewActivity_unable_to_write_to_external_storage_without_permission, Toast.LENGTH_LONG).show())
                   .onAllGranted(() -> {
                     performSavetoDisk(mediaItem);
                   })
                   .execute();
      });
    }
  }

  private void performSavetoDisk(@NonNull MediaItem mediaItem) {
    SaveAttachmentTask saveTask = new SaveAttachmentTask(MediaPreviewActivity.this);
    long               saveDate = (mediaItem.date > 0) ? mediaItem.date : System.currentTimeMillis();
    saveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new Attachment(mediaItem.uri, mediaItem.type, saveDate, null));
  }

  @SuppressLint("StaticFieldLeak")
  private void deleteMedia() {
    MediaItem mediaItem = getCurrentMediaItem();
    if (mediaItem == null || mediaItem.attachment == null) {
      return;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setIcon(R.drawable.ic_warning);
    builder.setTitle(R.string.MediaPreviewActivity_media_delete_confirmation_title);
    builder.setMessage(R.string.MediaPreviewActivity_media_delete_confirmation_message);
    builder.setCancelable(true);

    builder.setPositiveButton(R.string.delete, (dialogInterface, which) -> {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... voids) {
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
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.clear();
    MenuInflater inflater = this.getMenuInflater();
    inflater.inflate(R.menu.media_preview, menu);

    super.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    if (!isMediaInDb()) {
      menu.findItem(R.id.media_preview__overview).setVisible(false);
      menu.findItem(R.id.delete).setVisible(false);
    }

    // Restricted to API26 because of MemoryFileUtil not supporting lower API levels well
    menu.findItem(R.id.media_preview__share).setVisible(Build.VERSION.SDK_INT >= 26);

    if (cameFromAllMedia) {
      menu.findItem(R.id.media_preview__overview).setVisible(false);
    }

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    super.onOptionsItemSelected(item);

    int itemId = item.getItemId();

    if (itemId == R.id.media_preview__overview) { showOverview(); return true; }
    if (itemId == R.id.media_preview__forward)  { forward();      return true; }
    if (itemId == R.id.media_preview__share)    { share();        return true; }
    if (itemId == R.id.save)                    { saveToDisk();   return true; }
    if (itemId == R.id.delete)                  { deleteMedia();  return true; }
    if (itemId == android.R.id.home)            { finish();       return true; }

    return false;
  }

  private boolean isMediaInDb() {
    return threadId != NOT_IN_A_THREAD;
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
    return MediaUtil.isImageType(contentType) || MediaUtil.isVideoType(contentType);
  }

  @Override
  public @NonNull Loader<Pair<Cursor, Integer>> onCreateLoader(int id, Bundle args) {
    return new PagingMediaLoader(this, threadId, initialMediaUri, leftIsRecent, sorting);
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Pair<Cursor, Integer>> loader, @Nullable Pair<Cursor, Integer> data) {
    if (data != null) {
      if (data.first == cursor) {
        return;
      }

      if (cursor != null) {
        cursor.close();
      }
      cursor = Objects.requireNonNull(data.first);

      int mediaPosition = Objects.requireNonNull(data.second);

      CursorPagerAdapter adapter = new CursorPagerAdapter(getSupportFragmentManager(),this, cursor, mediaPosition, leftIsRecent);
      mediaPager.setAdapter(adapter);
      adapter.setActive(true);

      viewModel.setCursor(this, cursor, leftIsRecent);

      int item = restartItem >= 0 ? restartItem : mediaPosition;
      mediaPager.setCurrentItem(item);

      if (item == 0) {
        viewPagerListener.onPageSelected(0);
      }
    } else {
      mediaNotAvailable();
    }
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Pair<Cursor, Integer>> loader) {

  }

  @Override
  public boolean singleTapOnMedia() {
    fullscreenHelper.toggleUiVisibility();
    return true;
  }

  @Override
  public void mediaNotAvailable() {
    Toast.makeText(this, R.string.MediaPreviewActivity_media_no_longer_available, Toast.LENGTH_LONG).show();
    finish();
  }

  private class ViewPagerListener extends ExtendedOnPageChangedListener {

    @Override
    public void onPageSelected(int position) {
      super.onPageSelected(position);

      MediaItemAdapter adapter = (MediaItemAdapter)mediaPager.getAdapter();

      if (adapter != null) {
        MediaItem item = adapter.getMediaItemFor(position);
        if (item != null && item.recipient != null) {
          item.recipient.live().observe(MediaPreviewActivity.this, r -> initializeActionBar());
        }

        viewModel.setActiveAlbumRailItem(MediaPreviewActivity.this, position);
        initializeActionBar();
      }
    }


    @Override
    public void onPageUnselected(int position) {
      MediaItemAdapter adapter = (MediaItemAdapter)mediaPager.getAdapter();

      if (adapter != null) {
        MediaItem item = adapter.getMediaItemFor(position);
        if (item != null && item.recipient != null) {
          item.recipient.live().removeObservers(MediaPreviewActivity.this);
        }

        adapter.pause(position);
      }
    }
  }

  private static class SingleItemPagerAdapter extends FragmentStatePagerAdapter implements MediaItemAdapter {

    private final Uri     uri;
    private final String  mediaType;
    private final long    size;
    private final boolean isVideoGif;

    private MediaPreviewFragment mediaPreviewFragment;

    SingleItemPagerAdapter(@NonNull FragmentManager fragmentManager,
                           @NonNull Uri uri,
                           @NonNull String mediaType,
                           long size,
                           boolean isVideoGif)
    {
      super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
      this.uri        = uri;
      this.mediaType  = mediaType;
      this.size       = size;
      this.isVideoGif = isVideoGif;
    }

    @Override
    public int getCount() {
      return 1;
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
      mediaPreviewFragment = MediaPreviewFragment.newInstance(uri, mediaType, size, true, isVideoGif);
      return mediaPreviewFragment;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
      if (mediaPreviewFragment != null) {
        mediaPreviewFragment.cleanUp();
        mediaPreviewFragment = null;
      }
    }

    @Override
    public @Nullable MediaItem getMediaItemFor(int position) {
      return new MediaItem(null, null, null, uri, mediaType, -1, true);
    }

    @Override
    public void pause(int position) {
      if (mediaPreviewFragment != null) {
        mediaPreviewFragment.pause();
      }
    }

    @Override
    public @Nullable View getPlaybackControls(int position) {
      if (mediaPreviewFragment != null) {
        return mediaPreviewFragment.getPlaybackControls();
      }
      return null;
    }

    @Override
    public boolean hasFragmentFor(int position) {
      return mediaPreviewFragment != null;
    }
  }

  private static void anchorMarginsToBottomInsets(@NonNull View viewToAnchor) {
    ViewCompat.setOnApplyWindowInsetsListener(viewToAnchor, (view, insets) -> {
      ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) view.getLayoutParams();

      layoutParams.setMargins(insets.getSystemWindowInsetLeft(),
                              layoutParams.topMargin,
                              insets.getSystemWindowInsetRight(),
                              insets.getSystemWindowInsetBottom());

      view.setLayoutParams(layoutParams);

      return insets;
    });
  }

  private static class CursorPagerAdapter extends FragmentStatePagerAdapter implements MediaItemAdapter {

    @SuppressLint("UseSparseArrays")
    private final Map<Integer, MediaPreviewFragment> mediaFragments = new HashMap<>();

    private final Context context;
    private final Cursor  cursor;
    private final boolean leftIsRecent;

    private boolean active;
    private int     autoPlayPosition;

    CursorPagerAdapter(@NonNull FragmentManager fragmentManager,
                       @NonNull Context context,
                       @NonNull Cursor cursor,
                       int autoPlayPosition,
                       boolean leftIsRecent)
    {
      super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
      this.context          = context.getApplicationContext();
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

    @NonNull
    @Override
    public Fragment getItem(int position) {
      boolean autoPlay = autoPlayPosition == position;
      int cursorPosition = getCursorPosition(position);

      autoPlayPosition = -1;

      cursor.moveToPosition(cursorPosition);

      MediaDatabase.MediaRecord mediaRecord = MediaDatabase.MediaRecord.from(context, cursor);
      DatabaseAttachment        attachment  = Objects.requireNonNull(mediaRecord.getAttachment());
      MediaPreviewFragment      fragment    = MediaPreviewFragment.newInstance(attachment, autoPlay);

      mediaFragments.put(position, fragment);

      return fragment;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
      MediaPreviewFragment removed = mediaFragments.remove(position);

      if (removed != null) {
        removed.cleanUp();
      }

      super.destroyItem(container, position, object);
    }

    public @Nullable MediaItem getMediaItemFor(int position) {
      int cursorPosition = getCursorPosition(position);

      if (cursor.isClosed() || cursorPosition < 0) {
        Log.w(TAG, "Invalid cursor state! Closed: " + cursor.isClosed() + " Position: " + cursorPosition);
        return null;
      }

      cursor.moveToPosition(cursorPosition);

      MediaRecord        mediaRecord       = MediaRecord.from(context, cursor);
      DatabaseAttachment attachment        = Objects.requireNonNull(mediaRecord.getAttachment());
      RecipientId        recipientId       = mediaRecord.getRecipientId();
      RecipientId        threadRecipientId = mediaRecord.getThreadRecipientId();

      return new MediaItem(Recipient.live(recipientId).get(),
                           Recipient.live(threadRecipientId).get(),
                           attachment,
                           Objects.requireNonNull(attachment.getUri()),
                           mediaRecord.getContentType(),
                           mediaRecord.getDate(),
                           mediaRecord.isOutgoing());
    }

    @Override
    public void pause(int position) {
      MediaPreviewFragment mediaView = mediaFragments.get(position);
      if (mediaView != null) mediaView.pause();
    }

    @Override
    public @Nullable View getPlaybackControls(int position) {
      MediaPreviewFragment mediaView = mediaFragments.get(position);
      if (mediaView != null) return mediaView.getPlaybackControls();
      return null;
    }

    @Override
    public boolean hasFragmentFor(int position) {
      return mediaFragments.containsKey(position);
    }

    private int getCursorPosition(int position) {
      if (leftIsRecent) return position;
      else              return cursor.getCount() - 1 - position;
    }
  }

  private static class MediaItem {
    private final @Nullable Recipient          recipient;
    private final @Nullable Recipient          threadRecipient;
    private final @Nullable DatabaseAttachment attachment;
    private final @NonNull  Uri                uri;
    private final @NonNull  String             type;
    private final           long               date;
    private final           boolean            outgoing;

    private MediaItem(@Nullable Recipient recipient,
                      @Nullable Recipient threadRecipient,
                      @Nullable DatabaseAttachment attachment,
                      @NonNull Uri uri,
                      @NonNull String type,
                      long date,
                      boolean outgoing)
    {
      this.recipient       = recipient;
      this.threadRecipient = threadRecipient;
      this.attachment      = attachment;
      this.uri             = uri;
      this.type            = type;
      this.date            = date;
      this.outgoing        = outgoing;
    }
  }

  interface MediaItemAdapter {
    @Nullable MediaItem getMediaItemFor(int position);
    void pause(int position);
    @Nullable View getPlaybackControls(int position);
    boolean hasFragmentFor(int position);
  }
}
