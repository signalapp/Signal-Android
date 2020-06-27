package org.thoughtcrime.securesms.mediaoverview;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.RecyclerView;

import com.codewaves.stickyheadergrid.StickyHeaderGridLayoutManager;

import org.thoughtcrime.securesms.MediaPreviewActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.database.MediaDatabase;
import org.thoughtcrime.securesms.database.loaders.GroupedThreadMediaLoader;
import org.thoughtcrime.securesms.database.loaders.MediaLoader;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;

public final class MediaOverviewPageFragment extends Fragment
  implements MediaGalleryAllAdapter.ItemClickListener,
             LoaderManager.LoaderCallbacks<GroupedThreadMediaLoader.GroupedThreadMedia>
{

  private static final String TAG = Log.tag(MediaOverviewPageFragment.class);

  private static final String THREAD_ID_EXTRA  = "thread_id";
  private static final String MEDIA_TYPE_EXTRA = "media_type";
  private static final String GRID_MODE        = "grid_mode";

  private final ActionModeCallback            actionModeCallback = new ActionModeCallback();
  private       MediaDatabase.Sorting         sorting            = MediaDatabase.Sorting.Newest;
  private       MediaLoader.MediaType         mediaType          = MediaLoader.MediaType.GALLERY;
  private       long                          threadId;
  private       TextView                      noMedia;
  private       RecyclerView                  recyclerView;
  private       StickyHeaderGridLayoutManager gridManager;
  private       ActionMode                    actionMode;
  private       boolean                       detail;
  private       MediaGalleryAllAdapter        adapter;
  private       GridMode                      gridMode;

  public static @NonNull Fragment newInstance(long threadId,
                                              @NonNull MediaLoader.MediaType mediaType,
                                              @NonNull GridMode gridMode)
  {
    MediaOverviewPageFragment mediaOverviewAllFragment = new MediaOverviewPageFragment();
    Bundle args = new Bundle();
    args.putLong(THREAD_ID_EXTRA, threadId);
    args.putInt(MEDIA_TYPE_EXTRA, mediaType.ordinal());
    args.putInt(GRID_MODE, gridMode.ordinal());
    mediaOverviewAllFragment.setArguments(args);

    return mediaOverviewAllFragment;
  }

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    Bundle arguments = requireArguments();

    threadId  = arguments.getLong(THREAD_ID_EXTRA, Long.MIN_VALUE);
    mediaType = MediaLoader.MediaType.values()[arguments.getInt(MEDIA_TYPE_EXTRA)];
    gridMode  = GridMode.values()[arguments.getInt(GRID_MODE)];

    if (threadId == Long.MIN_VALUE) throw new AssertionError();

    LoaderManager.getInstance(this).initLoader(0, null, this);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    Context context = requireContext();
    View    view    = inflater.inflate(R.layout.media_overview_page_fragment, container, false);

    this.recyclerView = view.findViewById(R.id.media_grid);
    this.noMedia      = view.findViewById(R.id.no_images);
    this.gridManager  = new StickyHeaderGridLayoutManager(getResources().getInteger(R.integer.media_overview_cols));

    this.adapter = new MediaGalleryAllAdapter(context,
                                              GlideApp.with(this),
                                              new GroupedThreadMediaLoader.EmptyGroupedThreadMedia(),
                                              this,
                                              sorting.isRelatedToFileSize(),
                                              threadId == MediaDatabase.ALL_THREADS);
    this.recyclerView.setAdapter(adapter);
    this.recyclerView.setLayoutManager(gridManager);
    this.recyclerView.setHasFixedSize(true);

    MediaOverviewViewModel viewModel = MediaOverviewViewModel.getMediaOverviewViewModel(requireActivity());

    viewModel.getSortOrder()
      .observe(this, sorting -> {
        if (sorting != null) {
          this.sorting = sorting;
          adapter.setShowFileSizes(sorting.isRelatedToFileSize());
          LoaderManager.getInstance(this).restartLoader(0, null, this);
          refreshActionModeTitle();
        }
      });

    if (gridMode == GridMode.FOLLOW_MODEL) {
      viewModel.getDetailLayout()
               .observe(this, this::setDetailView);
    } else {
      setDetailView(gridMode == GridMode.FIXED_DETAIL);
    }

    return view;
  }

  private void setDetailView(boolean detail) {
    this.detail = detail;
    adapter.setDetailView(detail);
    refreshLayoutManager();
    refreshActionModeTitle();
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    if (gridManager != null) {
      refreshLayoutManager();
    }
  }

  private void refreshLayoutManager() {
    this.gridManager = new StickyHeaderGridLayoutManager(detail ? 1 : getResources().getInteger(R.integer.media_overview_cols));
    this.recyclerView.setLayoutManager(gridManager);
  }

  @Override
  public @NonNull Loader<GroupedThreadMediaLoader.GroupedThreadMedia> onCreateLoader(int i, Bundle bundle) {
    return new GroupedThreadMediaLoader(requireContext(), threadId, mediaType, sorting);
  }

  @Override
  public void onLoadFinished(@NonNull Loader<GroupedThreadMediaLoader.GroupedThreadMedia> loader, GroupedThreadMediaLoader.GroupedThreadMedia groupedThreadMedia) {
    ((MediaGalleryAllAdapter) recyclerView.getAdapter()).setMedia(groupedThreadMedia);
    ((MediaGalleryAllAdapter) recyclerView.getAdapter()).notifyAllSectionsDataSetChanged();

    noMedia.setVisibility(recyclerView.getAdapter().getItemCount() > 0 ? View.GONE : View.VISIBLE);
    getActivity().invalidateOptionsMenu();
  }

  @Override
  public void onLoaderReset(@NonNull Loader<GroupedThreadMediaLoader.GroupedThreadMedia> cursorLoader) {
    ((MediaGalleryAllAdapter) recyclerView.getAdapter()).setMedia(new GroupedThreadMediaLoader.EmptyGroupedThreadMedia());
  }

  @Override
  public void onMediaClicked(@NonNull MediaDatabase.MediaRecord mediaRecord) {
    if (actionMode != null) {
      handleMediaMultiSelectClick(mediaRecord);
    } else {
      handleMediaPreviewClick(mediaRecord);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    int childCount = recyclerView.getChildCount();
    for (int i = 0; i < childCount; i++) {
      adapter.pause(recyclerView.getChildViewHolder(recyclerView.getChildAt(i)));
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    int childCount = recyclerView.getChildCount();
    for (int i = 0; i < childCount; i++) {
      adapter.detach(recyclerView.getChildViewHolder(recyclerView.getChildAt(i)));
    }
  }

  private void handleMediaMultiSelectClick(@NonNull MediaDatabase.MediaRecord mediaRecord) {
    MediaGalleryAllAdapter adapter = getListAdapter();

    adapter.toggleSelection(mediaRecord);
    if (adapter.getSelectedMediaCount() == 0) {
      actionMode.finish();
    } else {
      refreshActionModeTitle();
    }
  }

  private void handleMediaPreviewClick(@NonNull MediaDatabase.MediaRecord mediaRecord) {
    if (mediaRecord.getAttachment().getDataUri() == null) {
      return;
    }

    Context context = getContext();
    if (context == null) {
      return;
    }

    DatabaseAttachment attachment = mediaRecord.getAttachment();

    if (MediaUtil.isVideo(attachment) || MediaUtil.isImage(attachment)) {
      Intent intent = new Intent(context, MediaPreviewActivity.class);
      intent.putExtra(MediaPreviewActivity.DATE_EXTRA, mediaRecord.getDate());
      intent.putExtra(MediaPreviewActivity.SIZE_EXTRA, mediaRecord.getAttachment().getSize());
      intent.putExtra(MediaPreviewActivity.THREAD_ID_EXTRA, threadId);
      intent.putExtra(MediaPreviewActivity.LEFT_IS_RECENT_EXTRA, true);
      intent.putExtra(MediaPreviewActivity.HIDE_ALL_MEDIA_EXTRA, true);
      intent.putExtra(MediaPreviewActivity.SHOW_THREAD_EXTRA, threadId == MediaDatabase.ALL_THREADS);
      intent.putExtra(MediaPreviewActivity.SORTING_EXTRA, sorting.ordinal());

      intent.setDataAndType(mediaRecord.getAttachment().getDataUri(), mediaRecord.getContentType());
      context.startActivity(intent);
    } else {
      if (!MediaUtil.isAudio(attachment)) {
        showFileExternally(context, mediaRecord);
      }
    }
  }

  private static void showFileExternally(@NonNull Context context, @NonNull MediaDatabase.MediaRecord mediaRecord) {
      Uri uri = mediaRecord.getAttachment().getDataUri();

      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      intent.setDataAndType(PartAuthority.getAttachmentPublicUri(uri), mediaRecord.getContentType());
      try {
        context.startActivity(intent);
      } catch (ActivityNotFoundException e) {
        Log.w(TAG, "No activity existed to view the media.");
        Toast.makeText(context, R.string.ConversationItem_unable_to_open_media, Toast.LENGTH_LONG).show();
      }
    }

  @Override
  public void onMediaLongClicked(MediaDatabase.MediaRecord mediaRecord) {
    ((MediaGalleryAllAdapter) recyclerView.getAdapter()).toggleSelection(mediaRecord);
    recyclerView.getAdapter().notifyDataSetChanged();

    if (actionMode == null) {
      enterMultiSelect();
    }
  }

  private void handleSelectAllMedia() {
    getListAdapter().selectAllMedia();
    refreshActionModeTitle();
  }

  private void refreshActionModeTitle() {
    if (actionMode != null) {
      actionMode.setTitle(getActionModeTitle());
    }
  }

  private String getActionModeTitle() {
    MediaGalleryAllAdapter adapter           = getListAdapter();
    int                    mediaCount        = adapter.getSelectedMediaCount();
    boolean                showTotalFileSize = detail                                     ||
                                               mediaType != MediaLoader.MediaType.GALLERY ||
                                               sorting   == MediaDatabase.Sorting.Largest;

    if (showTotalFileSize) {
      long                   totalFileSize = adapter.getSelectedMediaTotalFileSize();
      return getResources().getQuantityString(R.plurals.MediaOverviewActivity_d_items_s,
                                            mediaCount,
                                            mediaCount,
                                            Util.getPrettyFileSize(totalFileSize));
    } else {
      return getResources().getQuantityString(R.plurals.MediaOverviewActivity_d_items,
                                            mediaCount,
                                            mediaCount);
    }
  }

  private MediaGalleryAllAdapter getListAdapter() {
    return (MediaGalleryAllAdapter) recyclerView.getAdapter();
  }

  private void enterMultiSelect() {
    FragmentActivity activity = requireActivity();
    actionMode = ((AppCompatActivity) activity).startSupportActionMode(actionModeCallback);
    ((MediaOverviewActivity) activity).onEnterMultiSelect();
  }

  private class ActionModeCallback implements ActionMode.Callback {

    private int originalStatusBarColor;

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      mode.getMenuInflater().inflate(R.menu.media_overview_context, menu);
      mode.setTitle(getActionModeTitle());

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        Window window = requireActivity().getWindow();
        originalStatusBarColor = window.getStatusBarColor();
        window.setStatusBarColor(getResources().getColor(R.color.action_mode_status_bar));
      }
      return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
      return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem menuItem) {
      switch (menuItem.getItemId()) {
        case R.id.save:
          MediaActions.handleSaveMedia(MediaOverviewPageFragment.this,
                                       getListAdapter().getSelectedMedia(),
                                       () -> actionMode.finish());
          return true;
        case R.id.delete:
          MediaActions.handleDeleteMedia(requireContext(), getListAdapter().getSelectedMedia());
          actionMode.finish();
          return true;
        case R.id.select_all:
          handleSelectAllMedia();
          return true;
      }
      return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
      actionMode = null;
      getListAdapter().clearSelection();

      FragmentActivity activity = requireActivity();

      ((MediaOverviewActivity) activity).onExitMultiSelect();

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        activity.getWindow().setStatusBarColor(originalStatusBarColor);
      }
    }
  }

  public enum GridMode {
    FIXED_DETAIL,
    FOLLOW_MODEL
  }
}
