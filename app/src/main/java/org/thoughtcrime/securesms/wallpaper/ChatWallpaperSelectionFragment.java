package org.thoughtcrime.securesms.wallpaper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.wallpaper.crop.WallpaperImageSelectionActivity;

public class ChatWallpaperSelectionFragment extends Fragment {

  private static final short CHOOSE_WALLPAPER = 1;

  private ChatWallpaperViewModel viewModel;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.chat_wallpaper_selection_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Toolbar              toolbar              = view.findViewById(R.id.toolbar);
    View                 chooseFromPhotos     = view.findViewById(R.id.chat_wallpaper_choose_from_photos);
    RecyclerView         recyclerView         = view.findViewById(R.id.chat_wallpaper_recycler);

    chooseFromPhotos.setOnClickListener(unused -> {
      startActivityForResult(WallpaperImageSelectionActivity.getIntent(requireContext(), viewModel.getRecipientId()), CHOOSE_WALLPAPER);
    });

    toolbar.setTitle(R.string.preferences__chat_color_and_wallpaper);
    toolbar.setNavigationOnClickListener(nav -> Navigation.findNavController(nav).popBackStack());

    @SuppressWarnings("CodeBlock2Expr")
    ChatWallpaperSelectionAdapter adapter = new ChatWallpaperSelectionAdapter(chatWallpaper -> {
      startActivityForResult(ChatWallpaperPreviewActivity.create(requireActivity(), chatWallpaper, viewModel.getRecipientId(), viewModel.getDimInDarkTheme().getValue()), CHOOSE_WALLPAPER);
    });

    recyclerView.setAdapter(adapter);

    viewModel = new ViewModelProvider(requireActivity()).get(ChatWallpaperViewModel.class);
    viewModel.getWallpapers().observe(getViewLifecycleOwner(), adapter::submitList);
  }

  @Override
  public void onResume() {
    super.onResume();
    viewModel.refreshWallpaper();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == CHOOSE_WALLPAPER && resultCode == Activity.RESULT_OK && data != null) {
      ChatWallpaper chatWallpaper = data.getParcelableExtra(ChatWallpaperPreviewActivity.EXTRA_CHAT_WALLPAPER);
      viewModel.setWallpaper(chatWallpaper);
      viewModel.saveWallpaperSelection();
      Navigation.findNavController(requireView()).popBackStack();
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }
}
