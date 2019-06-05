package org.thoughtcrime.securesms.mediasend;

import androidx.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.Util;

import java.util.ArrayList;
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

    bucketId     = getArguments().getString(KEY_BUCKET_ID);
    folderTitle  = getArguments().getString(KEY_FOLDER_TITLE);
    maxSelection = getArguments().getInt(KEY_MAX_SELECTION);
    viewModel    = ViewModelProviders.of(requireActivity(), new MediaSendViewModel.Factory(requireActivity().getApplication(), new MediaRepository())).get(MediaSendViewModel.class);
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

    initMediaObserver(viewModel);
  }

  @Override
  public void onResume() {
    super.onResume();

    viewModel.onItemPickerStarted();
    requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
    requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    requireActivity().getMenuInflater().inflate(R.menu.mediapicker_default, menu);

    if (viewModel.getCountButtonState().getValue() != null && viewModel.getCountButtonState().getValue().isVisible()) {
      menu.findItem(R.id.mediapicker_menu_add).setVisible(false);
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.mediapicker_menu_add:
        adapter.setForcedMultiSelect(true);
        viewModel.onMultiSelectStarted();
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
    controller.onMediaSelected(media);
  }

  @Override
  public void onMediaSelectionStarted() {
    viewModel.onMultiSelectStarted();
  }

  @Override
  public void onMediaSelectionChanged(@NonNull List<Media> selected) {
    adapter.notifyDataSetChanged();
    viewModel.onSelectedMediaChanged(requireContext(), selected);
  }

  @Override
  public void onMediaSelectionOverflow(int maxSelection) {
    Toast.makeText(requireContext(), getResources().getQuantityString(R.plurals.MediaSendActivity_cant_share_more_than_n_items, maxSelection, maxSelection), Toast.LENGTH_SHORT).show();
  }

  private void initToolbar(Toolbar toolbar) {
    ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
    ((AppCompatActivity) requireActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(folderTitle);

    toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
  }

  private void initMediaObserver(@NonNull MediaSendViewModel viewModel) {
    viewModel.getCountButtonState().observe(this, media -> {
      requireActivity().invalidateOptionsMenu();
    });
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

  public interface Controller {
    void onMediaSelected(@NonNull Media media);
  }
}
