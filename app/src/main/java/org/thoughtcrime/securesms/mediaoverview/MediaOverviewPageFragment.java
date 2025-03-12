package org.thoughtcrime.securesms.mediaoverview;

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.codewaves.stickyheadergrid.StickyHeaderGridLayoutManager;

import org.signal.core.util.ByteSize;
import org.signal.core.util.DimensionUnit;
import org.signal.core.util.concurrent.LifecycleDisposable;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.components.DeleteSyncEducationDialog;
import org.thoughtcrime.securesms.components.menu.ActionItem;
import org.thoughtcrime.securesms.components.menu.SignalBottomActionBar;
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController;
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState;
import org.thoughtcrime.securesms.database.MediaTable;
import org.thoughtcrime.securesms.database.loaders.GroupedThreadMediaLoader;
import org.thoughtcrime.securesms.database.loaders.MediaLoader;
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory;
import org.thoughtcrime.securesms.mediapreview.MediaPreviewV2Activity;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.util.BottomOffsetDecoration;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Arrays;
import java.util.Objects;

public final class MediaOverviewPageFragment extends Fragment
  implements MediaGalleryAllAdapter.ItemClickListener,
             MediaGalleryAllAdapter.AudioItemListener,
             LoaderManager.LoaderCallbacks<GroupedThreadMediaLoader.GroupedThreadMedia>
{

  private static final String TAG = Log.tag(MediaOverviewPageFragment.class);

  private static final String THREAD_ID_EXTRA  = "thread_id";
  private static final String MEDIA_TYPE_EXTRA = "media_type";
  private static final String GRID_MODE        = "grid_mode";

  private final ActionModeCallback            actionModeCallback = new ActionModeCallback();
  private       MediaTable.Sorting            sorting            = MediaTable.Sorting.Newest;
  private       MediaLoader.MediaType         mediaType          = MediaLoader.MediaType.GALLERY;
  private       long                          threadId;
  private       TextView                      noMedia;
  private       RecyclerView                  recyclerView;
  private       StickyHeaderGridLayoutManager gridManager;
  private       ActionMode                    actionMode;
  private       boolean                       detail;
  private       MediaGalleryAllAdapter        adapter;
  private       GridMode                      gridMode;
  private       VoiceNoteMediaController      voiceNoteMediaController;
  private       SignalBottomActionBar         bottomActionBar;
  private       LifecycleDisposable           lifecycleDisposable;

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
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    voiceNoteMediaController = new VoiceNoteMediaController(requireActivity(), false);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    lifecycleDisposable = new LifecycleDisposable();
    lifecycleDisposable.bindTo(getViewLifecycleOwner());

    Context context = requireContext();
    View    view    = inflater.inflate(R.layout.media_overview_page_fragment, container, false);
    int     spans   = getResources().getInteger(R.integer.media_overview_cols);

    this.recyclerView    = view.findViewById(R.id.media_grid);
    this.noMedia         = view.findViewById(R.id.no_images);
    this.bottomActionBar = view.findViewById(R.id.media_overview_bottom_action_bar);
    this.gridManager     = new StickyHeaderGridLayoutManager(spans);

    this.adapter = new MediaGalleryAllAdapter(context,
                                              Glide.with(this),
                                              new GroupedThreadMediaLoader.EmptyGroupedThreadMedia(),
                                              this,
                                              this,
                                              sorting.isRelatedToFileSize(),
                                              threadId == MediaTable.ALL_THREADS);
    this.recyclerView.setAdapter(adapter);
    this.recyclerView.setLayoutManager(gridManager);
    this.recyclerView.setHasFixedSize(true);
    this.recyclerView.addItemDecoration(new MediaGridDividerDecoration(spans, ViewUtil.dpToPx(4), adapter));
    this.recyclerView.addItemDecoration(new BottomOffsetDecoration(ViewUtil.dpToPx(160)));

    MediaOverviewViewModel viewModel = MediaOverviewViewModel.getMediaOverviewViewModel(requireActivity());

    viewModel.getSortOrder()
      .observe(getViewLifecycleOwner(), sorting -> {
        if (sorting != null) {
          this.sorting = sorting;
          adapter.setShowFileSizes(sorting.isRelatedToFileSize());
          LoaderManager.getInstance(this).restartLoader(0, null, this);
          updateMultiSelect();
        }
      });

    if (gridMode == GridMode.FOLLOW_MODEL) {
      viewModel.getDetailLayout()
               .observe(getViewLifecycleOwner(), this::setDetailView);
    } else {
      setDetailView(gridMode == GridMode.FIXED_DETAIL);
    }

    return view;
  }

  private void setDetailView(boolean detail) {
    this.detail = detail;
    adapter.setDetailView(detail);
    refreshLayoutManager();
    updateMultiSelect();
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
  public void onMediaClicked(@NonNull View view, @NonNull MediaTable.MediaRecord mediaRecord) {
    if (actionMode != null) {
      handleMediaMultiSelectClick(mediaRecord);
    } else {
      handleMediaPreviewClick(view, mediaRecord);
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (recyclerView != null) {
      int childCount = recyclerView.getChildCount();
      for (int i = 0; i < childCount; i++) {
        adapter.detach(recyclerView.getChildViewHolder(recyclerView.getChildAt(i)));
      }
    }
  }

  private void handleMediaMultiSelectClick(@NonNull MediaTable.MediaRecord mediaRecord) {
    MediaGalleryAllAdapter adapter = getListAdapter();

    adapter.toggleSelection(mediaRecord);
    if (adapter.getSelectedMediaCount() == 0) {
      actionMode.finish();
    } else {
      updateMultiSelect();
    }
  }

  private void handleMediaPreviewClick(@NonNull View view, @NonNull MediaTable.MediaRecord mediaRecord) {
    if (mediaRecord.getAttachment().getUri() == null) {
      return;
    }

    Context context = getContext();
    if (context == null) {
      return;
    }

    DatabaseAttachment attachment = mediaRecord.getAttachment();

    if (MediaUtil.isVideo(attachment) || MediaUtil.isImage(attachment)) {
      MediaIntentFactory.MediaPreviewArgs args = new MediaIntentFactory.MediaPreviewArgs(
          threadId,
          mediaRecord.getDate(),
          Objects.requireNonNull(mediaRecord.getAttachment().getUri()),
          mediaRecord.getContentType(),
          mediaRecord.getAttachment().size,
          mediaRecord.getAttachment().caption,
          true,
          true,
          threadId == MediaTable.ALL_THREADS,
          true,
          sorting,
          attachment.videoGif,
          new MediaIntentFactory.SharedElementArgs(
              attachment.width,
              attachment.height,
              DimensionUnit.DP.toDp(12),
              DimensionUnit.DP.toDp(12),
              DimensionUnit.DP.toDp(12),
              DimensionUnit.DP.toDp(12)
          ),
          false);
      view.setTransitionName(MediaPreviewV2Activity.SHARED_ELEMENT_TRANSITION_NAME);
      ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(requireActivity(), view, MediaPreviewV2Activity.SHARED_ELEMENT_TRANSITION_NAME);
      context.startActivity(MediaIntentFactory.create(context, args), options.toBundle());
    } else {
      if (!MediaUtil.isAudio(attachment)) {
        showFileExternally(context, mediaRecord);
      }
    }
  }

  private static void showFileExternally(@NonNull Context context, @NonNull MediaTable.MediaRecord mediaRecord) {
      Uri uri = mediaRecord.getAttachment().getUri();

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
  public void onMediaLongClicked(MediaTable.MediaRecord mediaRecord) {
    if (actionMode == null) {
      enterMultiSelect();
    }

    handleMediaMultiSelectClick(mediaRecord);
  }

  private void handleDeleteSelectedMedia() {
    if (DeleteSyncEducationDialog.shouldShow()) {
      lifecycleDisposable.add(
          DeleteSyncEducationDialog.show(getChildFragmentManager())
                                   .subscribe(this::handleDeleteSelectedMedia)
      );
      return;
    }

    MediaActions.handleDeleteMedia(requireContext(), getListAdapter().getSelectedMedia());
    exitMultiSelect();
  }

  private void handleSelectAllMedia() {
    getListAdapter().selectAllMedia();
    updateMultiSelect();
  }

  private String getActionModeTitle() {
    MediaGalleryAllAdapter adapter       = getListAdapter();
    int                    mediaCount    = adapter.getSelectedMediaCount();
    long                   totalFileSize = adapter.getSelectedMediaTotalFileSize();

    return getResources().getQuantityString(R.plurals.MediaOverviewActivity_d_selected_s,
                                            mediaCount,
                                            mediaCount,
                                            new ByteSize(totalFileSize).toUnitString());
  }

  private MediaGalleryAllAdapter getListAdapter() {
    return (MediaGalleryAllAdapter) recyclerView.getAdapter();
  }

  private void enterMultiSelect() {
    FragmentActivity activity = requireActivity();
    actionMode = ((AppCompatActivity) activity).startSupportActionMode(actionModeCallback);
    ((MediaOverviewActivity) activity).onEnterMultiSelect();
    ViewUtil.animateIn(bottomActionBar, bottomActionBar.getEnterAnimation());
    updateMultiSelect();
  }

  private void exitMultiSelect() {
    actionMode.finish();
    actionMode = null;
    ViewUtil.animateOut(bottomActionBar, bottomActionBar.getExitAnimation());
  }

  private void updateMultiSelect() {
    if (actionMode != null) {
      actionMode.setTitle(getActionModeTitle());

      int selectionCount = getListAdapter().getSectionCount();

      bottomActionBar.setItems(Arrays.asList(
          new ActionItem(R.drawable.symbol_save_android_24, getResources().getQuantityString(R.plurals.MediaOverviewActivity_save_plural, selectionCount), () -> {
            MediaActions.handleSaveMedia(MediaOverviewPageFragment.this,
                                         getListAdapter().getSelectedMedia(),
                                         this::exitMultiSelect);
          }),
          new ActionItem(R.drawable.symbol_check_circle_24, getString(R.string.MediaOverviewActivity_select_all), this::handleSelectAllMedia),
          new ActionItem(R.drawable.symbol_trash_24, getResources().getQuantityString(R.plurals.MediaOverviewActivity_delete_plural, selectionCount), this::handleDeleteSelectedMedia)
      ));
    }
  }


  @Override
  public void onPlay(@NonNull Uri audioUri, double progress, long messageId) {
    voiceNoteMediaController.startSinglePlayback(audioUri, messageId, progress);
  }

  @Override
  public void onPause(@NonNull Uri audioUri) {
    voiceNoteMediaController.pausePlayback(audioUri);
  }

  @Override
  public void onSeekTo(@NonNull Uri audioUri, double progress) {
    voiceNoteMediaController.seekToPosition(audioUri, progress);
  }

  @Override
  public void onStopAndReset(@NonNull Uri audioUri) {
    voiceNoteMediaController.stopPlaybackAndReset(audioUri);
  }

  @Override
  public void registerPlaybackStateObserver(@NonNull Observer<VoiceNotePlaybackState> observer) {
    voiceNoteMediaController.getVoiceNotePlaybackState().observe(getViewLifecycleOwner(), observer);
  }

  @Override
  public void unregisterPlaybackStateObserver(@NonNull Observer<VoiceNotePlaybackState> observer) {
    voiceNoteMediaController.getVoiceNotePlaybackState().removeObserver(observer);
  }

  private class ActionModeCallback implements ActionMode.Callback {

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      mode.setTitle(getActionModeTitle());
      return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
      return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem menuItem) {
      return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
      getListAdapter().clearSelection();

      FragmentActivity activity = requireActivity();
      ((MediaOverviewActivity) activity).onExitMultiSelect();

      exitMultiSelect();
    }
  }

  public enum GridMode {
    FIXED_DETAIL,
    FOLLOW_MODEL
  }
}
