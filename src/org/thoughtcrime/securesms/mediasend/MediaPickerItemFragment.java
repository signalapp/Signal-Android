package org.thoughtcrime.securesms.mediasend;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Allows the user to select a set of media items from a specified folder.
 */
public class MediaPickerItemFragment extends Fragment implements MediaPickerItemAdapter.EventListener {

  private static final String KEY_BUCKET_ID     = "bucket_id";
  private static final String KEY_FOLDER_TITLE  = "folder_title";
  private static final String KEY_MAX_SELECTION = "max_selection";

  private String                 bucketId;
  private String                 folderTitle;
  private int                    maxSelection;
  private MediaSendViewModel     viewModel;
  private MediaPickerItemAdapter adapter;
  private Controller             controller;
  private GridLayoutManager      layoutManager;
  private ActionMode             actionMode;
  private ActionMode.Callback    actionModeCallback;

  public static MediaPickerItemFragment newInstance(@NonNull String bucketId, @NonNull String folderTitle, int maxSelection) {
    Bundle args = new Bundle();
    args.putString(KEY_BUCKET_ID, bucketId);
    args.putString(KEY_FOLDER_TITLE, folderTitle);
    args.putInt(KEY_MAX_SELECTION, maxSelection);

    MediaPickerItemFragment fragment = new MediaPickerItemFragment();
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);

    bucketId           = getArguments().getString(KEY_BUCKET_ID);
    folderTitle        = getArguments().getString(KEY_FOLDER_TITLE);
    maxSelection       = getArguments().getInt(KEY_MAX_SELECTION);
    viewModel          = ViewModelProviders.of(requireActivity(), new MediaSendViewModel.Factory(new MediaRepository())).get(MediaSendViewModel.class);
    actionModeCallback = new ActionModeCallback();
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    if (!(getActivity() instanceof Controller)) {
      throw new IllegalStateException("Parent activity must implement controller class.");
    }

    controller = (Controller) getActivity();
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.mediapicker_item_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    RecyclerView imageList = view.findViewById(R.id.mediapicker_item_list);

    adapter       = new MediaPickerItemAdapter(GlideApp.with(this), this, maxSelection);
    layoutManager = new GridLayoutManager(requireContext(), 4);

    imageList.setLayoutManager(layoutManager);
    imageList.setAdapter(adapter);

    initToolbar(view.findViewById(R.id.mediapicker_toolbar));
    onScreenWidthChanged(getScreenWidth());

    if (!Util.isEmpty(viewModel.getSelectedMedia().getValue())) {
      adapter.setSelected(viewModel.getSelectedMedia().getValue());
      onMediaSelectionChanged(new ArrayList<>(viewModel.getSelectedMedia().getValue()));
    }

    viewModel.getMediaInBucket(requireContext(), bucketId).observe(this, adapter::setMedia);
  }

  @Override
  public void onResume() {
    super.onResume();

    requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
    requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.mediapicker_default, menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.mediapicker_menu_add) {
      adapter.setForcedMultiSelect(true);
      actionMode = ((AppCompatActivity) requireActivity()).startSupportActionMode(actionModeCallback);
      actionMode.setTitle(getResources().getString(R.string.MediaPickerItemFragment_tap_to_select));
      return true;
    }
    return false;
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    onScreenWidthChanged(getScreenWidth());
  }

  @Override
  public void onMediaChosen(@NonNull Media media) {
    controller.onMediaSelected(bucketId, Collections.singleton(media));
    viewModel.onSelectedMediaChanged(Collections.singletonList(media));
  }

  @Override
  public void onMediaSelectionChanged(@NonNull List<Media> selected) {
    adapter.notifyDataSetChanged();

    if (actionMode == null && !selected.isEmpty()) {
      actionMode = ((AppCompatActivity) requireActivity()).startSupportActionMode(actionModeCallback);
      actionMode.setTitle(String.valueOf(selected.size()));
    } else if (actionMode != null && selected.isEmpty()) {
      actionMode.finish();
    } else if (actionMode != null) {
      actionMode.setTitle(String.valueOf(selected.size()));
    }

    viewModel.onSelectedMediaChanged(selected);
  }

  @Override
  public void onMediaSelectionOverflow(int maxSelection) {
    Toast.makeText(requireContext(), getResources().getQuantityString(R.plurals.MediaPickerItemFragment_cant_share_more_than_n_items, maxSelection, maxSelection), Toast.LENGTH_SHORT).show();
  }

  private void initToolbar(Toolbar toolbar) {
    ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
    ((AppCompatActivity) requireActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(folderTitle);

    toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
  }

  private void onScreenWidthChanged(int newWidth) {
    if (layoutManager != null) {
      layoutManager.setSpanCount(newWidth / getResources().getDimensionPixelSize(R.dimen.media_picker_item_width));
    }
  }

  private int getScreenWidth() {
    Point size = new Point();
    requireActivity().getWindowManager().getDefaultDisplay().getSize(size);
    return size.x;
  }

  private class ActionModeCallback implements ActionMode.Callback {

    private int statusBarColor;

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      MenuInflater inflater = mode.getMenuInflater();
      inflater.inflate(R.menu.mediapicker_multiselect, menu);

      if (Build.VERSION.SDK_INT >= 21) {
        Window window = requireActivity().getWindow();
        statusBarColor = window.getStatusBarColor();
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
      if (menuItem.getItemId() == R.id.mediapicker_menu_confirm) {
        List<Media> selected = new ArrayList<>(adapter.getSelected());
        actionMode.finish();
        viewModel.onSelectedMediaChanged(selected);
        controller.onMediaSelected(bucketId, selected);
        return true;
      }
      return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
      actionMode = null;
      adapter.setSelected(Collections.emptySet());
      viewModel.onSelectedMediaChanged(Collections.emptyList());

      if (Build.VERSION.SDK_INT >= 21) {
        requireActivity().getWindow().setStatusBarColor(statusBarColor);
      }
    }
  }


  public interface Controller {
    void onMediaSelected(@NonNull String bucketId, @NonNull Collection<Media> media);
  }
}
