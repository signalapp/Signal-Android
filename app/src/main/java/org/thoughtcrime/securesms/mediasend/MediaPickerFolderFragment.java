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

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.libsignal.util.guava.Optional;

/**
 * Allows the user to select a media folder to explore.
 */
public class MediaPickerFolderFragment extends Fragment implements MediaPickerFolderAdapter.EventListener {

  private static final String KEY_TOOLBAR_TITLE = "toolbar_title";

  private String             toolbarTitle;
  private MediaSendViewModel viewModel;
  private Controller         controller;
  private GridLayoutManager  layoutManager;

  public static @NonNull MediaPickerFolderFragment newInstance(@NonNull Context context, @Nullable Recipient recipient) {
    String toolbarTitle;

    if (recipient != null) {
      String name = recipient.getDisplayName(context);
      toolbarTitle = context.getString(R.string.MediaPickerActivity_send_to, name);
    } else {
      toolbarTitle = "";
    }

    Bundle args = new Bundle();
    args.putString(KEY_TOOLBAR_TITLE, toolbarTitle);

    MediaPickerFolderFragment fragment = new MediaPickerFolderFragment();
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);

    toolbarTitle = getArguments().getString(KEY_TOOLBAR_TITLE);
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
    return inflater.inflate(R.layout.mediapicker_folder_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    RecyclerView             list    = view.findViewById(R.id.mediapicker_folder_list);
    MediaPickerFolderAdapter adapter = new MediaPickerFolderAdapter(GlideApp.with(this), this);

    layoutManager = new GridLayoutManager(requireContext(), 2);
    onScreenWidthChanged(getScreenWidth());

    list.setLayoutManager(layoutManager);
    list.setAdapter(adapter);

    viewModel.getFolders(requireContext()).observe(this, adapter::setFolders);

    initToolbar(view.findViewById(R.id.mediapicker_toolbar));
  }

  @Override
  public void onResume() {
    super.onResume();
    viewModel.onFolderPickerStarted();
  }

  @Override
  public void onPrepareOptionsMenu(@NonNull Menu menu) {
    requireActivity().getMenuInflater().inflate(R.menu.mediapicker_default, menu);
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    switch (item.getItemId()) {
      case R.id.mediapicker_menu_camera:
        controller.onCameraSelected();
        return true;
    }
    return false;
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    onScreenWidthChanged(getScreenWidth());
  }

  private void initToolbar(Toolbar toolbar) {
    ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
    ((AppCompatActivity) requireActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(toolbarTitle);

    toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
  }

  private void onScreenWidthChanged(int newWidth) {
    if (layoutManager != null) {
      layoutManager.setSpanCount(newWidth / getResources().getDimensionPixelSize(R.dimen.media_picker_folder_width));
    }
  }

  private int getScreenWidth() {
    Point size = new Point();
    requireActivity().getWindowManager().getDefaultDisplay().getSize(size);
    return size.x;
  }

  @Override
  public void onFolderClicked(@NonNull MediaFolder folder) {
    controller.onFolderSelected(folder);
  }

  public interface Controller {
    void onFolderSelected(@NonNull MediaFolder folder);
    void onCameraSelected();
  }
}
